package org.wvicto.parcours.service;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;

import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.layout.Pane;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
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

        // Utilise le fournisseur passé en paramètre
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
        // Zoom avec la molette (comportement intuitif)
        mapViewer.addMouseWheelListener(e -> {
            // 🔹 1. Récupère la position de la souris et le point géographique correspondant
            Point mousePoint = e.getPoint();
            GeoPosition mouseGeo = mapViewer.convertPointToGeoPosition(mousePoint);

            // 🔹 2. Récupère le centre et le zoom actuels
            GeoPosition oldCenter = mapViewer.getAddressLocation();
            int oldZoom = mapViewer.getZoom();

            // 🔹 3. Calcule le nouveau niveau de zoom (limité entre 1 et 19)
            //       et le rapport d'homothétie (progression exponentielle)
            int zoomChange = e.getWheelRotation();
            int newZoom = Math.max(1, Math.min(19, oldZoom + zoomChange));
            double zoomRatio = Math.pow(2, newZoom - oldZoom);

            // 🔹 4. Calcule la différence entre le point sous la souris et le centre actuel
            double latDiff = mouseGeo.getLatitude() - oldCenter.getLatitude();
            double lonDiff = mouseGeo.getLongitude() - oldCenter.getLongitude();

            // 🔹 5. Applique le rapport d'homothétie à cette différence, et en déduit le nouveau centre
            double newLat = mouseGeo.getLatitude() - latDiff * zoomRatio;
            double newLon = mouseGeo.getLongitude() - lonDiff * zoomRatio;

            // 🔹 6. Enregistre le nouveau zoom et recentre la carte sur le nouveau centre
            mapViewer.setZoom(newZoom);
            mapViewer.setAddressLocation(new GeoPosition(newLat, newLon));            
            
        });

        // Classe interne pour gérer le glisser-déposer
        class DragHandler implements MouseListener, MouseMotionListener {
            private Point dragStart;               // Position du clic en pixels
            private GeoPosition initialGeoPosition; // Point géographique sous la souris au clic

            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                // 🔹 Enregistre le point géographique SOUS la souris au moment du clic
                initialGeoPosition = mapViewer.convertPointToGeoPosition(dragStart);
                mapViewer.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null || initialGeoPosition == null) return;

                Point currentPoint = e.getPoint();
                // 🔹 1. Récupère la position géographique ACTUELLE sous la souris
                GeoPosition currentGeo = mapViewer.convertPointToGeoPosition(currentPoint);

                // 🔹 2. Calcule la différence entre la position initiale et actuelle
                double latDiff = initialGeoPosition.getLatitude() - currentGeo.getLatitude();
                double lonDiff = initialGeoPosition.getLongitude() - currentGeo.getLongitude();

                // 🔹 3. Déplace le centre de la carte pour compenser EXACTEMENT
                GeoPosition currentCenter = mapViewer.getAddressLocation();
                double newLat = currentCenter.getLatitude() + latDiff;
                double newLon = currentCenter.getLongitude() + lonDiff;

                mapViewer.setAddressLocation(new GeoPosition(newLat, newLon));
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                mapViewer.setCursor(java.awt.Cursor.getDefaultCursor());
                dragStart = null;
                initialGeoPosition = null;
            }

            @Override public void mouseClicked(MouseEvent e) {}
            @Override public void mouseEntered(MouseEvent e) {}
            @Override public void mouseExited(MouseEvent e) {}
            @Override public void mouseMoved(MouseEvent e) {}
        }
        
        // Crée et ajoute le handler
        DragHandler dragHandler = new DragHandler();
        mapViewer.addMouseListener(dragHandler);
        mapViewer.addMouseMotionListener(dragHandler);
    }

    /**
     * Définit un callback pour les clics sur la carte.
     * @param callback Fonction appelée quand on clique sur la carte (reçoit les coordonnées).
     */
    public void setOnMapClicked(Consumer<GeoPosition> callback) {
        mapViewer.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
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