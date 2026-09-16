package org.wvicto.parcours.view;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.embed.swing.SwingNode;
import javafx.stage.Stage;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import javax.swing.SwingUtilities;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class CartePointsController {
    @FXML
    private Pane mapContainer;  // Conteneur pour SwingNode

    @FXML
    private Label coordsLabel;

    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void initialize() {
        SwingNode swingNode = new SwingNode();

        SwingUtilities.invokeLater(() -> {
            // 1. Crée le JXMapViewer (composant Swing)
            JXMapViewer mapViewer = new JXMapViewer();

            // 2. Configure OpenStreetMap (comme dans le code de Copilot)
            TileFactoryInfo info = new TileFactoryInfo(
                0, 19,               // Niveaux de zoom min/max
                256, 256,            // Taille des tuiles
                true, true,         // Inversion Y, axes X/Y
                "https://tile.openstreetmap.org",
                "x", "y", "z"
            ) {
                @Override
                public String getTileUrl(int x, int y, int zoom) {
                    return String.format("https://tile.openstreetmap.org/%d/%d/%d.png", zoom, x, y);
                }
            };
            DefaultTileFactory tileFactory = new DefaultTileFactory(info);
            mapViewer.setTileFactory(tileFactory);

            // 3. Centre sur Paris
            GeoPosition paris = new GeoPosition(48.8566, 2.3522);
            mapViewer.setZoom(12);
            mapViewer.setAddressLocation(paris);

            // 4. ✅ Gestion du clic (comme dans le code de Copilot)
            mapViewer.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    GeoPosition geo = mapViewer.convertPointToGeoPosition(e.getPoint());
                    coordsLabel.setText(String.format(
                        "Latitude: %.6f, Longitude: %.6f",
                        geo.getLatitude(),
                        geo.getLongitude()
                    ));
                }
            });

            swingNode.setContent(mapViewer);
        });

        mapContainer.getChildren().add(swingNode);
    }

    @FXML
    private void fermer() {
        stage.close();
    }
}