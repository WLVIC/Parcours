package org.wvicto.parcours.controller;

import org.wvicto.parcours.model.FiltreTrajet;
import org.wvicto.parcours.model.PointGpx;
import org.wvicto.parcours.model.Trajet;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import java.util.List;
import java.util.Optional;

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