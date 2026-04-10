package com.github.evermore;

import com.github.evermore.proto.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import org.apache.kafka.common.TopicPartition;

public class EngineNode {
    private static volatile double mu = 1.95;
    private static final double alpha = 0.2;
    private static final String topic = "auction-results-v17";

    static class ValuationServiceImpl extends ValuationServiceGrpc.ValuationServiceImplBase {
        private final ValuationEngine engine;

        public ValuationServiceImpl(ValuationEngine engine) {
            this.engine = engine;
        }

        @Override
        public void getMaximumBid(BidRequest request, StreamObserver<BidResponse> responseObserver) {
            System.out.println("[gRPC]: Received bid request for: " + request.getPolitician().getName());

            PoliticianMsg msg = request.getPolitician();
            Politician p = new Politician(msg.getId(), msg.getName(), msg.getIsIndian(), msg.getIsFemale(),
                    msg.getVolatilityIndex(), msg.getSpectrum(), msg.getTotal(), msg.getBasePrice());

            int mxBid = this.engine.getMaximumBid(p, request.getParam1(), request.getParam2(), request.getParam3(), request.getParam4());
            BidResponse response = BidResponse.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setMaxBid(mxBid)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    public static void main(String[] args) throws Exception {
        List<Politician> list = CSVReader.parseCSV("list.csv");
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter Total Budget (A): ");
        int budgetA = scanner.nextInt();
        System.out.print("Enter Min Team Size (B): ");
        int sizeB = scanner.nextInt();
        System.out.print("Enter Min Females (C): ");
        int femalesC = scanner.nextInt();
        System.out.print("Enter Max Volatility (D): ");
        int volD = scanner.nextInt();
        System.out.print("Enter Min Indians (E): ");
        int indiansE = scanner.nextInt();

        ValuationEngine sharedEngine = new ValuationEngine(budgetA, sizeB, femalesC, volD, indiansE, list);
        sharedEngine.updateExpectedPrices(mu);

        System.out.println("\n[Engine] initialized. Starting network service...");
        Server server = ServerBuilder.forPort(9090)
                .addService(new ValuationServiceImpl(sharedEngine))
                .build()
                .start();

        System.out.println("[gRPC] Server started, listening on 9090");
        Thread kafkaThread = new Thread(() -> {
            try {
                Properties constProps = new Properties();
                constProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
                String randomGroupId = "debug-group-" + java.util.UUID.randomUUID().toString();
                constProps.put(ConsumerConfig.GROUP_ID_CONFIG, randomGroupId);
                constProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

                constProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                constProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

                KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(constProps);
                TopicPartition partition0 = new TopicPartition(topic, 0);
                consumer.assign(Collections.singletonList(partition0));
                consumer.seekToBeginning(Collections.singletonList(partition0));

                int cnt = 0;
                while (true) {
                    cnt += 1;
                    if (cnt % 50 == 0) {
                        System.out.println("[KAFKA THREAD] Polling...");
                    }

                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));

                    if (!records.isEmpty()) {
                        System.out.println("\n[KAFKA THREAD] Found " + records.count() + " records");
                    }

                    for (ConsumerRecord<String, byte[]> record : records) {
                        try {
                            System.out.println("[KAFKA THREAD] Processing record at offset " + record.offset());

                            AuctionResultEvent event = AuctionResultEvent.parseFrom(record.value());
                            PoliticianMsg msg = event.getPolitician();

                            System.out.println("[KAFKA THREAD] SUCCESS: Parsed result for " + msg.getName());

                            Politician p = new Politician(msg.getId(), msg.getName(), msg.getIsIndian(), msg.getIsFemale(),
                                    msg.getVolatilityIndex(), msg.getSpectrum(), msg.getTotal(), msg.getBasePrice());

                            sharedEngine.recordAuctionResult(p, event.getWasBought(), event.getSoldPrice());

                            if (event.getSoldPrice() > 0) {
                                System.out.printf("OLD EMA: %.2f\n", mu);
                                double M = (double) event.getSoldPrice() / p.basePrice;
                                System.out.printf("M: %.2f\n", M);
                                mu = (alpha * M) + ((1 - alpha) * mu);
                                System.out.printf("--> Engine EMA updated to: %.2fx\n\n", mu);
                                sharedEngine.updateExpectedPrices(mu);
                            } else {
                                System.out.println("--> Politician went unsold. EMA remains: " + String.format("%.2fx", mu) + "\n");
                            }
                        } catch (Exception e) {
                            System.err.println("[KAFKA THREAD] ERROR processing specific record: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception fatal) {
                System.err.println("[KAFKA THREAD] FATAL ERR: " + fatal.getMessage());
                fatal.printStackTrace();
            }
        });

        kafkaThread.start();
        server.awaitTermination();
    }
}