package org.wvicto.parcours.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.DoubleStream;

import org.wvicto.parcours.model.PointGpx.SourceAltitude;

/**
 * Classe dédiée au calcul des statistiques pour un trajet.
 * Séparation des responsabilités : Trajet gère les données, cette classe gère les calculs.
 *
 * Les pentes et dénivelés se calculent sur l'altitude de la SourceAltitude choisie. Un
 * segment dont l'un des deux points n'a pas d'altitude est ignoré. Si aucun segment n'est
 * exploitable, les résultats valent Double.NaN (« inconnu ») et non 0 : un trajet sans
 * altitude n'est pas un trajet plat.
 */
public class StatistiquesTrajet {
    private final Trajet trajet;
    private final SourceAltitude sourceAltitude;

    /** Statistiques avec la meilleure altitude disponible (externe si elle existe, sinon GPX). */
    public StatistiquesTrajet(Trajet trajet) {
        this(trajet, SourceAltitude.MEILLEURE);
    }

    public StatistiquesTrajet(Trajet trajet, SourceAltitude sourceAltitude) {
        if (trajet == null) {
            throw new IllegalArgumentException("Le trajet ne peut pas être null");
        }
        if (sourceAltitude == null) {
            throw new IllegalArgumentException("La source d'altitude ne peut pas être null");
        }
        this.trajet = trajet;
        this.sourceAltitude = sourceAltitude;
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

    /**
     * Pente (%) de chaque segment, dans l'ordre du trajet (le segment i relie le point i au
     * point i+1). Vaut NaN pour un segment dont une extrémité n'a pas d'altitude : on garde
     * ainsi la liste alignée sur les segments.
     */
    public List<Double> getPentesPourcent() {
        List<PointGpx> points = trajet.getPoints();
        List<Double> pentes = new ArrayList<>();
        if (points.size() < 2) return pentes;
        for (int i = 0; i < points.size() - 1; i++) {
            pentes.add(calculerPentePourcent(points.get(i), points.get(i + 1)));
        }
        return pentes;
    }

    /** Pente maximale (%), ou NaN si aucun segment n'a d'altitude à ses deux extrémités. */
    public double getPenteMax() {
        return pentesConnues().max().orElse(Double.NaN);
    }

    /** Pente moyenne (%), ou NaN si aucun segment n'a d'altitude à ses deux extrémités. */
    public double getPenteMoyenne() {
        return pentesConnues().average().orElse(Double.NaN);
    }

    // ==================== DÉNIVELÉ ====================

    /** Dénivelé positif (m), ou NaN si aucun segment n'a d'altitude à ses deux extrémités. */
    public double getDenivelePositif() {
        return calculerDenivele(true);
    }

    /** Dénivelé négatif (m, valeur positive), ou NaN si aucun segment n'est exploitable. */
    public double getDeniveleNegatif() {
        return calculerDenivele(false);
    }

    // ==================== MÉTHODES PRIVÉES ====================

    /** Altitude du point pour la source choisie, ou NaN si ce point n'en a pas. */
    private double altitudeDe(PointGpx point) {
        return point.getAltitude(sourceAltitude).orElse(Double.NaN);
    }

    private DoubleStream pentesConnues() {
        return getPentesPourcent().stream()
            .mapToDouble(Double::doubleValue)
            .filter(pente -> !Double.isNaN(pente));
    }

    private double calculerPentePourcent(PointGpx p1, PointGpx p2) {
        double altitude1 = altitudeDe(p1);
        double altitude2 = altitudeDe(p2);
        if (Double.isNaN(altitude1) || Double.isNaN(altitude2)) {
            return Double.NaN;
        }
        double deltaAltitude = altitude2 - altitude1;
        double distanceHorizontaleKm = p1.distanceTo(p2);
        return (distanceHorizontaleKm == 0) ? 0.0 : (deltaAltitude / (distanceHorizontaleKm * 1000)) * 100;
    }

    private double calculerDenivele(boolean positif) {
        double denivele = 0.0;
        boolean segmentExploitable = false;
        List<PointGpx> points = trajet.getPoints();
        for (int i = 0; i < points.size() - 1; i++) {
            double altitude1 = altitudeDe(points.get(i));
            double altitude2 = altitudeDe(points.get(i + 1));
            if (Double.isNaN(altitude1) || Double.isNaN(altitude2)) {
                continue; // segment ignoré : altitude inconnue à l'une des extrémités
            }
            segmentExploitable = true;
            double delta = altitude2 - altitude1;
            if ((positif && delta > 0) || (!positif && delta < 0)) {
                denivele += Math.abs(delta);
            }
        }
        return segmentExploitable ? denivele : Double.NaN;
    }
}