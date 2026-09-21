package org.wvicto.parcours.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Une route de référence, équivalente à l'élément "rte" du format GPX :
 * une séquence ORDONNÉE de waypoints (équivalents des "rtept"), plus légère
 * qu'un Trajet ("trk") enregistré. Sert de "signature" géographique pour
 * reconnaître les trajets qui suivent le même itinéraire.
 */
public class Route {
    private String nom;
    private List<PointGpx> points;

    public Route(String nom, List<PointGpx> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("Une route nécessite au moins 2 points");
        }
        this.nom = nom;
        this.points = new ArrayList<>(points);
    }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public List<PointGpx> getPoints() { return new ArrayList<>(points); }

    @Override
    public String toString() {
        return String.format("🛣️ %s (%d points)", nom, points.size());
    }
}