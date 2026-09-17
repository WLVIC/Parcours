package org.wvicto.parcours.service;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;

import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.layout.Pane;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.util.function.Consumer;

/**
 * Service dédié à la gestion de la carte (JXMapViewer).
 * Utilise le pattern Strategy pour changer de fournisseur de tuiles.
 */
public class CarteService {
    private final JXMapViewer mapViewer;
    private final SwingNode swingNode;
    private GeoPosition startPosition;
    private Point dragStart;


    /**
     * Crée une nouvelle instance de CarteService.
     * @param mapContainer Le conteneur JavaFX où la carte sera affichée.
     * @param provider Le fournisseur de tuiles à utiliser (ex: OpenStreetMapProvider).
     */
    public CarteService(Pane mapContainer, TileFactoryProvider provider) {
        this.swingNode = new SwingNode();
        this.mapViewer = new JXMapViewer();

        // ✅ Utilise le fournisseur passé en paramètre
        this.mapViewer.setTileFactory(new DefaultTileFactory(provider.getTileFactoryInfo()));

        // Configuration de base
        this.mapViewer.setAddressLocation(new GeoPosition(48.8566, 2.3522)); // Paris
        this.mapViewer.setZoom(12);
        this.mapViewer.setPreferredSize(new java.awt.Dimension(800, 600));

        // Ajoute les écouteurs
        initMouseListeners();

        // Ajoute au conteneur JavaFX
        SwingUtilities.invokeLater(() -> swingNode.setContent(mapViewer));
        mapContainer.getChildren().add(swingNode);
    }

    /**
     * Initialise les écouteurs de souris (zoom, déplacement, clic).
     */
    private void initMouseListeners() {
        // Zoom avec la molette
        mapViewer.addMouseWheelListener(e -> {
            int zoomChange = -e.getWheelRotation(); // Inverse pour que la molette "vers l'avant" zoome
            int newZoom = mapViewer.getZoom() + zoomChange;
            mapViewer.setZoom(Math.max(0, Math.min(19, newZoom)));
        });

        // Déplacement par glisser-déposer
        mapViewer.addMouseListener(new java.awt.event.MouseInputAdapter() {
            private Point dragStart;
            private GeoPosition startPosition;

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                dragStart = e.getPoint();
                startPosition = mapViewer.getAddressLocation();
                mapViewer.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                mapViewer.setCursor(java.awt.Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                Point dragEnd = e.getPoint();
                int dx = dragStart.x - dragEnd.x;
                int dy = dragStart.y - dragEnd.y;

                double viewportHeightDeg = mapViewer.getViewportBounds().getHeight();
                double viewportWidthDeg = mapViewer.getViewportBounds().getWidth();
                double latScale = viewportHeightDeg / mapViewer.getHeight();
                double lonScale = viewportWidthDeg / mapViewer.getWidth();

                double newLat = startPosition.getLatitude() + (dy * latScale);
                double newLon = startPosition.getLongitude() + (dx * lonScale);

                mapViewer.setAddressLocation(new GeoPosition(newLat, newLon));
                dragStart = dragEnd;
            }
        });
    }

    /**
     * Définit un callback pour les clics sur la carte.
     * @param callback Fonction appelée quand on clique sur la carte (reçoit les coordonnées).
     */
    public void setOnMapClicked(Consumer<GeoPosition> callback) {
        mapViewer.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                GeoPosition geo = mapViewer.convertPointToGeoPosition(e.getPoint());

                Platform.runLater(() -> callback.accept(geo));
            }
        });
    }

    /**
     * Centre la carte sur une position.
     * @param position La position géographique (latitude, longitude).
     */
    public void setCenter(GeoPosition position) {
        mapViewer.setAddressLocation(position);
    }

    /**
     * Définit le niveau de zoom.
     * @param zoom Le niveau de zoom (0 = monde, 19 = très zoomé).
     */
    public void setZoom(int zoom) {
        mapViewer.setZoom(Math.max(0, Math.min(19, zoom)));
    }
}