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
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import org.jxmapviewer.viewer.GeoPosition;
import org.wvicto.parcours.model.PointGpx;
import org.wvicto.parcours.model.PointRemarquable;
import org.wvicto.parcours.model.Route;
import org.wvicto.parcours.model.Trajet;
import org.wvicto.parcours.service.CarteService;
import org.wvicto.parcours.service.OpenStreetMapProvider;
import org.wvicto.parcours.service.PointRemarquableService;
import org.wvicto.parcours.service.RoutePersistenceService;
import org.wvicto.parcours.util.Constants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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

    // Mode "création de route" : capture les clics gauche successifs comme points
    // ordonnés, au lieu de leur comportement normal (affichage des coordonnées).
    private boolean modeCreationRoute = false;
    private final List<PointGpx> pointsRouteEnCours = new ArrayList<>();

    @FXML
    private void initialize() {
        // ✅ Crée le service de carte avec le fournisseur OpenStreetMap
        carteService = new CarteService(
            mapContainer,
            new OpenStreetMapProvider()  // ✅ Passe le fournisseur ici
        );

        // ✅ Configure le callback pour les clics (affiche les coordonnées, ou capture
        // le point pour une route en cours de création selon le mode actif)
        carteService.setOnMapClicked(geo -> {
            if (modeCreationRoute) {
                pointsRouteEnCours.add(PointGpx.sansAltitude(geo.getLatitude(), geo.getLongitude()));
                rafraichirApercuRoute();
                coordsLabel.setText(pointsRouteEnCours.size() + " point(s) sélectionné(s) pour la route. "
                    + "Menu Routes > Terminer pour valider.");
            } else {
                coordsLabel.setText(String.format(
                    "Latitude: %.6f, Longitude: %.6f",
                    geo.getLatitude(),
                    geo.getLongitude()
                ));
            }
        });

        // ✅ Configure le callback pour les clics-droit (créer un point remarquable)
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
            fichierPoints.getParentFile().mkdirs(); // crée ~/.parcours si besoin
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

    // ==================== CRÉATION MANUELLE D'UNE ROUTE ====================

    /**
     * Active le mode création de route : les clics gauche suivants sur la carte
     * ajoutent des points, dans l'ordre, à la route en cours de construction.
     */
    public void demarrerCreationRoute() {
        modeCreationRoute = true;
        pointsRouteEnCours.clear();
        rafraichirApercuRoute();
        coordsLabel.setText("Mode création de route activé : cliquez les points dans l'ordre.");
    }

    /**
     * Abandonne la route en cours de création sans la sauvegarder, et revient
     * à l'affichage normal des points remarquables.
     */
    public void annulerCreationRoute() {
        modeCreationRoute = false;
        pointsRouteEnCours.clear();
        carteService.afficherPointsRemarquables(pointsRemarquables);
        coordsLabel.setText("Création de route annulée.");
    }

    /**
     * Termine la route en cours : demande un nom, la construit et la sauvegarde
     * (réutilise Route et RoutePersistenceService, comme pour une route générée
     * depuis un trajet). Reste en mode création si annulé, pour pouvoir continuer.
     */
    public void terminerCreationRoute() {
        if (pointsRouteEnCours.size() < 2) {
            showErreur("Route incomplète", "Il faut au moins 2 points pour créer une route.");
            return;
        }

        TextInputDialog dialogue = new TextInputDialog();
        dialogue.setTitle("Nouvelle route");
        dialogue.setHeaderText("Nom de la route (" + pointsRouteEnCours.size() + " points sélectionnés)");
        dialogue.setContentText("Nom :");
        Optional<String> nomResult = dialogue.showAndWait();
        if (nomResult.isEmpty() || nomResult.get().isBlank()) {
            return; // annulé : on reste en mode création pour ajuster/continuer
        }

        try {
            Route route = new Route(nomResult.get().trim(), new ArrayList<>(pointsRouteEnCours));

            File fichierRoutes = new File(Constants.FICHIER_ROUTES);
            fichierRoutes.getParentFile().mkdirs();
            List<Route> routes = RoutePersistenceService.charger(fichierRoutes);
            routes.add(route);
            RoutePersistenceService.sauvegarder(routes, fichierRoutes);

            modeCreationRoute = false;
            pointsRouteEnCours.clear();
            carteService.afficherPointsRemarquables(pointsRemarquables);
            coordsLabel.setText("Route '" + route.getNom() + "' créée avec succès.");
        } catch (IOException e) {
            showErreur("Erreur", "Impossible d'enregistrer la route : " + e.getMessage());
        }
    }

    /**
     * Affiche les points déjà cliqués comme aperçu, en réutilisant l'affichage des
     * points remarquables (numérotés, pour voir l'ordre) plutôt que d'ajouter un
     * mécanisme de dessin séparé pour ce cas temporaire.
     */
    private void rafraichirApercuRoute() {
        List<PointRemarquable> apercu = new ArrayList<>();
        for (int i = 0; i < pointsRouteEnCours.size(); i++) {
            PointGpx point = pointsRouteEnCours.get(i);
            apercu.add(new PointRemarquable(
                String.valueOf(i + 1), "Route en cours", point.getLatitude(), point.getLongitude()));
        }
        carteService.afficherPointsRemarquables(apercu);
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
     * Affiche une ou plusieurs routes sur la carte. Simple délégation, comme
     * afficherTrajets() et afficherPointsRemarquables().
     */
    public void afficherRoutes(List<Route> routes) {
        carteService.afficherRoutes(routes);
    }

    /**
     * Permet à l'appelant (le futur contrôleur principal) d'être notifié quand
     * l'utilisateur clique sur le tracé d'un trajet affiché sur la carte.
     */
    public void setOnTrajetClicked(Consumer<Trajet> callback) {
        carteService.setOnTrajetClicked(callback);
    }
    
    /**
     * Affiche un point temporaire sur la carte (pour indiquer la position correspondant
     * à une abscisse du graphique).
     */
    public void afficherPointSurCarte(PointGpx point) {
        carteService.afficherPointTemporaire(point);
    }


    /**
     * Efface le point temporaire affiché sur la carte.
     */
    public void effacerPointTemporaire() {
        carteService.effacerPointTemporaire();
    }
}