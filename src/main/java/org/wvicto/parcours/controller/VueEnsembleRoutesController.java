package org.wvicto.parcours.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.wvicto.parcours.model.FiltreTrajet;
import org.wvicto.parcours.model.Route;
import org.wvicto.parcours.model.Trajet;
import org.wvicto.parcours.service.RoutePersistenceService;
import org.wvicto.parcours.service.RouteService;
import org.wvicto.parcours.util.Constants;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vue d'ensemble des trajets classés par route, avec un groupe "Aucune correspondance"
 * pour repérer les trajets non associés — et, depuis ce groupe, désigner directement
 * l'un d'eux comme route typique si plusieurs se ressemblent.
 */
public class VueEnsembleRoutesController {
    private static final String GROUPE_SANS_ROUTE = "Aucune correspondance";

    @FXML
    private Accordion accordion;

    private Stage stage;
    private List<Trajet> trajets;
    private List<Route> routes;
    private double rayonKm;
    private Trajet trajetChoisi;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Fournit les données de départ et affiche le classement initial.
     * À appeler juste après le chargement du FXML, avant d'afficher la fenêtre.
     */
    public void initialiser(List<Trajet> trajets, List<Route> routes, double rayonKm) {
        this.trajets = trajets;
        this.routes = routes;
        this.rayonKm = rayonKm;
        rafraichir();
    }

    /**
     * Le trajet choisi via "Sélectionner ce trajet", ou null si la fenêtre a été
     * fermée sans choix (à consulter par l'appelant après stage.showAndWait()).
     */
    public Trajet getTrajetChoisi() {
        return trajetChoisi;
    }

    private void rafraichir() {
        accordion.getPanes().clear();
        Map<String, List<Trajet>> classement = FiltreTrajet.classifierParRoutes(trajets, routes, rayonKm);

        TitledPane panneauSansRoute = null;
        for (Map.Entry<String, List<Trajet>> entree : classement.entrySet()) {
            boolean sansRoute = entree.getKey().equals(GROUPE_SANS_ROUTE);
            TitledPane panneau = creerPanneauGroupe(entree.getKey(), entree.getValue(), sansRoute);
            accordion.getPanes().add(panneau);
            if (sansRoute) {
                panneauSansRoute = panneau;
            }
        }
        if (panneauSansRoute != null) {
            accordion.setExpandedPane(panneauSansRoute);
        }
    }

    private TitledPane creerPanneauGroupe(String nomGroupe, List<Trajet> trajetsGroupe, boolean sansRoute) {
        ListView<Trajet> liste = new ListView<>(FXCollections.observableArrayList(trajetsGroupe));
        liste.setPrefHeight(150);

        Button boutonSelectionner = new Button("Sélectionner ce trajet");
        boutonSelectionner.setOnAction(e -> {
            Trajet selection = liste.getSelectionModel().getSelectedItem();
            if (selection != null) {
                trajetChoisi = selection;
                stage.close();
            }
        });

        VBox contenu = new VBox(8, liste, boutonSelectionner);

        if (sansRoute) {
            Button boutonDesigner = new Button("Désigner comme route typique...");
            boutonDesigner.setOnAction(e -> designerCommeRoute(liste.getSelectionModel().getSelectedItem()));
            contenu.getChildren().add(boutonDesigner);
        }

        return new TitledPane(nomGroupe + " (" + trajetsGroupe.size() + ")", contenu);
    }

    /**
     * Même logique que ParcoursController.genererRouteDepuisTrajet(), mais suivie
     * d'un rafraîchissement immédiat de la vue plutôt que d'une simple confirmation :
     * on veut voir tout de suite quels autres trajets rejoignent le nouveau groupe.
     */
    private void designerCommeRoute(Trajet trajet) {
        if (trajet == null) {
            showErreur("Aucune sélection", "Sélectionne d'abord un trajet dans ce groupe.");
            return;
        }

        TextInputDialog nomDialog = new TextInputDialog(trajet.getNom());
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
            Route route = RouteService.genererDepuisTrajet(trajet, nomResult.get().trim(), toleranceKm);

            File fichierRoutes = new File(Constants.FICHIER_ROUTES);
            fichierRoutes.getParentFile().mkdirs();
            List<Route> routesActuelles = RoutePersistenceService.charger(fichierRoutes);
            routesActuelles.add(route);
            RoutePersistenceService.sauvegarder(routesActuelles, fichierRoutes);

            this.routes = routesActuelles;
            rafraichir();
        } catch (NumberFormatException e) {
            showErreur("Erreur de format", "La tolérance doit être un nombre.");
        } catch (IOException e) {
            showErreur("Erreur", "Impossible d'enregistrer la route : " + e.getMessage());
        }
    }

    private void showErreur(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
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