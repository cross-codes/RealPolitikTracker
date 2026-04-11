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
import javafx.geometry.Orientation;
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

    private static final String topic = "auction-results-v19";
    private ManagedChannel grpcChannel;
    private ValuationServiceGrpc.ValuationServiceBlockingStub engineStub;
    private KafkaProducer<String, byte[]> kafkaProducer;

    private List<Politician> polPool;

    private ObservableList<Politician> pool;
    private final ObservableList<Politician> currTeamData = FXCollections.observableArrayList();

    private static int budgetA, sizeB, femalesC, volD, indiansE;

    private Label bLabel, sLabel, fLabel, vLabel, iLabel;
    private CheckBox cbVolBuffer, cbPriceBuffer;
    private TextField tfVolBuffer, tfPriceBuffer;
    private Label avglabel1, avglabel2, avglabel3, avglabel4;

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
        pool = FXCollections.observableArrayList(polPool);
    }

    @Override
    public void start(Stage primaryStage) {
        showMainDashboard(primaryStage);
    }

    private void showMainDashboard(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        VBox topBox = new VBox(15);

        HBox statsBox = new HBox(20);
        statsBox.setAlignment(Pos.CENTER_LEFT);
        statsBox.setStyle("-fx-background-color: #ffffff; -fx-padding: 15; " + "-fx-border-color: #d1d5db; -fx-border-radius: 8; " + "-fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 2);");

        bLabel = new Label();
        sLabel = new Label();
        fLabel = new Label();
        vLabel = new Label();
        iLabel = new Label();

        statsBox.getChildren().addAll(bLabel, sLabel, fLabel, vLabel, iLabel);

        currTeamData.addListener((javafx.collections.ListChangeListener.Change<? extends Politician> _) -> updateStats());

        VBox engineConfigBox = new VBox(10);
        engineConfigBox.setAlignment(Pos.CENTER_LEFT);

        HBox volBox = new HBox(10);
        volBox.setAlignment(Pos.CENTER_LEFT);
        cbVolBuffer = new CheckBox("Apply Volatility Buffer?");
        cbVolBuffer.setSelected(true);
        tfVolBuffer = new TextField("2");
        tfVolBuffer.setPrefWidth(60);
        tfVolBuffer.disableProperty().bind(cbVolBuffer.selectedProperty().not());
        volBox.getChildren().addAll(cbVolBuffer, tfVolBuffer);

        HBox priceBox = new HBox(10);
        priceBox.setAlignment(Pos.CENTER_LEFT);
        cbPriceBuffer = new CheckBox("Apply Price Buffer?");
        cbPriceBuffer.setSelected(true);
        tfPriceBuffer = new TextField("1.01");
        tfPriceBuffer.setPrefWidth(60);
        tfPriceBuffer.disableProperty().bind(cbPriceBuffer.selectedProperty().not());
        priceBox.getChildren().addAll(cbPriceBuffer, tfPriceBuffer);

        engineConfigBox.getChildren().addAll(volBox, priceBox);

        VBox projectionBox = new VBox(5);
        projectionBox.setAlignment(Pos.CENTER_LEFT);

        Label projTitle = new Label("Avg spend per slot to reach:");
        projTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #4b5563;");

        avglabel1 = new Label();
        avglabel2 = new Label();
        avglabel3 = new Label();
        avglabel4 = new Label();

        String projRowStyle = "-fx-font-size: 12px;";
        avglabel1.setStyle(projRowStyle);
        avglabel2.setStyle(projRowStyle);
        avglabel3.setStyle(projRowStyle);
        avglabel4.setStyle(projRowStyle);

        projectionBox.getChildren().addAll(projTitle, avglabel1, avglabel2, avglabel3, avglabel4);
        updateStats();

        HBox headerRow = new HBox(20);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        headerRow.getChildren().addAll(statsBox, spacer, engineConfigBox, new Separator(Orientation.VERTICAL), projectionBox);

        TextField searchField = new TextField();
        searchField.setPromptText("Search politician by name...");
        searchField.setStyle("-fx-padding: 8; -fx-font-size: 14px; -fx-border-radius: 5; -fx-background-radius: 5;");
        topBox.getChildren().addAll(headerRow, searchField);
        root.setTop(topBox);

        TableView<Politician> poolTable = new TableView<>();
        TableColumn<Politician, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().ID)));
        idCol.setMaxWidth(50);

        TableColumn<Politician, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name));

        TableColumn<Politician, String> basePriceCol = new TableColumn<>("Base Price");
        basePriceCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().basePrice)));

        TableColumn<Politician, String> volCol = new TableColumn<>("Volatility");
        volCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().volatilityIndex)));

        TableColumn<Politician, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().total)));

        TableColumn<Politician, String> spectrumCol = new TableColumn<>("Spectrum");
        spectrumCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().spectrum)));

        TableColumn<Politician, String> femaleCol = new TableColumn<>("Female");
        femaleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isFemale ? "Yes" : "No"));

        TableColumn<Politician, String> indianCol = new TableColumn<>("Indian");
        indianCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isIndian ? "Yes" : "No"));

        poolTable.getColumns().addAll(idCol, nameCol, basePriceCol, volCol, totalCol, spectrumCol, femaleCol, indianCol);
        poolTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        poolTable.setStyle("-fx-font-size: 14px; -fx-selection-bar: #4CAF50; -fx-selection-bar-non-focused: #81C784;");

        FilteredList<Politician> filteredData = new FilteredList<>(pool, _ -> true);
        searchField.textProperty().addListener((_, _, newValue) -> filteredData.setPredicate(politician -> {
            if (newValue == null || newValue.isEmpty()) return true;
            return politician.name.toLowerCase().contains(newValue.toLowerCase());
        }));

        SortedList<Politician> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(poolTable.comparatorProperty());
        poolTable.setItems(sortedData);

        poolTable.setRowFactory(_ -> {
            TableRow<Politician> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showBidDialog(row.getItem());
                }
            });
            return row;
        });

        TableView<Politician> teamTable = new TableView<>();
        TableColumn<Politician, String> teamIdCol = new TableColumn<>("ID");
        teamIdCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().ID)));
        teamIdCol.setMaxWidth(50);

        TableColumn<Politician, String> teamNameCol = new TableColumn<>("Name");
        teamNameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name));

        TableColumn<Politician, String> teamBasePriceCol = new TableColumn<>("Bought Price");
        teamBasePriceCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().basePrice)));

        TableColumn<Politician, String> teamVolCol = new TableColumn<>("Volatility");
        teamVolCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().volatilityIndex)));

        TableColumn<Politician, String> teamTotalCol = new TableColumn<>("Total");
        teamTotalCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().total)));

        TableColumn<Politician, String> teamSpectrumCol = new TableColumn<>("Spectrum");
        teamSpectrumCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().spectrum)));

        TableColumn<Politician, String> teamFemaleCol = new TableColumn<>("Female");
        teamFemaleCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isFemale ? "Yes" : "No"));

        TableColumn<Politician, String> teamIndianCol = new TableColumn<>("Indian");
        teamIndianCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isIndian ? "Yes" : "No"));

        teamTable.getColumns().addAll(teamIdCol, teamNameCol, teamBasePriceCol, teamVolCol, teamTotalCol, teamSpectrumCol, teamFemaleCol, teamIndianCol);
        teamTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        teamTable.setItems(currTeamData);
        teamTable.setStyle("-fx-font-size: 14px;");

        Label poolLabel = new Label("Remaining pool");
        poolLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        VBox topSide = new VBox(10, poolLabel, poolTable);
        VBox.setVgrow(poolTable, Priority.ALWAYS);

        Label teamLabel = new Label("Current roster");
        teamLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        VBox bottomSide = new VBox(10, teamLabel, teamTable);
        VBox.setVgrow(teamTable, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(topSide, bottomSide);
        splitPane.setDividerPositions(0.65);

        root.setCenter(splitPane);
        BorderPane.setMargin(splitPane, new Insets(15, 0, 0, 0));

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("Auction dashboard");
        stage.setScene(scene);
        stage.show();
    }

    private void updateStats() {
        int curSpent = 0;
        int curVol = 0;
        int curFemales = 0;
        int curIndians = 0;
        int curSize = currTeamData.size();

        for (Politician p : currTeamData) {
            curSpent += p.basePrice; // This was updated if we bought it
            curVol += p.volatilityIndex;
            if (p.isFemale) curFemales++;
            if (p.isIndian) curIndians++;
        }

        bLabel.setText(String.format("Amount Spent: %d / %d", curSpent, budgetA));
        sLabel.setText(String.format("Team Size: %d / %d", curSize, sizeB));
        fLabel.setText(String.format("Females: %d / %d", curFemales, femalesC));
        vLabel.setText(String.format("Volatility: %d / %d", curVol, volD));
        iLabel.setText(String.format("Indians: %d / %d", curIndians, indiansE));

        String baseStyle = "-fx-padding: 5 10; -fx-background-radius: 5; -fx-font-weight: bold; ";
        String greenStyle = baseStyle + "-fx-background-color: #a7f3d0; -fx-text-fill: #065f46;";
        String redStyle = baseStyle + "-fx-background-color: #fecaca; -fx-text-fill: #991b1b;";

        bLabel.setStyle(curSpent <= budgetA ? greenStyle : redStyle);
        vLabel.setStyle(curVol <= volD ? greenStyle : redStyle);

        sLabel.setStyle(curSize >= sizeB ? greenStyle : redStyle);
        fLabel.setStyle(curFemales >= femalesC ? greenStyle : redStyle);
        iLabel.setStyle(curIndians >= indiansE ? greenStyle : redStyle);

        int budgetLeft = budgetA - curSpent;
        updateProjectionLabel(avglabel1, sizeB, curSize, budgetLeft);
        updateProjectionLabel(avglabel2, sizeB + 1, curSize, budgetLeft);
        updateProjectionLabel(avglabel3, sizeB + 2, curSize, budgetLeft);
        updateProjectionLabel(avglabel4, sizeB + 3, curSize, budgetLeft);
    }

    private void updateProjectionLabel(Label lbl, int targetSize, int curSize, int budgetLeft) {
        if (curSize >= targetSize) {
            lbl.setText(String.format("Size %d: Met", targetSize));
            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #065f46; -fx-font-weight: bold;");
        } else {
            double avg = (double) budgetLeft / (targetSize - curSize);
            lbl.setText(String.format("Size %d: %.1f / slot", targetSize, avg));
            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
        }
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

        boolean applyVol = cbVolBuffer.isSelected();
        int[] volBuf = {0};
        if (applyVol) {
            try {
                volBuf[0] = Integer.parseInt(tfVolBuffer.getText());
            } catch (NumberFormatException ex) {
                volBuf[0] = 2;
            }
        }

        boolean applyPrice = cbPriceBuffer.isSelected();
        double[] infFactor = {1.0};
        if (applyPrice) {
            try {
                infFactor[0] = Double.parseDouble(tfPriceBuffer.getText());
            } catch (NumberFormatException ex) {
                infFactor[0] = 1.05;
            }
        }

        new Thread(() -> {
            try {
                PoliticianMsg msg = toProto(politician);
                BidRequest request = BidRequest.newBuilder().setRequestId(UUID.randomUUID().toString()).setPolitician(msg).setParam1(applyVol).setParam2(volBuf[0]).setParam3(applyPrice).setParam4(infFactor[0]).build();

                BidResponse response = engineStub.getMaximumBid(request);
                Platform.runLater(() -> maxBidLabel.setText("Recommended maximum bid: " + response.getMaxBid()));
            } catch (Exception e) {
                Platform.runLater(() -> maxBidLabel.setText("Error querying Engine!"));
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

        group.selectedToggleProperty().addListener((_, _, newVal) -> {
            if (newVal == rbUnsold) {
                priceField.setText("0");
                priceField.setDisable(true);
            } else {
                priceField.setDisable(false);
                if (priceField.getText().equals("0")) priceField.setText(String.valueOf(politician.basePrice));
            }
        });

        Button submitBtn = new Button("Submit result to engine and proceed");
        submitBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");

        submitBtn.setOnAction(_ -> {
            try {
                int soldPrice = Integer.parseInt(priceField.getText().trim());
                boolean weBoughtIt = rbWeBought.isSelected();

                if (weBoughtIt) {
                    politician.basePrice = soldPrice;
                }

                pool.removeIf(p -> p.ID == politician.ID);
                polPool.removeIf(p -> p.ID == politician.ID);

                if (weBoughtIt) {
                    currTeamData.add(politician);
                }

                broadcastAuctionResult(politician, weBoughtIt, soldPrice);
                dialog.close();

            } catch (NumberFormatException ex) {
                Alert a = new Alert(Alert.AlertType.ERROR, "Invalid price! Please enter a valid number without spaces.");
                a.setHeaderText("Input Error");
                a.show();
            }
        });

        layout.getChildren().addAll(nameLabel, basePriceLabel, maxBidLabel, sep, new Label("Auction Outcome:"), rbWeBought, rbTheyBought, rbUnsold, priceBox, submitBtn);

        Scene scene = new Scene(layout, 350, 380);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void broadcastAuctionResult(Politician p, boolean bought, int soldPrice) {
        PoliticianMsg msg = toProto(p);

        AuctionResultEvent event = AuctionResultEvent.newBuilder().setPolitician(msg).setWasBought(bought).setSoldPrice(soldPrice).build();

        kafkaProducer.send(new ProducerRecord<>(topic, String.valueOf(p.ID), event.toByteArray()), (metadata, exception) -> {
            if (exception != null) {
                System.err.println("[KAFKA] ERROR: Failed to broadcast!");
            } else {
                System.out.println("[KAFKA] Broadcasted outcome for " + p.name + " to network (Partition " + metadata.partition() + ").");
            }
        });
        kafkaProducer.flush();
    }

    private PoliticianMsg toProto(Politician p) {
        return PoliticianMsg.newBuilder().setId(p.ID).setName(p.name).setIsIndian(p.isIndian).setIsFemale(p.isFemale).setVolatilityIndex(p.volatilityIndex).setSpectrum(p.spectrum).setTotal(p.total).setBasePrice(p.basePrice).build();
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
        launch(args);
    }
}