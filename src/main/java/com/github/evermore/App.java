package com.github.evermore;

import com.github.evermore.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class App extends Application {

    private ManagedChannel grpcChannel;
    private ValuationServiceGrpc.ValuationServiceBlockingStub engineStub;
    private KafkaProducer<String, byte[]> kafkaProducer;

    private Label bidLabel;
    private List<Politician> politicians;
    private Politician currentPolitician;
    private static final String topic = "auction-results-v2";

    @Override
    public void init() throws Exception {
        grpcChannel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();
        engineStub = ValuationServiceGrpc.newBlockingStub(grpcChannel);

        Properties prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        kafkaProducer = new KafkaProducer<>(prodProps);

        System.out.println("App Client connected to network service.");

        politicians = CSVReader.parseCSV("list.csv");
        if (politicians != null && !politicians.isEmpty()) {
            currentPolitician = politicians.get(0);
            System.out.println("Loaded CSV. First politician: " + currentPolitician.name);
        } else {
            System.err.println("Warning: list.csv is empty or could not be loaded.");
        }
    }

    @Override
    public void start(Stage stage) {
        String startText = currentPolitician != null
                ? "Ready to bid on: " + currentPolitician.name
                : "Waiting for next politician...";
        bidLabel = new Label(startText);

        Button askBidBtn = new Button("Ask Engine for Max Bid");
        askBidBtn.setOnAction(e -> requestMaxBid());

        Button submitResultBtn = new Button("I Won the Auction!");
        submitResultBtn.setOnAction(e -> broadcastVictory());

        VBox layout = new VBox(10, bidLabel, askBidBtn, submitResultBtn);
        layout.setPadding(new Insets(20));

        Scene scene = new Scene(layout, 400, 300);
        stage.setTitle("RealPolitik Tracker");
        stage.setScene(scene);
        stage.show();
    }

    private void requestMaxBid() {
        if (currentPolitician == null) {
            Platform.runLater(() -> bidLabel.setText("No politician loaded from CSV!"));
            return;
        }

        PoliticianMsg msg = toProto(currentPolitician);
        BidRequest request = BidRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setPolitician(msg)
                .setParam1(true).setParam2(2)
                .setParam3(false).setParam4(1.05)
                .build();

        try {
            BidResponse response = engineStub.getMaximumBid(request);
            Platform.runLater(() -> bidLabel.setText("Engine says: Max Bid for " + currentPolitician.name + " is " + response.getMaxBid()));
        } catch (Exception ex) {
            ex.printStackTrace();
            Platform.runLater(() -> bidLabel.setText("Error contacting Engine! Is EngineNode running?"));
        }
    }

    private void broadcastVictory() {
        if (currentPolitician == null) return;

        PoliticianMsg msg = toProto(currentPolitician);
        int hardcodedPurchasePrice = 100;

        AuctionResultEvent event = AuctionResultEvent.newBuilder()
                .setPolitician(msg)
                .setWasBought(true)
                .setSoldPrice(hardcodedPurchasePrice)
                .build();

        kafkaProducer.send(new ProducerRecord<>(topic, String.valueOf(currentPolitician.ID), event.toByteArray()),
                (metadata, exception) -> {
                    if (exception != null) {
                        System.err.println("[KAFKA ERROR]: Failed to send message!");
                        exception.printStackTrace();
                    } else {
                        System.out.println("[KAFKA SUCCESS]: Message delivered to partition " + metadata.partition());
                    }
                });

        kafkaProducer.flush();
        Platform.runLater(() -> bidLabel.setText("Victory broadcasted! Bought " + currentPolitician.name + " for " + hardcodedPurchasePrice));
    }

    private PoliticianMsg toProto(Politician p) {
        return PoliticianMsg.newBuilder()
                .setId(p.ID)
                .setName(p.name)
                .setIsIndian(p.isIndian)
                .setIsFemale(p.isFemale)
                .setVolatilityIndex(p.volatilityIndex)
                .setSpectrum(p.spectrum)
                .setTotal(p.total)
                .setBasePrice(p.basePrice)
                .build();
    }

    @Override
    public void stop() throws Exception {
        if (kafkaProducer != null) kafkaProducer.close();
        if (grpcChannel != null) grpcChannel.shutdown();
        System.out.println("Client shutdown complete.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}