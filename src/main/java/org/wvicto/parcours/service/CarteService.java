package org.wvicto.parcours.service;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.CompoundPainter;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.WaypointPainter;
import org.jxmapviewer.viewer.WaypointRenderer;
import org.wvicto.parcours.model.PointGpx;
import org.wvicto.parcours.model.PointRemarquable;
import org.wvicto.parcours.model.Trajet;

import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.layout.Pane;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    // État de ce qui est actuellement affiché, pour pouvoir recomposer les calques
    // (trajets + points remarquables) à chaque changement de l'un ou de l'autre.
    private List<PointRemarquable> pointsAffiches = new ArrayList<>();
    private List<Trajet> trajetsAffiches = new ArrayList<>();
    private Trajet trajetSelectionne;

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

        // Un Pane ne redimensionne pas ses enfants automatiquement : il faut répercuter
        // manuellement les changements de taille de mapContainer sur le SwingNode et le
        // composant Swing qu'il héberge (utile depuis le passage à un panneau intégré,
        // élastique, dans un SplitPane, plutôt qu'une fenêtre modale à taille fixe).
        mapContainer.widthProperty().addListener((obs, ancienne, nouvelle) ->
            ajusterTailleCarte(nouvelle.doubleValue(), mapContainer.getHeight()));
        mapContainer.heightProperty().addListener((obs, ancienne, nouvelle) ->
            ajusterTailleCarte(mapContainer.getWidth(), nouvelle.doubleValue()));
    }

    /**
     * Répercute une nouvelle taille sur le SwingNode (côté JavaFX) et sur le JXMapViewer
     * qu'il héberge (côté Swing, donc sur l'EDT via SwingUtilities.invokeLater).
     */
    private void ajusterTailleCarte(double largeur, double hauteur) {
        if (largeur <= 0 || hauteur <= 0) {
            return; // taille pas encore connue (avant le premier passage de layout)
        }
        swingNode.resize(largeur, hauteur);
        SwingUtilities.invokeLater(() -> {
            mapViewer.setSize((int) largeur, (int) hauteur);
            mapViewer.revalidate();
            mapViewer.repaint();
        });
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
     * Définit un callback pour les clics-DROIT sur la carte (ex: ajouter un point remarquable).
     * @param callback Fonction appelée avec les coordonnées du clic-droit.
     */
    public void setOnMapRightClicked(Consumer<GeoPosition> callback) {
        mapViewer.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    GeoPosition geo = mapViewer.convertPointToGeoPosition(e.getPoint());
                    Platform.runLater(() -> callback.accept(geo));
                }
            }
        });
    }

    /**
     * Définit un callback appelé quand l'utilisateur clique sur le tracé d'un trajet
     * actuellement affiché (tolérance de quelques pixels autour de la ligne). N'est
     * déclenché que si le clic touche effectivement un trajet.
     */
    public void setOnTrajetClicked(Consumer<Trajet> callback) {
        mapViewer.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    Trajet trajet = trouverTrajetAuClic(e.getPoint());
                    if (trajet != null) {
                        Platform.runLater(() -> callback.accept(trajet));
                    }
                }
            }
        });
    }

    private static final double TOLERANCE_CLIC_PIXELS = 6.0;

    /**
     * Cherche, parmi les trajets actuellement affichés, celui dont le tracé passe à moins
     * de TOLERANCE_CLIC_PIXELS pixels du point cliqué (en coordonnées écran). Renvoie null
     * si aucun trajet ne correspond.
     */
    private Trajet trouverTrajetAuClic(Point clicPixel) {
        Rectangle rect = mapViewer.getViewportBounds();

        for (Trajet trajet : trajetsAffiches) {
            List<PointGpx> points = trajet.getPoints();
            for (int i = 0; i < points.size() - 1; i++) {
                Point2D p1 = mapViewer.getTileFactory().geoToPixel(
                    new GeoPosition(points.get(i).getLatitude(), points.get(i).getLongitude()), mapViewer.getZoom());
                Point2D p2 = mapViewer.getTileFactory().geoToPixel(
                    new GeoPosition(points.get(i + 1).getLatitude(), points.get(i + 1).getLongitude()), mapViewer.getZoom());

                // geoToPixel renvoie des coordonnées "monde" ; on les ramène en coordonnées
                // écran (comme e.getPoint()) en soustrayant l'origine du viewport actuel.
                double x1 = p1.getX() - rect.x;
                double y1 = p1.getY() - rect.y;
                double x2 = p2.getX() - rect.x;
                double y2 = p2.getY() - rect.y;

                if (distanceSegmentPixels(clicPixel.getX(), clicPixel.getY(), x1, y1, x2, y2) <= TOLERANCE_CLIC_PIXELS) {
                    return trajet;
                }
            }
        }
        return null;
    }

    /**
     * Distance (en pixels) entre le point (px,py) et le segment [(ax,ay), (bx,by)].
     * Même principe que RouteService.distancePointSegment, mais en pixels écran plutôt
     * qu'en coordonnées géographiques : donne une tolérance de clic constante quel que
     * soit le niveau de zoom.
     */
    private static double distanceSegmentPixels(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        if (dx == 0 && dy == 0) {
            return Math.hypot(px - ax, py - ay);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double projX = ax + t * dx;
        double projY = ay + t * dy;
        return Math.hypot(px - projX, py - projY);
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

    /**
     * Affiche une liste de points remarquables comme marqueurs sur la carte.
     * Remplace l'affichage précédent (appeler à nouveau après chaque ajout/suppression).
     */
    public void afficherPointsRemarquables(List<PointRemarquable> points) {
        this.pointsAffiches = points;
        rafraichirAffichage();
    }

    /**
     * Affiche le tracé de plusieurs trajets, avec celui sélectionné mis en évidence.
     * Les trajets non sélectionnés sont dessinés en fin et semi-transparent (juste le
     * contexte géographique) ; le trajet sélectionné est dessiné en dernier, en rouge
     * et plus épais, pour rester visible même par-dessus les autres.
     *
     * @param trajets      tous les trajets à afficher (typiquement la liste filtrée courante)
     * @param selectionne  le trajet à mettre en évidence, ou null si aucun n'est sélectionné
     */
    public void afficherTrajets(List<Trajet> trajets, Trajet selectionne) {
        this.trajetsAffiches = trajets;
        this.trajetSelectionne = selectionne;
        rafraichirAffichage();
    }

    /**
     * Recompose les calques (tracés des trajets + marqueurs des points remarquables)
     * à partir de l'état actuel, et redessine la carte.
     */
    private void rafraichirAffichage() {
        Set<PointRemarquableWaypoint> waypoints = new HashSet<>();
        for (PointRemarquable point : pointsAffiches) {
            waypoints.add(new PointRemarquableWaypoint(point));
        }
        WaypointPainter<PointRemarquableWaypoint> waypointPainter = new WaypointPainter<>();
        waypointPainter.setWaypoints(waypoints);
        waypointPainter.setRenderer(new PointRemarquableRenderer());

        TrajetsPainter trajetsPainter = new TrajetsPainter(trajetsAffiches, trajetSelectionne);

        // Ordre important : les tracés d'abord, les marqueurs de points remarquables
        // par-dessus, pour qu'ils restent visibles même si un trajet passe dessous.
        CompoundPainter<JXMapViewer> compound = new CompoundPainter<>(trajetsPainter, waypointPainter);
        mapViewer.setOverlayPainter(compound);
        mapViewer.repaint();
    }

    /**
     * Un Waypoint qui garde en mémoire le nom du PointRemarquable qu'il représente,
     * pour pouvoir l'afficher à côté du marqueur.
     */
    private static class PointRemarquableWaypoint extends DefaultWaypoint {
        private final String nom;

        PointRemarquableWaypoint(PointRemarquable point) {
            super(new GeoPosition(point.getLatitude(), point.getLongitude()));
            this.nom = point.getNom();
        }

        String getNom() {
            return nom;
        }
    }

    /**
     * Dessine chaque point remarquable comme un disque rouge, avec son nom à côté
     * (en blanc cerné de noir, pour rester lisible quel que soit le fond de carte).
     */
    private static class PointRemarquableRenderer implements WaypointRenderer<PointRemarquableWaypoint> {
        @Override
        public void paintWaypoint(Graphics2D g, JXMapViewer map, PointRemarquableWaypoint waypoint) {
            Point2D point = map.getTileFactory().geoToPixel(waypoint.getPosition(), map.getZoom());
            int x = (int) point.getX();
            int y = (int) point.getY();

            g.setColor(Color.RED);
            g.fillOval(x - 6, y - 6, 12, 12);
            g.setColor(Color.BLACK);
            g.drawOval(x - 6, y - 6, 12, 12);

            g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
            g.setColor(Color.WHITE);
            g.drawString(waypoint.getNom(), x + 9, y + 3);
            g.setColor(Color.BLACK);
            g.drawString(waypoint.getNom(), x + 8, y + 4);
        }
    }

    /**
     * Dessine le tracé de plusieurs trajets : les non-sélectionnés en fin trait bleu
     * semi-transparent (contexte géographique), le trajet sélectionné en rouge épais
     * par-dessus (dessiné en dernier pour rester visible malgré les chevauchements).
     */
    private static class TrajetsPainter implements Painter<JXMapViewer> {
        private static final Color COULEUR_CONTEXTE = new Color(60, 60, 200, 110);
        private static final Color COULEUR_SELECTION = Color.RED;

        private final List<Trajet> trajets;
        private final Trajet selectionne;

        TrajetsPainter(List<Trajet> trajets, Trajet selectionne) {
            this.trajets = trajets;
            this.selectionne = selectionne;
        }

        @Override
        public void paint(Graphics2D g, JXMapViewer map, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            Rectangle rect = map.getViewportBounds();
            g2.translate(-rect.x, -rect.y);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            for (Trajet trajet : trajets) {
                if (trajet != selectionne) {
                    dessiner(g2, map, trajet, COULEUR_CONTEXTE, 2f);
                }
            }
            if (selectionne != null) {
                dessiner(g2, map, selectionne, COULEUR_SELECTION, 3f);
            }

            g2.dispose();
        }

        private void dessiner(Graphics2D g, JXMapViewer map, Trajet trajet, Color couleur, float epaisseur) {
            List<PointGpx> points = trajet.getPoints();
            if (points.size() < 2) {
                return;
            }
            g.setColor(couleur);
            g.setStroke(new BasicStroke(epaisseur));

            int[] xs = new int[points.size()];
            int[] ys = new int[points.size()];
            for (int i = 0; i < points.size(); i++) {
                PointGpx point = points.get(i);
                Point2D pixel = map.getTileFactory().geoToPixel(
                    new GeoPosition(point.getLatitude(), point.getLongitude()), map.getZoom());
                xs[i] = (int) pixel.getX();
                ys[i] = (int) pixel.getY();
            }
            g.drawPolyline(xs, ys, points.size());
        }
    }
}