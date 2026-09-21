package org.wvicto.parcours.controller;

import org.wvicto.parcours.model.GpxParser;
import org.wvicto.parcours.model.FiltreTrajet;
import org.wvicto.parcours.model.Route;
import org.wvicto.parcours.model.StatistiquesTrajet;
import org.wvicto.parcours.model.Trajet;
import org.wvicto.parcours.service.GpxService;
import org.wvicto.parcours.service.RoutePersistenceService;
import org.wvicto.parcours.service.RouteService;
import org.wvicto.parcours.util.Constants;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

public class ParcoursController {
    @FXML
    private ListView<Trajet> listeTrajets;

    private static final String LAST_DIRECTORY_KEY = "lastDirectory";
    private Preferences prefs = Preferences.userRoot().node(this.getClass().getName());

    private final ObservableList<Trajet> trajets = FXCollections.observableArrayList();
    private List<Trajet> trajetsFiltres;
 // Champ : annoté @FXML, injecté par fx:include
    @FXML
    private CartePointsController carteController;

    @FXML
    private void initialize() {
        listeTrajets.setItems(trajets);
        listeTrajets.setCellFactory(lv -> new TextFieldListCell<>());

        // Sélectionner un trajet dans la liste met à jour sa mise en évidence sur la carte
        // (si elle est ouverte). Ce même listener se déclenche aussi quand la sélection
        // est réinitialisée par un changement de liste (filtrage, tout afficher...).
        listeTrajets.getSelectionModel().selectedItemProperty()
            .addListener((obs, ancien, nouveau) -> afficherTrajetsSurCarte());
        
        carteController.setOnTrajetClicked(trajet ->
        listeTrajets.getSelectionModel().select(trajet));
    }

    /**
     * Pousse la liste actuellement affichée (filtrée ou non) et le trajet sélectionné
     * vers la carte
     */
    private void afficherTrajetsSurCarte() {
        List<Trajet> listeAffichee = trajetsFiltres != null ? trajetsFiltres : trajets;
        Trajet selectionne = listeTrajets.getSelectionModel().getSelectedItem();
        carteController.afficherTrajets(listeAffichee, selectionne);
    }

    @FXML
    private void chargerTrajet() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner un ou plusieurs fichiers GPX");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers GPX (*.gpx)", "*.gpx")
        );
        Stage stage = (Stage) listeTrajets.getScene().getWindow();

        List<File> files = fileChooser.showOpenMultipleDialog(stage);

        if (files != null && !files.isEmpty()) {
            try {
                List<Trajet> nouveauxTrajets = GpxService.chargerTrajets(files);
                trajets.addAll(nouveauxTrajets);
                afficherTrajetsSurCarte();
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

        File lastDir = getLastDirectory();
        if (lastDir != null) {
            directoryChooser.setInitialDirectory(lastDir);
        }

        Stage stage = (Stage) listeTrajets.getScene().getWindow();
        File directory = directoryChooser.showDialog(stage);

        if (directory != null) {
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
                afficherTrajetsSurCarte();
            } else {
                showError("Aucun fichier", "Aucun fichier GPX trouvé dans ce dossier.");
            }
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

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
        afficherTrajetsSurCarte();
    }

    private void afficherTrajetsFiltres() {
        ObservableList<Trajet> filteredList = trajetsFiltres != null
            ? FXCollections.observableArrayList(trajetsFiltres)
            : FXCollections.observableArrayList();
        listeTrajets.setItems(filteredList);
        afficherTrajetsSurCarte();

        if (filteredList.isEmpty()) {
            showInfo("Aucun résultat", "Aucun trajet trouvé.");
        }
    }

    /**
     * Désigne le trajet sélectionné comme "typique" d'un itinéraire, et génère
     * une Route (équivalent GPX <rte>) simplifiée à partir de son tracé.
     */
    @FXML
    private void genererRouteDepuisTrajet() {
        Trajet trajetSelectionne = listeTrajets.getSelectionModel().getSelectedItem();
        if (trajetSelectionne == null) {
            showError("Aucune sélection", "Sélectionne d'abord un trajet dans la liste.");
            return;
        }

        TextInputDialog nomDialog = new TextInputDialog(trajetSelectionne.getNom());
        nomDialog.setTitle("Nouvelle route");
        nomDialog.setHeaderText("Nom de la route à créer à partir de ce trajet");
        nomDialog.setContentText("Nom :");
        Optional<String> nomResult = nomDialog.showAndWait();
        if (nomResult.isEmpty() || nomResult.get().isBlank()) {
            return;
        }

        TextInputDialog toleranceDialog = new TextInputDialog("0.03");
        toleranceDialog.setTitle("Tolérance de simplification");
        toleranceDialog.setHeaderText("Tolérance en km (plus grand = moins de points conservés)");
        toleranceDialog.setContentText("Exemple : 0.03 (30m)");
        Optional<String> toleranceResult = toleranceDialog.showAndWait();
        if (toleranceResult.isEmpty()) {
            return;
        }

        try {
            double toleranceKm = Double.parseDouble(toleranceResult.get());
            Route route = RouteService.genererDepuisTrajet(trajetSelectionne, nomResult.get().trim(), toleranceKm);

            File fichierRoutes = new File(Constants.FICHIER_ROUTES);
            fichierRoutes.getParentFile().mkdirs();
            List<Route> routes = RoutePersistenceService.charger(fichierRoutes);
            routes.add(route);
            RoutePersistenceService.sauvegarder(routes, fichierRoutes);

            showInfo("Route créée", String.format(
                "Route '%s' créée : %d points conservés sur %d points du trajet d'origine.",
                route.getNom(), route.getPoints().size(), trajetSelectionne.getPoints().size()));
        } catch (NumberFormatException e) {
            showError("Erreur de format", "La tolérance doit être un nombre.");
        } catch (IOException e) {
            showError("Erreur", "Impossible d'enregistrer la route : " + e.getMessage());
        }
    }

    /**
     * Répartit les trajets chargés selon la route de référence qu'ils suivent
     * (ou "Aucune correspondance"), puis propose d'afficher un des groupes obtenus.
     */
    @FXML
    private void classifierParRoutes() {
        File fichierRoutes = new File(Constants.FICHIER_ROUTES);
        List<Route> routes;
        try {
            routes = RoutePersistenceService.charger(fichierRoutes);
        } catch (IOException e) {
            showError("Erreur", "Impossible de charger les routes : " + e.getMessage());
            return;
        }

        if (routes.isEmpty()) {
            showError("Aucune route",
                "Aucune route enregistrée. Désigne d'abord un trajet comme typique d'une route.");
            return;
        }

        TextInputDialog radiusDialog = new TextInputDialog("0.05");
        radiusDialog.setTitle("Rayon de tolérance");
        radiusDialog.setHeaderText("Distance maximale pour considérer qu'un trajet suit une route (en km)");
        radiusDialog.setContentText("Exemple : 0.05 (50m)");
        Optional<String> radiusResult = radiusDialog.showAndWait();
        if (radiusResult.isEmpty()) {
            return;
        }

        try {
            double rayonKm = Double.parseDouble(radiusResult.get());
            Map<String, List<Trajet>> classement = FiltreTrajet.classifierParRoutes(trajets, routes, rayonKm);

            StringBuilder resume = new StringBuilder("Répartition des trajets :\n\n");
            for (Map.Entry<String, List<Trajet>> entree : classement.entrySet()) {
                resume.append(String.format("• %s : %d trajet(s)\n", entree.getKey(), entree.getValue().size()));
            }
            showInfo("Classification par routes", resume.toString());

            List<String> noms = new ArrayList<>(classement.keySet());
            ChoiceDialog<String> choixGroupe = new ChoiceDialog<>(noms.get(0), noms);
            choixGroupe.setTitle("Afficher un groupe");
            choixGroupe.setHeaderText("Quel groupe afficher dans la liste ?");
            choixGroupe.setContentText("Groupe :");
            Optional<String> groupeResult = choixGroupe.showAndWait();
            groupeResult.ifPresent(nomGroupe -> {
                trajetsFiltres = classement.get(nomGroupe);
                afficherTrajetsFiltres();
            });
        } catch (NumberFormatException e) {
            showError("Erreur de format", "Le rayon doit être un nombre.");
        }
    }

    private void saveLastDirectory(File directory) {
        if (directory != null) {
            prefs.put(LAST_DIRECTORY_KEY, directory.getAbsolutePath());
        }
    }

    private File getLastDirectory() {
        String lastDir = prefs.get(LAST_DIRECTORY_KEY, null);
        if (lastDir != null && new File(lastDir).exists()) {
            return new File(lastDir);
        }
        return null;
    }
}