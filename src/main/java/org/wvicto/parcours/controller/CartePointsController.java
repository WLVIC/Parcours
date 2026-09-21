package org.wvicto.parcours.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import org.jxmapviewer.viewer.GeoPosition;
import org.wvicto.parcours.model.PointRemarquable;
import org.wvicto.parcours.model.Trajet;
import org.wvicto.parcours.service.CarteService;
import org.wvicto.parcours.service.OpenStreetMapProvider;
import org.wvicto.parcours.service.PointRemarquableService;
import org.wvicto.parcours.util.Constants;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class CartePointsController {
    @FXML
    private Pane mapContainer;

    @FXML
    private Label coordsLabel;

    private CarteService carteService;

    private final File fichierPoints = new File(Constants.FICHIER_POINTS_REMARQUABLES);
    private final ObservableList<PointRemarquable> pointsRemarquables = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        carteService = new CarteService(
            mapContainer,
            new OpenStreetMapProvider()
        );

        carteService.setOnMapClicked(geo -> {
            coordsLabel.setText(String.format(
                "Latitude: %.6f, Longitude: %.6f",
                geo.getLatitude(),
                geo.getLongitude()
            ));
        });

        carteService.setOnMapRightClicked(this::ouvrirDialogueAjoutPoint);

        chargerPointsExistants();
    }

    private void chargerPointsExistants() {
        try {
            pointsRemarquables.setAll(PointRemarquableService.charger(fichierPoints));
            carteService.afficherPointsRemarquables(pointsRemarquables);
        } catch (IOException e) {
            showErreur("Erreur de chargement", "Impossible de charger les points remarquables : " + e.getMessage());
        }
    }

    /**
     * Ouvre un petit dialogue demandant le nom et la catégorie du point, puis
     * l'ajoute à la liste et sauvegarde immédiatement (nom vide = annulé).
     */
    private void ouvrirDialogueAjoutPoint(GeoPosition geo) {
        Dialog<PointRemarquable> dialogue = new Dialog<>();
        dialogue.setTitle("Nouveau point remarquable");
        dialogue.setHeaderText(String.format(
            "Position : %.6f, %.6f", geo.getLatitude(), geo.getLongitude()));

        ButtonType boutonAjouter = new ButtonType("Ajouter", ButtonType.OK.getButtonData());
        dialogue.getDialogPane().getButtonTypes().addAll(boutonAjouter, ButtonType.CANCEL);

        TextField champNom = new TextField();
        champNom.setPromptText("ex: Maison, Travail, Boulangerie...");
        TextField champCategorie = new TextField();
        champCategorie.setPromptText("ex: Domicile, Commerce... (facultatif)");

        GridPane grille = new GridPane();
        grille.setHgap(10);
        grille.setVgap(10);
        grille.setPadding(new Insets(20, 20, 10, 10));
        grille.add(new Label("Nom :"), 0, 0);
        grille.add(champNom, 1, 0);
        grille.add(new Label("Catégorie :"), 0, 1);
        grille.add(champCategorie, 1, 1);
        dialogue.getDialogPane().setContent(grille);

        dialogue.setResultConverter(bouton -> {
            if (bouton == boutonAjouter && !champNom.getText().isBlank()) {
                return new PointRemarquable(
                    champNom.getText().trim(),
                    champCategorie.getText().trim(),
                    geo.getLatitude(),
                    geo.getLongitude()
                );
            }
            return null;
        });

        Optional<PointRemarquable> resultat = dialogue.showAndWait();
        resultat.ifPresent(point -> {
            pointsRemarquables.add(point);
            sauvegarderPoints();
            carteService.afficherPointsRemarquables(pointsRemarquables);
            coordsLabel.setText("Point ajouté : " + point.getNom());
        });
    }

    private void sauvegarderPoints() {
        try {
            fichierPoints.getParentFile().mkdirs();
            PointRemarquableService.sauvegarder(pointsRemarquables, fichierPoints);
        } catch (IOException e) {
            showErreur("Erreur de sauvegarde", "Impossible d'enregistrer le point : " + e.getMessage());
        }
    }

    private void showErreur(String titre, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Affiche des trajets sur la carte, avec celui sélectionné mis en évidence.
     * Simple délégation à CarteService : ce contrôleur ne sait pas dessiner, il
     * orchestre juste le service qui sait.
     */
    public void afficherTrajets(List<Trajet> trajets, Trajet selectionne) {
        carteService.afficherTrajets(trajets, selectionne);
    }

    /**
     * Permet à l'appelant (le futur contrôleur principal) d'être notifié quand
     * l'utilisateur clique sur le tracé d'un trajet affiché sur la carte.
     */
    public void setOnTrajetClicked(Consumer<Trajet> callback) {
        carteService.setOnTrajetClicked(callback);
    }

}