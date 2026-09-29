package org.wvicto.parcours.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ProfilTrajet {
    private final Trajet trajet;

    public ProfilTrajet(Trajet trajet) {
        if (trajet == null) {
            throw new IllegalArgumentException("Le trajet ne peut pas être null");
        }
        this.trajet = trajet;
    }

    public record PointProfil(double distanceKm, double altitude, double vitesseKmh) {}

    public List<PointProfil> calculer() {
        List<PointGpx> points = trajet.getPoints();
        List<PointProfil> profil = new ArrayList<>();
        if (points.isEmpty()) {
            return profil;
        }

        double distanceCumulee = 0.0;
        profil.add(new PointProfil(0.0, points.get(0).getAltitude(), 0.0));

        for (int i = 1; i < points.size(); i++) {
            PointGpx precedent = points.get(i - 1);
            PointGpx courant = points.get(i);

            double distanceSegmentKm = precedent.distanceTo(courant);
            distanceCumulee += distanceSegmentKm;

            profil.add(new PointProfil(
                distanceCumulee,
                courant.getAltitude(),
                calculerVitesseKmh(precedent, courant, distanceSegmentKm)
            ));
        }
        return profil;
    }

    /**
     * Vitesse moyenne sur un segment (distance du segment / durée entre les deux
     * horodatages). Renvoie 0 si l'un des deux points n'a pas d'horodatage exploitable
     * (ex: points ajoutés manuellement) ou si la durée est nulle ou négative.
     */
    private double calculerVitesseKmh(PointGpx precedent, PointGpx courant, double distanceSegmentKm) {
        if (precedent.getTimestamp() == null || courant.getTimestamp() == null) {
            return 0.0;
        }
        double dureeHeures = Duration.between(precedent.getTimestamp(), courant.getTimestamp()).toMillis() / 3_600_000.0;
        return dureeHeures <= 0 ? 0.0 : distanceSegmentKm / dureeHeures;
    }
    
    /**
     * Lisse les vitesses d'un profil par moyenne glissante centrée : chaque vitesse est
     * remplacée par la moyenne des vitesses dans une fenêtre de 'tailleFenetre' points
     * autour d'elle (rétrécie automatiquement près des extrémités). Nécessaire car la
     * vitesse point à point est très bruitée (imprécision GPS amplifiée par des
     * intervalles de temps courts) — distance et altitude restent inchangées.
     *
     * @param tailleFenetre nombre de points de la fenêtre (ex: 5). Plus grand = plus lisse,
     *                       mais aussi plus "en retard" sur les vraies variations de vitesse.
     */
    public static List<PointProfil> lisserVitesse(List<PointProfil> profil, int tailleFenetre) {
        int n = profil.size();
        List<PointProfil> lisse = new ArrayList<>(n);
        int demiFenetre = tailleFenetre / 2;

        for (int i = 0; i < n; i++) {
            int debut = Math.max(0, i - demiFenetre);
            int fin = Math.min(n - 1, i + demiFenetre);

            double sommeVitesse = 0;
            int compte = 0;
            for (int j = debut; j <= fin; j++) {
                sommeVitesse += profil.get(j).vitesseKmh();
                compte++;
            }

            PointProfil p = profil.get(i);
            lisse.add(new PointProfil(p.distanceKm(), p.altitude(), sommeVitesse / compte));
        }
        return lisse;
    }
}