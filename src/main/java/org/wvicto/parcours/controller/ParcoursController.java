package org.wvicto.parcours.controller;

import org.wvicto.parcours.model.GpxParser;
import org.wvicto.parcours.model.StatistiquesTrajet;
import org.wvicto.parcours.model.Trajet;
import org.wvicto.parcours.service.GpxService;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.prefs.Preferences;

public class ParcoursController {
    @FXML
    private ListView<Trajet> listeTrajets;

    private static final String LAST_DIRECTORY_KEY = "lastDirectory";  // le champ est sauvegardé dans le registre HKEY_CURRENT_USER\Software\JavaSoft\Prefs\org\wvicto\parcours
    private Preferences prefs = Preferences.userRoot().node(this.getClass().getName());

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
        fileChooser.setTitle("Sélectionner un ou plusieurs fichiers GPX");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers GPX (*.gpx)", "*.gpx")
        );
        Stage stage = (Stage) listeTrajets.getScene().getWindow();

        // ✅ CORRECT : showOpenMultipleDialog() gère la sélection multiple TOUT SEUL
        List<File> files = fileChooser.showOpenMultipleDialog(stage);

        if (files != null && !files.isEmpty()) {
            try {
                List<Trajet> nouveauxTrajets = GpxService.chargerTrajets(files);
                trajets.addAll(nouveauxTrajets);
                showInfo("Succès", nouveauxTrajets.size() + " trajet(s) chargé(s) !");
            } catch (Exception e) {
                showError("Erreur de chargement", e.getMessage());
            }
        }
    }

    @FXML
    private void chargerDossier() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Sélectionner un dossier contenant des fichiers GPX");

        // ✅ Définis le répertoire initial comme le dernier utilisé
        File lastDir = getLastDirectory();
        if (lastDir != null) {
            directoryChooser.setInitialDirectory(lastDir);
        }

        Stage stage = (Stage) listeTrajets.getScene().getWindow();
        File directory = directoryChooser.showDialog(stage);

        if (directory != null) {
            // ✅ Sauvegarde le répertoire sélectionné
            saveLastDirectory(directory);

            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".gpx"));
            if (files != null && files.length > 0) {
                int trajetsAjoutes = 0;
                for (File file : files) {
                    try {
                        Trajet trajet = GpxParser.parseFile(file);
                        trajets.add(trajet);
                        trajetsAjoutes++;
                    } catch (Exception e) {
                        showError("Erreur",
                                 "Fichier '" + file.getName() + "' non valide : " + e.getMessage());
                    }
                }
                showInfo("Succès", trajetsAjoutes + " trajet(s) chargé(s) depuis le dossier !");
            } else {
                showError("Aucun fichier", "Aucun fichier GPX trouvé dans ce dossier.");
            }
        }
    }

    // Méthode utilitaire pour afficher une info (à ajouter si ce n'est pas déjà fait)
    private void showInfo(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

 // Méthode utilitaire pour afficher une alerte (à ajouter dans la classe)
    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/wvicto/parcours/view/Filtre.fxml"));
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
    
    
    @FXML
    private void ouvrirCarte() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/wvicto/parcours/view/CartePoints.fxml"));
            Parent root = loader.load();
            CartePointsController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Carte - Sélection de points");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(listeTrajets.getScene().getWindow());
            controller.setStage(stage);

            stage.setScene(new Scene(root, 800, 650));
            stage.showAndWait();
        } catch (IOException e) {
            showError("Erreur", "Impossible de charger la carte : " + e.getMessage());
        }
    }
    
    
    // Méthode pour sauvegarder le dernier répertoire
    private void saveLastDirectory(File directory) {
        if (directory != null) {
            prefs.put(LAST_DIRECTORY_KEY, directory.getAbsolutePath());
        }
    }

    // Méthode pour récupérer le dernier répertoire
    private File getLastDirectory() {
        String lastDir = prefs.get(LAST_DIRECTORY_KEY, null);
        if (lastDir != null && new File(lastDir).exists()) {
            return new File(lastDir);
        }
        return null; // Retourne null si aucun répertoire enregistré ou invalide
    }
}