package org.wvicto.parcours.view;

import org.wvicto.parcours.model.GpxParser;
import org.wvicto.parcours.model.StatistiquesTrajet;
import org.wvicto.parcours.model.Trajet;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ParcoursController {
    @FXML
    private ListView<Trajet> listeTrajets;

    private final ObservableList<Trajet> trajets = FXCollections.observableArrayList();
    private List<Trajet> trajetsFiltres;

    @FXML
    private void initialize() {
        listeTrajets.setItems(trajets);
        listeTrajets.setCellFactory(lv -> new TextFieldListCell<>());
    }

    @FXML
    private void chargerTrajet() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner un fichier GPX");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers GPX (*.gpx)", "*.gpx")
        );
        Stage stage = (Stage) listeTrajets.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                Trajet trajet = GpxParser.parseFile(file);
                trajets.add(trajet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void supprimerDebut() {
        Trajet trajetSelectionne = listeTrajets.getSelectionModel().getSelectedItem();
        if (trajetSelectionne != null) {
            trajetSelectionne.supprimerDebut(10);
            listeTrajets.refresh();
        }
    }

    @FXML
    private void supprimerFin() {
        Trajet trajetSelectionne = listeTrajets.getSelectionModel().getSelectedItem();
        if (trajetSelectionne != null) {
            trajetSelectionne.supprimerFin(10);
            listeTrajets.refresh();
        }
    }

    @FXML
    private void analyserPentes() {
        Trajet trajetSelectionne = listeTrajets.getSelectionModel().getSelectedItem();
        if (trajetSelectionne != null) {
            StatistiquesTrajet stats = trajetSelectionne.getStatistiques();
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Analyse des pentes");
            alert.setHeaderText(null);
            alert.setContentText(
                String.format(
                    "📊 Statistiques pour '%s' :\n\n" +
                    "📏 Distance : %.2f km\n" +
                    "⬆️ Dénivelé + : %.1f m\n" +
                    "⬇️ Dénivelé - : %.1f m\n\n" +
                    "📈 Pente max : %.1f %%\n" +
                    "📉 Pente moyenne : %.1f %%",
                    trajetSelectionne.getNom(),
                    stats.getDistanceTotaleKm(),
                    stats.getDenivelePositif(),
                    stats.getDeniveleNegatif(),
                    stats.getPenteMax(),
                    stats.getPenteMoyenne()
                )
            );
            alert.showAndWait();
        }
    }

    @FXML
    private void ouvrirFiltre() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/Filtre.fxml"));
            Parent root = loader.load();
            FiltreController filtreController = loader.getController();
            filtreController.setTrajets(trajets);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Filtrer les trajets");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(listeTrajets.getScene().getWindow());
            filtreController.setDialogStage(dialogStage);

            dialogStage.setScene(new Scene(root, 400, 200));
            dialogStage.showAndWait();

            List<Trajet> result = filtreController.getTrajetsFiltres();
            if (result != null) {
                trajetsFiltres = result;
                afficherTrajetsFiltres();
            }
        } catch (IOException e) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Erreur");
            alert.setContentText("Impossible de charger la fenêtre de filtrage : " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void afficherTousTrajets() {
        listeTrajets.setItems(trajets);
        trajetsFiltres = null;
    }

    private void afficherTrajetsFiltres() {
        if (trajetsFiltres != null && !trajetsFiltres.isEmpty()) {
            ObservableList<Trajet> filteredList = FXCollections.observableArrayList(trajetsFiltres);
            listeTrajets.setItems(filteredList);
        }
    }
}