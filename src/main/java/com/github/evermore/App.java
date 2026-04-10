package com.github.evermore;

import com.github.evermore.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;

public class App extends Application {

    private ManagedChannel grpcChannel;
    private ValuationServiceGrpc.ValuationServiceBlockingStub engineStub;
    private KafkaProducer<String, byte[]> kafkaProducer;

    private List<Politician> polPool;

    private static int budgetA, sizeB, femalesC, volD, indiansE;
    private static final String topic = "auction-results-v11";

    @Override
    public void init() throws Exception {
        grpcChannel = ManagedChannelBuilder.forAddress("localhost", 9090).usePlaintext().build();
        engineStub = ValuationServiceGrpc.newBlockingStub(grpcChannel);

        Properties prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        kafkaProducer = new KafkaProducer<>(prodProps);

        polPool = CSVReader.parseCSV("list.csv");
    }

    @Override
    public void start(Stage primaryStage) {
        showMainDashboard(primaryStage);
    }

    private void showMainDashboard(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15)); // Slightly increased padding

        VBox topBox = new VBox(15);
        HBox statsBox = new HBox(20); // Spaced out the stats a bit more

        // Upgraded styling for the stats box
        statsBox.setStyle("-fx-background-color: #ffffff; -fx-padding: 15; " +
                "-fx-border-color: #d1d5db; -fx-border-radius: 8; " +
                "-fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 2);");

        // Made the stats labels bolder for better readability
        String labelStyle = "-fx-font-weight: bold; -fx-text-fill: #374151;";
        Label bLabel = new Label("Budget: " + budgetA); bLabel.setStyle(labelStyle);
        Label sLabel = new Label("Target Size: " + sizeB); sLabel.setStyle(labelStyle);
        Label fLabel = new Label("Min Females: " + femalesC); fLabel.setStyle(labelStyle);
        Label vLabel = new Label("Max Volatility: " + volD); vLabel.setStyle(labelStyle);
        Label iLabel = new Label("Min Indians: " + indiansE); iLabel.setStyle(labelStyle);

        statsBox.getChildren().addAll(bLabel, sLabel, fLabel, vLabel, iLabel);

        TextField searchField = new TextField();
        searchField.setPromptText("Search politician by name...");
        searchField.setStyle("-fx-padding: 8; -fx-font-size: 14px; -fx-border-radius: 5; -fx-background-radius: 5;");

        topBox.getChildren().addAll(statsBox, searchField);
        root.setTop(topBox);

        TableView<Politician> table = new TableView<>();

        // --- EXISTING COLUMNS ---
        TableColumn<Politician, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().ID)));
        idCol.setPrefWidth(50);

        TableColumn<Politician, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name));
        nameCol.setPrefWidth(200);

        TableColumn<Politician, String> basePriceCol = new TableColumn<>("Base Price");
        basePriceCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().basePrice)));

        TableColumn<Politician, String> volCol = new TableColumn<>("Volatility");
        volCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().volatilityIndex)));

        // --- NEW MISSING COLUMNS ---
        TableColumn<Politician, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().total)));

        TableColumn<Politician, String> spectrumCol = new TableColumn<>("Spectrum");
        spectrumCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().spectrum)));

        TableColumn<Politician, String> femaleCol = new TableColumn<>("Female");
        femaleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isFemale ? "Yes" : "No"));

        TableColumn<Politician, String> indianCol = new TableColumn<>("Indian");
        indianCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isIndian ? "Yes" : "No"));

        // Add ALL columns to the table
        table.getColumns().addAll(idCol, nameCol, basePriceCol, volCol, totalCol, spectrumCol, femaleCol, indianCol);

        // STYLING TWEAK: Make columns automatically fill the table width
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-font-size: 14px; -fx-selection-bar: #4CAF50; -fx-selection-bar-non-focused: #81C784;");

        ObservableList<Politician> masterData = FXCollections.observableArrayList(polPool);
        FilteredList<Politician> filteredData = new FilteredList<>(masterData, p -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(politician -> {
                if (newValue == null || newValue.isEmpty()) return true;
                return politician.name.toLowerCase().contains(newValue.toLowerCase());
            });
        });

        SortedList<Politician> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedData);

        table.setRowFactory(tv -> {
            TableRow<Politician> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showBidDialog(row.getItem());
                }
            });
            return row;
        });

        root.setCenter(table);
        BorderPane.setMargin(table, new Insets(15, 0, 0, 0));

        Scene scene = new Scene(root, 1000, 700); // Increased window size to accommodate new columns
        stage.setTitle("RealPolitik Tracker Dashboard");
        stage.setScene(scene);
        stage.show();
    }

    private void showBidDialog(Politician politician) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Auction: " + politician.name);

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label("Politician: " + politician.name);
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        Label basePriceLabel = new Label("Base Price: " + politician.basePrice);
        Label maxBidLabel = new Label("Querying Engine for Max Bid...");
        maxBidLabel.setStyle("-fx-text-fill: blue; -fx-font-weight: bold;");

        new Thread(() -> {
            try {
                PoliticianMsg msg = toProto(politician);
                // TODO: HARDCODED PARAMETERS
                BidRequest request = BidRequest.newBuilder()
                        .setRequestId(UUID.randomUUID().toString())
                        .setPolitician(msg)
                        .setParam1(true).setParam2(2).setParam3(true).setParam4(1.05)
                        .build();

                BidResponse response = engineStub.getMaximumBid(request);
                Platform.runLater(() -> maxBidLabel.setText("[KAFKA] Engine Max Bid: " + response.getMaxBid()));
            } catch (Exception e) {
                Platform.runLater(() -> maxBidLabel.setText("[KAFKA] Error querying Engine!"));
                e.printStackTrace();
            }
        }).start();

        Separator sep = new Separator();
        ToggleGroup group = new ToggleGroup();
        RadioButton rbWeBought = new RadioButton("Bought by self");
        RadioButton rbTheyBought = new RadioButton("Bought by other");
        RadioButton rbUnsold = new RadioButton("Unsold");
        rbWeBought.setToggleGroup(group);
        rbTheyBought.setToggleGroup(group);
        rbUnsold.setToggleGroup(group);
        rbUnsold.setSelected(true);

        HBox priceBox = new HBox(10);
        priceBox.setAlignment(Pos.CENTER_LEFT);
        Label priceLabel = new Label("Sold Price:");
        TextField priceField = new TextField("0");
        priceField.setDisable(true);
        priceBox.getChildren().addAll(priceLabel, priceField);

        // Toggle text field based on radio buttons
        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == rbUnsold) {
                priceField.setText("0");
                priceField.setDisable(true);
            } else {
                priceField.setDisable(false);
                if (priceField.getText().equals("0")) priceField.setText(String.valueOf(politician.basePrice));
            }
        });

        Button submitBtn = new Button("Submit Result to Network");
        submitBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");

        submitBtn.setOnAction(e -> {
            try {
                int soldPrice = Integer.parseInt(priceField.getText());
                boolean weBoughtIt = rbWeBought.isSelected();

                broadcastAuctionResult(politician, weBoughtIt, soldPrice);
                dialog.close();
            } catch (NumberFormatException ex) {
                Alert a = new Alert(Alert.AlertType.ERROR, "Invalid price!");
                a.show();
            }
        });

        layout.getChildren().addAll(nameLabel, basePriceLabel, maxBidLabel, sep,
                new Label("Auction Outcome:"), rbWeBought, rbTheyBought, rbUnsold, priceBox, submitBtn);

        Scene scene = new Scene(layout, 350, 380);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void broadcastAuctionResult(Politician p, boolean bought, int soldPrice) {
        PoliticianMsg msg = toProto(p);

        AuctionResultEvent event = AuctionResultEvent.newBuilder()
                .setPolitician(msg)
                .setWasBought(bought)
                .setSoldPrice(soldPrice)
                .build();

        kafkaProducer.send(new ProducerRecord<>(topic, String.valueOf(p.ID), event.toByteArray()),
                (metadata, exception) -> {
                    if (exception != null) {
                        System.err.println("[KAFKA] ERROR: Failed to broadcast!");
                    } else {
                        System.out.println("[KAFKA] Broadcasted outcome for " + p.name + " to network (Partition " + metadata.partition() + ").");
                    }
                });
        kafkaProducer.flush();
    }

    private PoliticianMsg toProto(Politician p) {
        return PoliticianMsg.newBuilder()
                .setId(p.ID).setName(p.name).setIsIndian(p.isIndian)
                .setIsFemale(p.isFemale).setVolatilityIndex(p.volatilityIndex)
                .setSpectrum(p.spectrum).setTotal(p.total).setBasePrice(p.basePrice).build();
    }

    @Override
    public void stop() throws Exception {
        if (kafkaProducer != null) kafkaProducer.close();
        if (grpcChannel != null) grpcChannel.shutdown();
        System.out.println("Client shutdown complete.");
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter Total Budget (A): ");
        budgetA = scanner.nextInt();
        System.out.print("Enter Min Team Size (B): ");
        sizeB = scanner.nextInt();
        System.out.print("Enter Min Females (C): ");
        femalesC = scanner.nextInt();
        System.out.print("Enter Max Volatility (D): ");
        volD = scanner.nextInt();
        System.out.print("Enter Min Indians (E): ");
        indiansE = scanner.nextInt();
        System.out.println("\nParameters saved. Launching UI Dashboard...");
        launch(args);
    }
}