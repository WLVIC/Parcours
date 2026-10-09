package org.wvicto.parcours.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.wvicto.parcours.model.PointGpx.SourceAltitude;

/**
 * Calcule, point par point, le profil d'un trajet pour l'affichage graphique :
 * distance cumulée depuis le départ, altitude, vitesse et pente instantanées à chaque
 * point. Même principe de séparation des responsabilités que StatistiquesTrajet : Trajet
 * gère les données, cette classe gère un calcul dérivé particulier (les séries à tracer).
 *
 * L'altitude lue sur chaque point dépend de la SourceAltitude choisie. Quand un point n'a
 * pas d'altitude pour cette source, elle vaut Double.NaN dans le profil (« non-nombre » :
 * un double qui signifie « valeur inconnue »). Tout ce qui en dépend (pente) vaut alors
 * NaN aussi, au lieu d'inventer une valeur. Pour tester : Double.isNaN(valeur).
 */
public class ProfilTrajet {
    private final Trajet trajet;
    private final SourceAltitude sourceAltitude;

    /** Profil avec la meilleure altitude disponible (externe si elle existe, sinon GPX). */
    public ProfilTrajet(Trajet trajet) {
        this(trajet, SourceAltitude.MEILLEURE);
    }

    public ProfilTrajet(Trajet trajet, SourceAltitude sourceAltitude) {
        if (trajet == null) {
            throw new IllegalArgumentException("Le trajet ne peut pas être null");
        }
        if (sourceAltitude == null) {
            throw new IllegalArgumentException("La source d'altitude ne peut pas être null");
        }
        this.trajet = trajet;
        this.sourceAltitude = sourceAltitude;
    }

    /**
     * Un point du profil : distance cumulée depuis le départ (km), altitude (m) à cette
     * distance (NaN si elle est inconnue), vitesse instantanée (km/h) et pente instantanée
     * (%, NaN si l'altitude d'une des deux extrémités du segment est inconnue) sur le
     * segment menant à ce point (0 pour le tout premier point, faute de segment précédent),
     * et son horodatage (peut être null pour un point sans horodatage exploitable).
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
        profil.add(new PointProfil(0.0, altitudeDe(points.get(0)), 0.0, 0.0, points.get(0).getTimestamp()));

        for (int i = 1; i < points.size(); i++) {
            PointGpx precedent = points.get(i - 1);
            PointGpx courant = points.get(i);

            double distanceSegmentKm = precedent.distanceTo(courant);
            distanceCumulee += distanceSegmentKm;

            double altitudePrecedente = altitudeDe(precedent);
            double altitudeCourante = altitudeDe(courant);

            profil.add(new PointProfil(
                distanceCumulee,
                altitudeCourante,
                calculerVitesseKmh(precedent, courant, distanceSegmentKm),
                calculerPentePourcent(distanceSegmentKm, altitudePrecedente, altitudeCourante),
                courant.getTimestamp()
            ));
        }
        return profil;
    }

    /** Altitude du point pour la source choisie, ou NaN si ce point n'en a pas. */
    private double altitudeDe(PointGpx point) {
        return point.getAltitude(sourceAltitude).orElse(Double.NaN);
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
     * Négative en descente. Renvoie NaN si l'une des deux altitudes est inconnue, et 0 si la
     * distance du segment est nulle (deux points au même endroit), pour éviter une division
     * par zéro.
     */
    private double calculerPentePourcent(double distanceSegmentKm, double altitudePrecedente, double altitudeCourante) {
        if (Double.isNaN(altitudePrecedente) || Double.isNaN(altitudeCourante)) {
            return Double.NaN;
        }
        double distanceSegmentM = distanceSegmentKm * 1000.0;
        if (distanceSegmentM <= 0) {
            return 0.0;
        }
        double deniveleM = altitudeCourante - altitudePrecedente;
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
     * Lisse l'altitude du profil en deux temps, et renvoie un nouveau profil (les autres
     * colonnes sont inchangées) :
     *
     * 1. une médiane glissante sur 'tailleMediane' points (petite : 5 ou 7) qui élimine les
     *    points aberrants isolés (un pic d'altitude GPS, par exemple) ;
     * 2. une moyenne pondérée sur une DISTANCE : chaque point devient la moyenne des points
     *    situés à moins de 'demiLargeurM' mètres de lui, le poids d'un voisin diminuant
     *    linéairement avec son éloignement (un point qui entre ou sort de la fenêtre n'a
     *    ainsi presque aucun effet, ce qui évite les petits sauts d'une moyenne à poids
     *    égaux).
     *
     * La largeur du lissage est donnée en mètres et non en nombre de points : l'écart entre
     * deux points dépend de la vitesse (quelques mètres à pied, bien davantage à vélo ou en
     * voiture) et une fenêtre en nombre de points ne lisserait pas la même chose d'une trace
     * à l'autre. Les points sans altitude (NaN) restent sans altitude et sont ignorés dans
     * les moyennes : on ne comble pas les trous.
     *
     * @param tailleMediane largeur de la médiane, en nombre de points (1 = pas de médiane)
     * @param demiLargeurM  distance maximale (m) des voisins pris en compte, de chaque côté
     *                      (0 = pas de moyenne)
     */
    public static List<PointProfil> lisserAltitude(List<PointProfil> profil, int tailleMediane,
                                                   double demiLargeurM) {
        List<Double> altitudes = medianeGlissante(
            profil.stream().map(PointProfil::altitude).toList(), tailleMediane);
        altitudes = moyennePonderee(profil, altitudes, demiLargeurM / 1000.0);

        List<PointProfil> resultat = new ArrayList<>(profil.size());
        for (int i = 0; i < profil.size(); i++) {
            PointProfil p = profil.get(i);
            resultat.add(new PointProfil(p.distanceKm(), altitudes.get(i), p.vitesseKmh(),
                p.pentePourcent(), p.horodatage()));
        }
        return resultat;
    }

    /**
     * Calcule la pente de chaque point sur une fenêtre de DISTANCE, à partir des altitudes
     * du profil telles qu'elles sont (pensez à appeler lisserAltitude avant).
     *
     * Pourquoi une fenêtre de distance : deux points consécutifs ne sont distants que de
     * quelques mètres (un point par seconde). La moindre irrégularité de l'altitude entre
     * eux suffit alors à fabriquer des pentes aberrantes : 0,5 m d'écart sur 3 m, c'est
     * 17 %. Une pente est une dérivée, et une dérivée amplifie le bruit. On prend donc la
     * pente de la droite qui relie les deux extrémités d'une fenêtre d'environ
     * 'fenetreDistanceM' mètres centrée sur le point : c'est la même idée que pour la
     * vitesse, calculée sur une fenêtre de temps.
     *
     * La pente d'un point dont l'altitude, ou celle d'une extrémité de sa fenêtre, est
     * inconnue (NaN) vaut NaN.
     *
     * @param fenetreDistanceM largeur de la fenêtre, en mètres (ex: 50). Plus grand = pente
     *                         plus lisse mais moins précise sur les changements brusques. Si
     *                         la fenêtre est plus étroite que l'écart entre deux points, on
     *                         prend les deux points voisins.
     */
    public static List<PointProfil> lisserPente(List<PointProfil> profil, double fenetreDistanceM) {
        int n = profil.size();
        List<PointProfil> resultat = new ArrayList<>(n);
        double demiFenetreKm = fenetreDistanceM / 2000.0;

        int debut = 0;
        int fin = 0;
        for (int i = 0; i < n; i++) {
            double distance = profil.get(i).distanceKm();

            // Premier point de la fenêtre, et dernier point qui y est encore (les distances
            // cumulées ne diminuent jamais : les deux repères ne font qu'avancer)
            while (profil.get(debut).distanceKm() < distance - demiFenetreKm) {
                debut++;
            }
            while (fin + 1 < n && profil.get(fin + 1).distanceKm() <= distance + demiFenetreKm) {
                fin++;
            }

            int a = debut;
            int b = fin;
            if (a == b) {   // fenêtre trop étroite : on prend les deux voisins
                a = Math.max(0, i - 1);
                b = Math.min(n - 1, i + 1);
            }

            PointProfil p = profil.get(i);
            resultat.add(new PointProfil(distance, p.altitude(), p.vitesseKmh(),
                penteEntre(profil, a, b, i), p.horodatage()));
        }
        return resultat;
    }

    /** Pente (%) de la droite entre les points a et b ; NaN si une des altitudes en jeu est inconnue. */
    private static double penteEntre(List<PointProfil> profil, int a, int b, int i) {
        double altitudeDuPoint = profil.get(i).altitude();
        double altitudeA = profil.get(a).altitude();
        double altitudeB = profil.get(b).altitude();
        if (Double.isNaN(altitudeDuPoint) || Double.isNaN(altitudeA) || Double.isNaN(altitudeB)) {
            return Double.NaN;
        }
        double distanceM = (profil.get(b).distanceKm() - profil.get(a).distanceKm()) * 1000.0;
        return distanceM <= 0 ? 0.0 : ((altitudeB - altitudeA) / distanceM) * 100.0;
    }

    /**
     * Moyenne pondérée sur une distance : le poids d'un voisin vaut 1 à distance nulle et
     * décroît linéairement jusqu'à 0 à 'demiLargeurKm'. Les NaN sont ignorés, et un point
     * dont la valeur est NaN reste NaN.
     */
    private static List<Double> moyennePonderee(List<PointProfil> profil, List<Double> valeurs,
                                                double demiLargeurKm) {
        if (demiLargeurKm <= 0) {
            return valeurs;
        }
        int n = valeurs.size();
        List<Double> lisse = new ArrayList<>(n);
        int debut = 0;
        int fin = 0;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(valeurs.get(i))) {
                lisse.add(Double.NaN);
                continue;
            }
            double distance = profil.get(i).distanceKm();
            while (profil.get(debut).distanceKm() < distance - demiLargeurKm) {
                debut++;
            }
            while (fin + 1 < n && profil.get(fin + 1).distanceKm() <= distance + demiLargeurKm) {
                fin++;
            }

            double sommePoids = 0.0;
            double somme = 0.0;
            for (int j = debut; j <= fin; j++) {
                double valeur = valeurs.get(j);
                if (Double.isNaN(valeur)) {
                    continue;
                }
                double poids = 1.0 - Math.abs(profil.get(j).distanceKm() - distance) / demiLargeurKm;
                sommePoids += poids;
                somme += poids * valeur;
            }
            lisse.add(sommePoids > 0 ? somme / sommePoids : valeurs.get(i));
        }
        return lisse;
    }

    /**
     * Médiane glissante centrée : chaque valeur est remplacée par la médiane des valeurs
     * dans une fenêtre de 'tailleFenetre' autour d'elle (rétrécie automatiquement près des
     * extrémités). Un point isolé très éloigné des autres n'a presque aucune influence.
     *
     * Les valeurs NaN (altitude inconnue) sont ignorées dans le calcul de la médiane, et
     * une valeur NaN reste NaN : le lissage ne comble pas les trous.
     */
    private static List<Double> medianeGlissante(List<Double> valeurs, int tailleFenetre) {
        int n = valeurs.size();
        List<Double> lisse = new ArrayList<>(n);
        int demiFenetre = tailleFenetre / 2;

        for (int i = 0; i < n; i++) {
            if (Double.isNaN(valeurs.get(i))) {
                lisse.add(Double.NaN);
                continue;
            }

            int debut = Math.max(0, i - demiFenetre);
            int fin = Math.min(n - 1, i + demiFenetre);

            List<Double> fenetre = new ArrayList<>();
            for (int j = debut; j <= fin; j++) {
                if (!Double.isNaN(valeurs.get(j))) {
                    fenetre.add(valeurs.get(j));
                }
            }
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