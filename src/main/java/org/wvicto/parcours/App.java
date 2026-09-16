package org.wvicto.parcours;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Charge la vue principale (MainView.fxml)
        Parent root = FXMLLoader.load(getClass().getResource("Parcours.fxml"));
        primaryStage.setTitle("Parcours - Gestion des trajets GPX");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args); // Lance l'application JavaFX
    }
}