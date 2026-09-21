package org.wvicto.parcours.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Une portion de trajet caractérisée par une séquence ORDONNÉE de points remarquables.
 * Couvre deux usages :
 *  - une simple connexion entre deux points (ex: "Domicile → Travail", peu importe le chemin)
 *  - une portion plus précise définie par plusieurs points (ex: une côte, une variante de trajet
 *    passant par un point intermédiaire particulier)
 */
public class PortionRemarquable {
    private String nom;
    private List<PointRemarquable> points; // ordre = ordre attendu le long du trajet
    private double rayonKm; // tolérance de proximité pour considérer qu'un point est "atteint"

    public PortionRemarquable(String nom, List<PointRemarquable> points, double rayonKm) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("Une portion remarquable nécessite au moins 2 points");
        }
        this.nom = nom;
        this.points = new ArrayList<>(points);
        this.rayonKm = rayonKm;
    }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public List<PointRemarquable> getPoints() { return new ArrayList<>(points); }

    public double getRayonKm() { return rayonKm; }
    public void setRayonKm(double rayonKm) { this.rayonKm = rayonKm; }

    @Override
    public String toString() {
        return String.format("%s (%d points)", nom, points.size());
    }
}