package org.wvicto.parcours.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Classe utilitaire pour filtrer des trajets selon des critères géographiques.
 * Respecte le principe de responsabilité unique : cette classe gère uniquement le filtrage.
 */
public class FiltreTrajet {

    /**
     * Filtre les trajets qui passent à moins de 'radiusKm' kilomètres d'un point donné.
     */
    public static List<Trajet> filtrerParProximite(List<Trajet> trajets, PointGpx point, double radiusKm) {
        if (point == null || trajets == null) {
            throw new IllegalArgumentException("Le point et la liste de trajets ne peuvent pas être null");
        }
        return trajets.stream()
                .filter(t -> t.containsPoint(point, radiusKm))
                .collect(Collectors.toList());
    }

    /**
     * Filtre les trajets dans une zone (boîte englobante).
     */
    public static List<Trajet> filtrerParZone(List<Trajet> trajets, PointGpx coinNordOuest, PointGpx coinSudEst) {
        if (coinNordOuest == null || coinSudEst == null || trajets == null) {
            throw new IllegalArgumentException("Les points et la liste de trajets ne peuvent pas être null");
        }
        return trajets.stream()
                .filter(t -> t.isWithinBoundingBox(coinNordOuest, coinSudEst))
                .collect(Collectors.toList());
    }

    /**
     * Filtre les trajets passant par un point.
     */
    public static List<Trajet> filtrerParPointPassage(List<Trajet> trajets, PointGpx point) {
        return filtrerParProximite(trajets, point, Constants.DEFAULT_RADIUS_KM);
    }
}