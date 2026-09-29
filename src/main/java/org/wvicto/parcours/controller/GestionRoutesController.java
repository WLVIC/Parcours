package org.wvicto.parcours.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.stage.Stage;
import org.wvicto.parcours.model.Route;
import org.wvicto.parcours.service.RoutePersistenceService;
import org.wvicto.parcours.util.Constants;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Fenêtre de gestion des routes enregistrées : liste et suppression
 * (multi-sélection possible). Pas d'édition pour l'instant, seulement suppression.
 */
public class GestionRoutesController {
    @FXML
    private ListView<Route> listeRoutes;

    private Stage stage;
    private final File fichierRoutes = new File(Constants.FICHIER_ROUTES);
    private final ObservableList<Route> routes = FXCollections.observableArrayList();

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void initialize() {
        listeRoutes.setItems(routes);
        listeRoutes.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        chargerRoutes();
    }

    private void chargerRoutes() {
        try {
            routes.setAll(RoutePersistenceService.charger(fichierRoutes));
        } catch (IOException e) {
            showErreur("Erreur de chargement", "Impossible de charger les routes : " + e.getMessage());
        }
    }

    @FXML
    private void supprimerSelection() {
        List<Route> selection = List.copyOf(listeRoutes.getSelectionModel().getSelectedItems());
        if (selection.isEmpty()) {
            return;
        }

        Alert confirmation = new Alert(AlertType.CONFIRMATION);
        confirmation.setTitle("Supprimer");
        confirmation.setHeaderText("Supprimer " + selection.size() + " route(s) ?");
        confirmation.setContentText("Cette action est irréversible.");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        routes.removeAll(selection);
        sauvegarderRoutes();
    }

    private void sauvegarderRoutes() {
        try {
            RoutePersistenceService.sauvegarder(routes, fichierRoutes);
        } catch (IOException e) {
            showErreur("Erreur de sauvegarde", "Impossible d'enregistrer : " + e.getMessage());
        }
    }

    private void showErreur(String titre, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void fermer() {
        stage.close();
    }
}