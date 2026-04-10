package com.github.evermore;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.List;
import java.util.Scanner;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        var label = new Label("Hello, JavaFX");
        var scene = new Scene(new StackPane(label), 640, 480);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        List<Politician> list = CSVReader.parseCSV("list.csv");

        Scanner scanner = new Scanner(System.in);
        IO.print("Enter Total Budget (A): ");
        int budgetA = scanner.nextInt();

        IO.print("Enter Min Team Size (B): ");
        int sizeB = scanner.nextInt();

        IO.print("Enter Min Females (C): ");
        int femalesC = scanner.nextInt();

        IO.print("Enter Max Volatility (D): ");
        int volD = scanner.nextInt();

        IO.print("Enter Min Indians (E): ");
        int indiansE = scanner.nextInt();

        ValuationEngine engine = new ValuationEngine(budgetA, sizeB, femalesC, volD, indiansE, list);
        double mu = 1.95, alpha = 0.2;

        engine.updateExpectedPrices(mu);
        IO.println("\nGame initialized. Expected market multiplier starting at: " + mu + "x");

        for (int i = 0; i < 5; i++) {
            Politician p = list.get(i);
            System.out.println("--> QUERIED: " + p.name + " (ID: " + p.ID + ") | Base Price: " + p.basePrice + " | Score: " + p.total + " | Vol: " + p.volatilityIndex);

            int maxBid = engine.getMaximumBid(p, true, 2, false, 1.05);
            IO.println("*** YOUR MAX BID: " + maxBid + " ***");

            IO.print("Who won? (1 = Me, 2 = Opponent, 0 = Unsold): ");
            int winner = scanner.nextInt();

            int soldPrice = 0;
            if (winner != 0) {
                System.out.print("At what price?: ");
                soldPrice = scanner.nextInt();
            }

            boolean iWon = (winner == 1);
            boolean wasSold = (winner != 0);
            engine.recordAuctionResult(p, iWon, iWon ? soldPrice : 0);
            if (wasSold) {
                double M = (double) soldPrice / p.basePrice;
                mu = (alpha * M) + ((1 - alpha) * mu);

                System.out.printf("EMA updated to: %.2fx\n", mu);

                engine.updateExpectedPrices(mu);
            } else {
                IO.println("Unsold. Skipping EMA update.");
            }
        }

        System.out.println("Auction Complete. Launching GUI...");
        scanner.close();
        launch();
    }
}