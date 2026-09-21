package org.wvicto.parcours.controller;

import org.wvicto.parcours.model.FiltreTrajet;
import org.wvicto.parcours.model.PointGpx;
import org.wvicto.parcours.model.Trajet;
import org.wvicto.parcours.model.PointRemarquable;
import org.wvicto.parcours.model.PortionRemarquable;
import org.wvicto.parcours.service.PointRemarquableService;
import org.wvicto.parcours.util.Constants;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.ChoiceDialog;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Contrôleur pour la fenêtre de filtrage des trajets.
 * Permet de filtrer par point ou par zone géographiques.
 */
public class FiltreController {

    private List<Trajet> trajets;
    private List<Trajet> trajetsFiltres;
    private Stage dialogStage;

    public void setTrajets(List<Trajet> trajets) {
        this.trajets = trajets;
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public List<Trajet> getTrajetsFiltres() {
        return trajetsFiltres;
    }

    @FXML
    private void filtrerParPoint() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Filtrer par point");
        dialog.setHeaderText("Saisir les coordonnées du point (latitude,longitude)");
        dialog.setContentText("Exemple: 48.8566,2.3522");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                String[] coords = result.get().split(",");
                if (coords.length != 2) {
                    throw new IllegalArgumentException("Format invalide. Utilisez: latitude,longitude");
                }
                double latitude = Double.parseDouble(coords[0].trim());
                double longitude = Double.parseDouble(coords[1].trim());

                // Demander le rayon
                TextInputDialog radiusDialog = new TextInputDialog("0.5");
                radiusDialog.setTitle("Rayon de recherche");
                radiusDialog.setHeaderText("Saisir le rayon en kilomètres");
                radiusDialog.setContentText("Exemple: 0.5 (pour 500m)");

                Optional<String> radiusResult = radiusDialog.showAndWait();
                if (radiusResult.isPresent()) {
                    double radiusKm = Double.parseDouble(radiusResult.get());
                    PointGpx point = new PointGpx(latitude, longitude, 0, null);
                    trajetsFiltres = FiltreTrajet.filtrerParProximite(trajets, point, radiusKm);
                    dialogStage.close();
                }
            } catch (NumberFormatException e) {
                showError("Erreur de format", "Les coordonnées doivent être des nombres.");
            } catch (IllegalArgumentException e) {
                showError("Erreur", e.getMessage());
            }
        }
    }

    @FXML
    private void filtrerParZone() {
        // Demander le coin Nord-Ouest
        TextInputDialog dialogNO = new TextInputDialog();
        dialogNO.setTitle("Filtrer par zone");
        dialogNO.setHeaderText("Saisir le coin Nord-Ouest (latitude,longitude)");
        dialogNO.setContentText("Exemple: 48.8566,2.3522");

        Optional<String> resultNO = dialogNO.showAndWait();
        if (!resultNO.isPresent()) {
            return;
        }

        // Demander le coin Sud-Est
        TextInputDialog dialogSE = new TextInputDialog();
        dialogSE.setTitle("Filtrer par zone");
        dialogSE.setHeaderText("Saisir le coin Sud-Est (latitude,longitude)");
        dialogSE.setContentText("Exemple: 48.8049,2.1204");

        Optional<String> resultSE = dialogSE.showAndWait();
        if (!resultSE.isPresent()) {
            return;
        }

        try {
            String[] coordsNO = resultNO.get().split(",");
            String[] coordsSE = resultSE.get().split(",");

            if (coordsNO.length != 2 || coordsSE.length != 2) {
                throw new IllegalArgumentException("Format invalide. Utilisez: latitude,longitude");
            }

            double latNO = Double.parseDouble(coordsNO[0].trim());
            double lonNO = Double.parseDouble(coordsNO[1].trim());
            double latSE = Double.parseDouble(coordsSE[0].trim());
            double lonSE = Double.parseDouble(coordsSE[1].trim());

            PointGpx coinNordOuest = new PointGpx(latNO, lonNO, 0, null);
            PointGpx coinSudEst = new PointGpx(latSE, lonSE, 0, null);

            trajetsFiltres = FiltreTrajet.filtrerParZone(trajets, coinNordOuest, coinSudEst);
            dialogStage.close();
        } catch (NumberFormatException e) {
            showError("Erreur de format", "Les coordonnées doivent être des nombres.");
        } catch (IllegalArgumentException e) {
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void filtrerParPointsRemarquables() {
        List<PointRemarquable> pointsDisponibles;
        try {
            pointsDisponibles = PointRemarquableService.charger(new File(Constants.FICHIER_POINTS_REMARQUABLES));
        } catch (IOException e) {
            showError("Erreur", "Impossible de charger les points remarquables : " + e.getMessage());
            return;
        }

        if (pointsDisponibles.size() < 2) {
            showError("Pas assez de points",
                "Il faut au moins 2 points remarquables enregistrés (clic-droit sur la carte) pour filtrer ainsi.");
            return;
        }

        List<PointRemarquable> sequence = new ArrayList<>();
        List<PointRemarquable> restants = new ArrayList<>(pointsDisponibles);
        int etape = 1;

        while (!restants.isEmpty()) {
            String header = etape <= 2
                ? "Choisir le point n°" + etape + " de la séquence"
                : "Choisir le point n°" + etape + " (Annuler pour arrêter avec " + sequence.size() + " points)";

            ChoiceDialog<PointRemarquable> choix = new ChoiceDialog<>(restants.get(0), restants);
            choix.setTitle("Points remarquables");
            choix.setHeaderText(header);
            choix.setContentText("Point :");

            Optional<PointRemarquable> resultat = choix.showAndWait();
            if (resultat.isEmpty()) {
                if (etape <= 2) {
                    return; // annulé avant d'avoir 2 points : on abandonne le filtre
                }
                break; // l'utilisateur arrête la séquence ici
            }

            PointRemarquable point = resultat.get();
            sequence.add(point);
            restants.remove(point);
            etape++;
        }

        TextInputDialog radiusDialog = new TextInputDialog("0.05");
        radiusDialog.setTitle("Rayon de tolérance");
        radiusDialog.setHeaderText("Distance maximale pour considérer qu'un trajet passe par un point (en km)");
        radiusDialog.setContentText("Exemple: 0.05 (pour 50m)");

        Optional<String> radiusResult = radiusDialog.showAndWait();
        if (radiusResult.isEmpty()) {
            return;
        }

        try {
            double rayonKm = Double.parseDouble(radiusResult.get());
            PortionRemarquable portion = new PortionRemarquable("Recherche", sequence, rayonKm);
            trajetsFiltres = FiltreTrajet.filtrerParPortion(trajets, portion);
            dialogStage.close();
        } catch (NumberFormatException e) {
            showError("Erreur de format", "Le rayon doit être un nombre.");
        }
    }
    
    @FXML
    private void annuler() {
        trajetsFiltres = null;
        dialogStage.close();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}