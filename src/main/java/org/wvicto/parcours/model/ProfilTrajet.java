package org.wvicto.parcours.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calcule, point par point, le profil d'un trajet pour l'affichage graphique :
 * distance cumulée depuis le départ, altitude, vitesse et pente instantanées à chaque
 * point. Même principe de séparation des responsabilités que StatistiquesTrajet : Trajet
 * gère les données, cette classe gère un calcul dérivé particulier (les séries à tracer).
 */
public class ProfilTrajet {
    private final Trajet trajet;

    public ProfilTrajet(Trajet trajet) {
        if (trajet == null) {
            throw new IllegalArgumentException("Le trajet ne peut pas être null");
        }
        this.trajet = trajet;
    }

    /**
     * Un point du profil : distance cumulée depuis le départ (km), altitude (m) à cette
     * distance, vitesse instantanée (km/h) et pente instantanée (%) sur le segment menant
     * à ce point (0 pour le tout premier point, faute de segment précédent), et son
     * horodatage (peut être null pour un point sans horodatage exploitable).
     */
    public record PointProfil(double distanceKm, double altitude, double vitesseKmh,
                               double pentePourcent, LocalDateTime horodatage) {}

    public List<PointProfil> calculer() {
        List<PointGpx> points = trajet.getPoints();
        List<PointProfil> profil = new ArrayList<>();
        if (points.isEmpty()) {
            return profil;
        }

        double distanceCumulee = 0.0;
        profil.add(new PointProfil(0.0, points.get(0).getAltitude(), 0.0, 0.0, points.get(0).getTimestamp()));

        for (int i = 1; i < points.size(); i++) {
            PointGpx precedent = points.get(i - 1);
            PointGpx courant = points.get(i);

            double distanceSegmentKm = precedent.distanceTo(courant);
            distanceCumulee += distanceSegmentKm;

            profil.add(new PointProfil(
                distanceCumulee,
                courant.getAltitude(),
                calculerVitesseKmh(precedent, courant, distanceSegmentKm),
                calculerPentePourcent(precedent, courant, distanceSegmentKm),
                courant.getTimestamp()
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
     * Pente moyenne sur un segment, en pourcentage (dénivelé / distance horizontale x 100).
     * Négative en descente. Renvoie 0 si la distance du segment est nulle (deux points au
     * même endroit), pour éviter une division par zéro.
     */
    private double calculerPentePourcent(PointGpx precedent, PointGpx courant, double distanceSegmentKm) {
        double distanceSegmentM = distanceSegmentKm * 1000.0;
        if (distanceSegmentM <= 0) {
            return 0.0;
        }
        double deniveleM = courant.getAltitude() - precedent.getAltitude();
        return (deniveleM / distanceSegmentM) * 100.0;
    }

    /**
     * Comme pour la pente, on ne lisse pas après coup une vitesse déjà calculée point à
     * point (une dérivée, donc déjà amplifiée) : ici, le bruit vient surtout d'intervalles
     * de temps très courts entre deux points consécutifs (~1s), où même une petite
     * imprécision de position donne, une fois divisée, plusieurs km/h d'écart apparent.
     * On calcule donc la vitesse sur une fenêtre de temps plus large : distance parcourue
     * entre deux points distants de 'tailleFenetre/2' de chaque côté du point courant,
     * divisée par le temps réellement écoulé entre eux — une vitesse moyenne sur un
     * intervalle plus robuste, plutôt que la moyenne de plusieurs vitesses instantanées
     * déjà bruitées.
     *
     * @param tailleFenetre largeur de la fenêtre en nombre de points (ex: 20). Plus grand
     *                       = plus stable, mais aussi plus "en retard" sur les vraies
     *                       variations (accélérations, freinages).
     */
    public static List<PointProfil> lisserVitesse(List<PointProfil> profil, int tailleFenetre) {
        int demiFenetre = tailleFenetre / 2;
        int n = profil.size();
        List<PointProfil> resultat = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            int debut = Math.max(0, i - demiFenetre);
            int fin = Math.min(n - 1, i + demiFenetre);

            PointProfil pDebut = profil.get(debut);
            PointProfil pFin = profil.get(fin);

            double vitesse = 0.0;
            if (debut != fin && pDebut.horodatage() != null && pFin.horodatage() != null) {
                double distanceSegmentKm = pFin.distanceKm() - pDebut.distanceKm();
                double dureeHeures = Duration.between(pDebut.horodatage(), pFin.horodatage()).toMillis() / 3_600_000.0;
                vitesse = dureeHeures <= 0 ? 0.0 : distanceSegmentKm / dureeHeures;
            }

            PointProfil p = profil.get(i);
            resultat.add(new PointProfil(p.distanceKm(), p.altitude(), vitesse, p.pentePourcent(), p.horodatage()));
        }
        return resultat;
    }

    /**
     * Contrairement à l'ancienne version, on ne lisse pas la pente brute après coup : une
     * pente est une dérivée (altitude qui change / distance), et une dérivée AMPLIFIE le
     * bruit du signal d'origine avant même qu'on ait la chance de le lisser. Lisser après
     * coup ne fait alors que nettoyer imparfaitement un dégât déjà fait — surtout si
     * l'anomalie d'altitude dure sur plusieurs points consécutifs (dégradation du signal
     * satellite prolongée, pas un seul point isolé), auquel cas la fenêtre de lissage
     * n'a plus une minorité de valeurs aberrantes à ignorer.
     * On lisse donc l'ALTITUDE d'abord (par médiane, pour rester robuste à un point ou une
     * courte série de points aberrants), puis on calcule la pente à partir de l'altitude
     * déjà lissée : deux altitudes lissées voisines se ressemblent bien plus que deux
     * altitudes brutes voisines, donc leur différence est mécaniquement plus stable.
     */
    public static List<PointProfil> lisserPente(List<PointProfil> profil, int tailleFenetre) {
        List<Double> altitudesLissees = medianeGlissante(
            profil.stream().map(PointProfil::altitude).toList(), tailleFenetre);

        List<PointProfil> resultat = new ArrayList<>(profil.size());
        resultat.add(new PointProfil(
            profil.get(0).distanceKm(), profil.get(0).altitude(), profil.get(0).vitesseKmh(),
            0.0, profil.get(0).horodatage()));

        for (int i = 1; i < profil.size(); i++) {
            PointProfil precedent = profil.get(i - 1);
            PointProfil courant = profil.get(i);

            double distanceSegmentM = (courant.distanceKm() - precedent.distanceKm()) * 1000.0;
            double deniveleLisseM = altitudesLissees.get(i) - altitudesLissees.get(i - 1);
            double pente = distanceSegmentM <= 0 ? 0.0 : (deniveleLisseM / distanceSegmentM) * 100.0;

            resultat.add(new PointProfil(
                courant.distanceKm(), courant.altitude(), courant.vitesseKmh(), pente, courant.horodatage()));
        }
        return resultat;
    }

    /**
     * Médiane glissante centrée : chaque valeur est remplacée par la médiane des valeurs
     * dans une fenêtre de 'tailleFenetre' autour d'elle (rétrécie automatiquement près des
     * extrémités). Un point isolé très éloigné des autres n'a presque aucune influence.
     */
    private static List<Double> medianeGlissante(List<Double> valeurs, int tailleFenetre) {
        int n = valeurs.size();
        List<Double> lisse = new ArrayList<>(n);
        int demiFenetre = tailleFenetre / 2;

        for (int i = 0; i < n; i++) {
            int debut = Math.max(0, i - demiFenetre);
            int fin = Math.min(n - 1, i + demiFenetre);

            List<Double> fenetre = new ArrayList<>(valeurs.subList(debut, fin + 1));
            Collections.sort(fenetre);
            int taille = fenetre.size();
            double mediane = (taille % 2 == 1)
                ? fenetre.get(taille / 2)
                : (fenetre.get(taille / 2 - 1) + fenetre.get(taille / 2)) / 2.0;
            lisse.add(mediane);
        }
        return lisse;
    }
}