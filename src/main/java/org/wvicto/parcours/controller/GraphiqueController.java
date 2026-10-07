package org.wvicto.parcours.controller;

import org.wvicto.parcours.model.PointGpx;
import org.wvicto.parcours.model.ProfilTrajet;
import org.wvicto.parcours.model.Trajet;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Line;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

public class GraphiqueController {

    @FXML
    private LineChart<Number, Number> graphiqueAltitude;

    @FXML
    private LineChart<Number, Number> graphiqueVitesse;

    @FXML
    private Line ligneVerticale;

    @FXML
    private Line ligneHorizontale;

    @FXML
    private StackPane container;

    private Consumer<PointGpx> onPointSelectedCallback;
    private Trajet trajetActuel;

    @FXML
    private void initialize() {
    	Axis<Number> axeYAltitude = graphiqueAltitude.getYAxis();
    	Axis<Number> axeYVitesse = graphiqueVitesse.getYAxis();

    	// Altitude : on laisse à droite la place de l'axe de la vitesse
    	graphiqueAltitude.paddingProperty().bind(Bindings.createObjectBinding(
    	        () -> new Insets(0, axeYVitesse.getWidth(), 0, 0),
    	        axeYVitesse.widthProperty()));

    	// Vitesse : on laisse à gauche la place de l'axe de l'altitude
    	graphiqueVitesse.paddingProperty().bind(Bindings.createObjectBinding(
    	        () -> new Insets(0, 0, 0, axeYAltitude.getWidth()),
    	        axeYAltitude.widthProperty()));
    	
        container.setOnMouseMoved(event -> {
            if (graphiqueAltitude.getData().isEmpty()) {
                return;
            }

            double mouseX = event.getX();
            double mouseY = event.getY();

            updateCrosshair(mouseX, mouseY);
            notifyPointSelected(mouseX);
        });

        container.setOnMouseExited(event -> hideCrosshair());
    }

    private void updateCrosshair(double mouseX, double mouseY) {
        ligneVerticale.setStartX(mouseX);
        ligneVerticale.setStartY(0);
        ligneVerticale.setEndX(mouseX);
        ligneVerticale.setEndY(container.getHeight());
        ligneVerticale.setVisible(true);

        ligneHorizontale.setStartX(0);
        ligneHorizontale.setStartY(mouseY);
        ligneHorizontale.setEndX(container.getWidth());
        ligneHorizontale.setEndY(mouseY);
        ligneHorizontale.setVisible(true);
    }

    private void notifyPointSelected(double mouseX) {
        if (onPointSelectedCallback == null || trajetActuel == null) {
            return;
        }

        NumberAxis xAxis = (NumberAxis) graphiqueAltitude.getXAxis();
        // ✅ CORRECTION : Conversion correcte des coordonnées
        Point2D p = xAxis.sceneToLocal(container.localToScene(mouseX, 0));
        double xValue = xAxis.getValueForDisplay(p.getX()).doubleValue();

        PointGpx point = trouverPointParDistance(trajetActuel, xValue);
        onPointSelectedCallback.accept(point);
    }

    private void hideCrosshair() {
        ligneVerticale.setVisible(false);
        ligneHorizontale.setVisible(false);
        if (onPointSelectedCallback != null) {
            onPointSelectedCallback.accept(null);
        }
    }

    public void afficherProfil(Trajet trajet, boolean modePente) {
        this.trajetActuel = trajet;

        graphiqueAltitude.getData().clear();
        graphiqueVitesse.getData().clear();
        graphiqueVitesse.getYAxis().setLabel(modePente ? "Pente (%)" : "Vitesse (km/h)");

        if (trajet == null) {
            return;
        }

        List<ProfilTrajet.PointProfil> profilBrut = new ProfilTrajet(trajet).calculer();
        if (profilBrut.isEmpty()) {
            return;
        }
        List<ProfilTrajet.PointProfil> profil = modePente
            ? ProfilTrajet.lisserPente(profilBrut, 20)
            : ProfilTrajet.lisserVitesse(profilBrut, 20);

        ajouterCourbe(graphiqueAltitude, profil, ProfilTrajet.PointProfil::altitude);
        ajouterCourbe(graphiqueVitesse, profil,
            modePente ? ProfilTrajet.PointProfil::pentePourcent : ProfilTrajet.PointProfil::vitesseKmh);
    }

    /**
     * Trace une courbe en une série par tronçon : une valeur NaN (inconnue) termine le tronçon
     * en cours, ce qui laisse un trou dans la courbe au lieu de relier les points voisins.
     */
    private void ajouterCourbe(LineChart<Number, Number> graphique,
                               List<ProfilTrajet.PointProfil> profil,
                               ToDoubleFunction<ProfilTrajet.PointProfil> valeur) {
        XYChart.Series<Number, Number> troncon = new XYChart.Series<>();
        for (ProfilTrajet.PointProfil point : profil) {
            double y = valeur.applyAsDouble(point);
            if (Double.isNaN(y)) {
                if (!troncon.getData().isEmpty()) {
                    graphique.getData().add(troncon);
                    troncon = new XYChart.Series<>();
                }
            } else {
                troncon.getData().add(new XYChart.Data<>(point.distanceKm(), y));
            }
        }
        // Dernier tronçon. Si le graphique est resté vide (aucune valeur connue), on ajoute
        // quand même une série vide : sinon le repère de survol serait désactivé (voir
        // initialize), alors que la carte doit continuer à afficher le point survolé.
        if (!troncon.getData().isEmpty() || graphique.getData().isEmpty()) {
            graphique.getData().add(troncon);
        }
    }
    public void changerMode(boolean modePente) {
        if (trajetActuel != null) {
            afficherProfil(trajetActuel, modePente);
        }
    }

    public void setOnPointSelected(Consumer<PointGpx> callback) {
        this.onPointSelectedCallback = callback;
    }

    private PointGpx trouverPointParDistance(Trajet trajet, double distanceKm) {
        List<ProfilTrajet.PointProfil> profil = new ProfilTrajet(trajet).calculer();
        if (profil.isEmpty()) {
            return null;
        }

        for (int i = 0; i < profil.size() - 1; i++) {
            double distDebut = profil.get(i).distanceKm();
            double distFin = profil.get(i + 1).distanceKm();

            if (distanceKm >= distDebut && distanceKm <= distFin) {
                PointGpx p1 = trajet.getPoints().get(i);
                if (distFin == distDebut) {
                    return p1;
                }
                double ratio = (distanceKm - distDebut) / (distFin - distDebut);
                return p1.interpoleVers(trajet.getPoints().get(i + 1), ratio);
            }
        }

        return distanceKm <= profil.get(0).distanceKm()
            ? trajet.getPoints().get(0)
            : trajet.getPoints().get(trajet.getPoints().size() - 1);
    }
}