package org.wvicto.parcours.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe dédiée au calcul des statistiques pour un trajet.
 * Séparation des responsabilités : Trajet gère les données, cette classe gère les calculs.
 */
public class StatistiquesTrajet {
    private final Trajet trajet;

    public StatistiquesTrajet(Trajet trajet) {
        if (trajet == null) {
            throw new IllegalArgumentException("Le trajet ne peut pas être null");
        }
        this.trajet = trajet;
    }

    // ==================== DISTANCE ====================
    public double getDistanceTotaleKm() {
        List<PointGpx> points = trajet.getPoints();
        if (points.size() < 2) return 0.0;
        double distanceTotale = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            distanceTotale += points.get(i).distanceTo(points.get(i + 1));
        }
        return distanceTotale;
    }

    // ==================== PENTES ====================
    public List<Double> getPentesPourcent() {
        List<PointGpx> points = trajet.getPoints();
        List<Double> pentes = new ArrayList<>();
        if (points.size() < 2) return pentes;
        for (int i = 0; i < points.size() - 1; i++) {
            pentes.add(calculerPentePourcent(points.get(i), points.get(i + 1)));
        }
        return pentes;
    }

    public double getPenteMax() {
        return getPentesPourcent().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    public double getPenteMoyenne() {
        return getPentesPourcent().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    // ==================== DÉNIVELÉ ====================
    public double getDenivelePositif() {
        return calculerDenivele(true);
    }

    public double getDeniveleNegatif() {
        return calculerDenivele(false);
    }

    // ==================== MÉTHODES PRIVÉES ====================
    private double calculerPentePourcent(PointGpx p1, PointGpx p2) {
        double deltaAltitude = p2.getAltitude() - p1.getAltitude();
        double distanceHorizontaleKm = p1.distanceTo(p2);
        return (distanceHorizontaleKm == 0) ? 0.0 : (deltaAltitude / (distanceHorizontaleKm * 1000)) * 100;
    }

    private double calculerDenivele(boolean positif) {
        double denivele = 0.0;
        List<PointGpx> points = trajet.getPoints();
        for (int i = 0; i < points.size() - 1; i++) {
            double delta = points.get(i + 1).getAltitude() - points.get(i).getAltitude();
            if ((positif && delta > 0) || (!positif && delta < 0)) {
                denivele += Math.abs(delta);
            }
        }
        return denivele;
    }
}