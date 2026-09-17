package org.wvicto.parcours.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.jxmapviewer.viewer.GeoPosition;
import org.wvicto.parcours.service.CarteService;
import org.wvicto.parcours.service.OpenStreetMapProvider;

public class CartePointsController {
    @FXML
    private Pane mapContainer;

    @FXML
    private Label coordsLabel;

    private Stage stage;
    private CarteService carteService;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void initialize() {
        // ✅ Crée le service de carte avec le fournisseur OpenStreetMap
        carteService = new CarteService(
            mapContainer,
            new OpenStreetMapProvider()  // ✅ Passe le fournisseur ici
        );

        // ✅ Configure le callback pour les clics
        carteService.setOnMapClicked(geo -> {
            coordsLabel.setText(String.format(
                "Latitude: %.6f, Longitude: %.6f",
                geo.getLatitude(),
                geo.getLongitude()
            ));
        });
    }

    @FXML
    private void fermer() {
        stage.close();
    }
}