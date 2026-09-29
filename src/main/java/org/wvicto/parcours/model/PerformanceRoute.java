package org.wvicto.parcours.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calcule, pour une route donnée, le temps mis par chaque trajet qui la parcourt
 * (entre son premier et son dernier point de correspondance), pour suivre l'évolution
 * des performances dans le temps et identifier le record.
 */
public class PerformanceRoute {

    /** Un trajet ayant parcouru la route, avec le temps mis pour la parcourir. */
    public record Performance(Trajet trajet, Duration duree) {}

    /**
     * Calcule les performances de tous les trajets qui correspondent à la route.
     * Ignore silencieusement les trajets qui ne correspondent pas, ou dont les points
     * de correspondance n'ont pas d'horodatage exploitable.
     */
    public static List<Performance> calculer(List<Trajet> trajets, Route route, double rayonKm) {
        List<Performance> performances = new ArrayList<>();

        for (Trajet trajet : trajets) {
            int[] indices = FiltreTrajet.trouverIndicesRoute(trajet, route, rayonKm);
            if (indices == null) {
                continue;
            }

            PointGpx debut = trajet.getPoints().get(indices[0]);
            PointGpx fin = trajet.getPoints().get(indices[indices.length - 1]);
            if (debut.getTimestamp() == null || fin.getTimestamp() == null) {
                continue;
            }

            Duration duree = Duration.between(debut.getTimestamp(), fin.getTimestamp());
            if (!duree.isNegative()) {
                performances.add(new Performance(trajet, duree));
            }
        }
        return performances;
    }

    /** Le meilleur temps (le plus court) parmi une liste de performances, ou null si vide. */
    public static Performance record(List<Performance> performances) {
        return performances.stream()
            .min(Comparator.comparing(Performance::duree))
            .orElse(null);
    }
}