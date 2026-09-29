package org.wvicto.parcours.controller;

import org.wvicto.parcours.model.GpxParser;
import org.wvicto.parcours.model.FiltreTrajet;
import org.wvicto.parcours.model.PerformanceRoute;
import org.wvicto.parcours.model.ProfilTrajet;
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
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
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

    private static final String LAST_DIRECTORY_KEY = "lastDirectory";  // le champ est sauvegardé dans le registre HKEY_CURRENT_USER\Software\JavaSoft\Prefs\org\wvicto\parcours
    private Preferences prefs = Preferences.userRoot().node(this.getClass().getName());

    private final ObservableList<Trajet> trajets = FXCollections.observableArrayList();
    private List<Trajet> trajetsFiltres;

    // Injecté automatiquement par <fx:include fx:id="carte"> dans Parcours.fxml :
    // JavaFX cherche un champ nommé "<fx:id>Controller", ici "carteController".
    @FXML
    private CartePointsController carteController;

    @FXML
    private LineChart<Number, Number> graphiqueAltitude;
    @FXML
    private LineChart<Number, Number> graphiqueVitesse;

    @FXML
    private void initialize() {
        listeTrajets.setItems(trajets);
        listeTrajets.setCellFactory(lv -> new TextFieldListCell<>());

        // Sélectionner un trajet dans la liste met à jour sa mise en évidence sur la carte
        // et son profil sur le graphique. Se déclenche aussi quand la sélection est
        // réinitialisée par un changement de liste (filtrage, tout afficher...).
        listeTrajets.getSelectionModel().selectedItemProperty()
            .addListener((obs, ancien, nouveau) -> {
                afficherTrajetsSurCarte();
                afficherProfilSurGraphique(nouveau);
            });

        // Clic sur un trajet affiché sur la carte -> sélection dans la liste.
        // carteController est déjà injecté ici : fx:include est chargé avant que
        // JavaFX n'appelle initialize() sur ce contrôleur.
        carteController.setOnTrajetClicked(trajet ->
            listeTrajets.getSelectionModel().select(trajet));
    }

    /**
     * Pousse la liste actuellement affichée (filtrée ou non) et le trajet sélectionné
     * vers la carte.
     */
    private void afficherTrajetsSurCarte() {
        List<Trajet> listeAffichee = trajetsFiltres != null ? trajetsFiltres : trajets;
        Trajet selectionne = listeTrajets.getSelectionModel().getSelectedItem();
        carteController.afficherTrajets(listeAffichee, selectionne);
    }

    /**
     * Alimente les deux graphiques superposés (altitude à gauche, vitesse à droite)
     * avec le profil du trajet sélectionné. Les vide si aucun trajet n'est sélectionné.
     */
    private void afficherProfilSurGraphique(Trajet trajet) {
        graphiqueAltitude.getData().clear();
        graphiqueVitesse.getData().clear();
        if (trajet == null) {
            return;
        }

        XYChart.Series<Number, Number> serieAltitude = new XYChart.Series<>();
        serieAltitude.setName("Altitude (m)");
        XYChart.Series<Number, Number> serieVitesse = new XYChart.Series<>();
        serieVitesse.setName("Vitesse (km/h)");

        List<ProfilTrajet.PointProfil> profil = ProfilTrajet.lisserVitesse(
            new ProfilTrajet(trajet).calculer(), 20);

        for (ProfilTrajet.PointProfil point : profil) {
            serieAltitude.getData().add(new XYChart.Data<>(point.distanceKm(), point.altitude()));
            serieVitesse.getData().add(new XYChart.Data<>(point.distanceKm(), point.vitesseKmh()));
        }

        graphiqueAltitude.getData().add(serieAltitude);
        graphiqueVitesse.getData().add(serieVitesse);
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

    /**
     * Démarrage/fin/annulation de la création manuelle d'une route par clics sur la
     * carte : simple délégation, carteController porte toute la logique.
     */
    @FXML
    private void demarrerCreationRoute() {
        carteController.demarrerCreationRoute();
    }

    @FXML
    private void terminerCreationRoute() {
        carteController.terminerCreationRoute();
    }

    @FXML
    private void annulerCreationRoute() {
        carteController.annulerCreationRoute();
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

    /**
     * Calcule le temps de parcours d'une route pour chaque trajet correspondant, et
     * ouvre une fenêtre affichant leur évolution dans le temps (nuage de points).
     */
    @FXML
    private void analyserRoute() {
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
                "Aucune route enregistrée. Crée-en une (trajet typique, ou clics sur la carte) avant d'analyser.");
            return;
        }

        ChoiceDialog<Route> choixRoute = new ChoiceDialog<>(routes.get(0), routes);
        choixRoute.setTitle("Analyser une route");
        choixRoute.setHeaderText("Quelle route analyser ?");
        choixRoute.setContentText("Route :");
        Optional<Route> routeResult = choixRoute.showAndWait();
        if (routeResult.isEmpty()) {
            return;
        }

        TextInputDialog radiusDialog = new TextInputDialog("0.05");
        radiusDialog.setTitle("Rayon de tolérance");
        radiusDialog.setHeaderText("Distance maximale pour considérer qu'un trajet suit cette route (en km)");
        radiusDialog.setContentText("Exemple : 0.05 (50m)");
        Optional<String> radiusResult = radiusDialog.showAndWait();
        if (radiusResult.isEmpty()) {
            return;
        }

        try {
            double rayonKm = Double.parseDouble(radiusResult.get());
            Route route = routeResult.get();
            List<PerformanceRoute.Performance> performances = PerformanceRoute.calculer(trajets, route, rayonKm);

            if (performances.isEmpty()) {
                showInfo("Aucun résultat",
                    "Aucun trajet ne correspond à cette route (ou aucun horodatage exploitable).");
                return;
            }

            ouvrirFenetrePerformance(route, performances);
        } catch (NumberFormatException e) {
            showError("Erreur de format", "Le rayon doit être un nombre.");
        }
    }

    private void ouvrirFenetrePerformance(Route route, List<PerformanceRoute.Performance> performances) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/wvicto/parcours/view/PerformanceRoute.fxml"));
            Parent root = loader.load();
            PerformanceRouteController controller = loader.getController();
            controller.afficher(route, performances);

            Stage stage = new Stage();
            stage.setTitle("Performances - " + route.getNom());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(listeTrajets.getScene().getWindow());
            stage.setScene(new Scene(root, 700, 500));
            stage.showAndWait();
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir la fenêtre de performance : " + e.getMessage());
        }
    }

    /**
     * Laisse choisir une ou plusieurs routes enregistrées, et les affiche sur la carte
     * (tracé + marqueur sur chaque waypoint, pour voir concrètement leur simplification).
     */
    @FXML
    private void afficherRoutesSurCarte() {
        File fichierRoutes = new File(Constants.FICHIER_ROUTES);
        List<Route> routesDisponibles;
        try {
            routesDisponibles = RoutePersistenceService.charger(fichierRoutes);
        } catch (IOException e) {
            showError("Erreur", "Impossible de charger les routes : " + e.getMessage());
            return;
        }

        if (routesDisponibles.isEmpty()) {
            showError("Aucune route", "Aucune route enregistrée.");
            return;
        }

        Dialog<List<Route>> dialogue = new Dialog<>();
        dialogue.setTitle("Afficher des routes");
        dialogue.setHeaderText("Sélectionne une ou plusieurs routes à afficher sur la carte");

        ListView<Route> liste = new ListView<>(FXCollections.observableArrayList(routesDisponibles));
        liste.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        liste.setPrefHeight(200);
        dialogue.getDialogPane().setContent(liste);
        dialogue.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogue.setResultConverter(bouton ->
            bouton == ButtonType.OK ? new ArrayList<>(liste.getSelectionModel().getSelectedItems()) : null);

        Optional<List<Route>> resultat = dialogue.showAndWait();
        resultat.ifPresent(routesChoisies -> carteController.afficherRoutes(routesChoisies));
    }

    /**
     * Ouvre la vue d'ensemble des trajets classés par route (avec un groupe "Aucune
     * correspondance"). Si l'utilisateur y sélectionne un trajet, il devient la
     * sélection dans la liste principale une fois la fenêtre fermée.
     */
    @FXML
    private void vueEnsembleRoutes() {
        File fichierRoutes = new File(Constants.FICHIER_ROUTES);
        List<Route> routes;
        try {
            routes = RoutePersistenceService.charger(fichierRoutes);
        } catch (IOException e) {
            showError("Erreur", "Impossible de charger les routes : " + e.getMessage());
            return;
        }
 
        if (routes.isEmpty()) {
            showError("Aucune route", "Aucune route enregistrée pour l'instant.");
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
 
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/wvicto/parcours/view/VueEnsembleRoutes.fxml"));
            Parent root = loader.load();
            VueEnsembleRoutesController controller = loader.getController();
 
            Stage stage = new Stage();
            stage.setTitle("Vue d'ensemble des routes");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(listeTrajets.getScene().getWindow());
            controller.setStage(stage);
            controller.initialiser(trajets, routes, rayonKm);
 
            stage.setScene(new Scene(root, 500, 600));
            stage.showAndWait();
 
            Trajet choisi = controller.getTrajetChoisi();
            if (choisi != null) {
                listeTrajets.getSelectionModel().select(choisi);
            }
        } catch (NumberFormatException e) {
            showError("Erreur de format", "Le rayon doit être un nombre.");
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir la vue d'ensemble : " + e.getMessage());
        }
    }
    
    /**
     * Ouvre la fenêtre de gestion des routes (liste + suppression).
     */
    @FXML
    private void gererRoutes() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/wvicto/parcours/view/GestionRoutes.fxml"));
            Parent root = loader.load();
            GestionRoutesController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Gérer les routes");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(listeTrajets.getScene().getWindow());
            controller.setStage(stage);

            stage.setScene(new Scene(root, 400, 400));
            stage.showAndWait();
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir la gestion des routes : " + e.getMessage());
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
        return null;
    }
}