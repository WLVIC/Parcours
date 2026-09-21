package org.wvicto.parcours.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.wvicto.parcours.util.Constants;

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

    // ==================== SÉQUENCES DE POINTS REMARQUABLES ====================

    /**
     * Teste si un trajet passe, DANS L'ORDRE, par chaque point de la portion donnée.
     * Rien n'est exigé entre deux points consécutifs de la séquence : un trajet qui relie
     * "Domicile" et "Travail" correspond quel que soit le chemin emprunté entre les deux.
     * Ajouter des points intermédiaires à la portion permet d'exiger un passage précis
     * (ex: une côte, une variante de trajet passant par un point donné).
     */
    public static boolean correspond(Trajet trajet, PortionRemarquable portion) {
        return trouverIndicesSequence(trajet, portion.getPoints(), portion.getRayonKm()) != null;
    }

    /**
     * Comme correspond(), mais renvoie l'index (dans les points du trajet) de chaque point
     * de la portion trouvé, dans l'ordre. Renvoie null si la portion ne correspond pas.
     * Utile pour extraire la sous-portion exacte du trajet (ex: trajet.sousTrajet(indices[0], indices[indices.length - 1])).
     */
    public static int[] trouverIndicesSequence(Trajet trajet, List<PointRemarquable> points, double rayonKm) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("La liste de points ne peut pas être vide");
        }
        List<PointGpx> cibles = points.stream().map(PointRemarquable::versPointGpx).collect(Collectors.toList());
        return trouverIndicesSequenceGeo(trajet, cibles, rayonKm);
    }

    /**
     * Filtre les trajets qui correspondent à une portion remarquable (séquence de points, dans l'ordre).
     */
    public static List<Trajet> filtrerParPortion(List<Trajet> trajets, PortionRemarquable portion) {
        if (trajets == null || portion == null) {
            throw new IllegalArgumentException("La liste de trajets et la portion ne peuvent pas être null");
        }
        return trajets.stream()
                .filter(t -> correspond(t, portion))
                .collect(Collectors.toList());
    }

    /**
     * Comme filtrerParPortion(), mais renvoie directement les sous-trajets extraits
     * (du premier au dernier point de la séquence trouvée) plutôt que les trajets entiers.
     * Pratique pour isoler la portion "côte" au sein de trajets plus longs.
     */
    public static List<Trajet> extrairePortions(List<Trajet> trajets, PortionRemarquable portion) {
        if (trajets == null || portion == null) {
            throw new IllegalArgumentException("La liste de trajets et la portion ne peuvent pas être null");
        }
        List<Trajet> resultats = new ArrayList<>();
        for (Trajet trajet : trajets) {
            int[] indices = trouverIndicesSequence(trajet, portion.getPoints(), portion.getRayonKm());
            if (indices != null) {
                resultats.add(trajet.sousTrajet(indices[0], indices[indices.length - 1]));
            }
        }
        return resultats;
    }

    // ==================== CORRESPONDANCE AVEC UNE ROUTE (<rte>) ====================

    /**
     * Teste si un trajet (<trk>) suit une route de référence (<rte>) : il doit passer,
     * dans l'ordre, à moins de 'rayonKm' de chacun des waypoints de la route.
     */
    public static boolean correspondRoute(Trajet trajet, Route route, double rayonKm) {
        return trouverIndicesSequenceGeo(trajet, route.getPoints(), rayonKm) != null;
    }

    /**
     * Filtre les trajets qui suivent une route de référence donnée.
     */
    public static List<Trajet> filtrerParRoute(List<Trajet> trajets, Route route, double rayonKm) {
        if (trajets == null || route == null) {
            throw new IllegalArgumentException("La liste de trajets et la route ne peuvent pas être null");
        }
        return trajets.stream()
                .filter(t -> correspondRoute(t, route, rayonKm))
                .collect(Collectors.toList());
    }

    /**
     * Répartit une liste de trajets selon la route de référence qu'ils suivent.
     * Un trajet peut apparaître sous plusieurs routes s'il correspond à plusieurs d'entre elles
     * (ex: deux routes qui partagent leur début) ; il apparaît sous "Aucune correspondance"
     * s'il ne suit aucune des routes fournies.
     * @return une Map ordonnée : nom de la route -> trajets correspondants, plus la clé "Aucune correspondance".
     */
    public static java.util.Map<String, List<Trajet>> classifierParRoutes(
            List<Trajet> trajets, List<Route> routes, double rayonKm) {
        java.util.Map<String, List<Trajet>> resultat = new java.util.LinkedHashMap<>();
        for (Route route : routes) {
            resultat.put(route.getNom(), new ArrayList<>());
        }
        resultat.put("Aucune correspondance", new ArrayList<>());

        for (Trajet trajet : trajets) {
            boolean trouve = false;
            for (Route route : routes) {
                if (correspondRoute(trajet, route, rayonKm)) {
                    resultat.get(route.getNom()).add(trajet);
                    trouve = true;
                }
            }
            if (!trouve) {
                resultat.get("Aucune correspondance").add(trajet);
            }
        }
        return resultat;
    }

    // ==================== ALGORITHME COMMUN ====================

    /**
     * Cœur de l'algorithme de correspondance séquentielle : cherche chaque point de 'cibles',
     * DANS L'ORDRE, dans les points du trajet, chacun à partir de la position où le précédent
     * a été trouvé. Renvoie les index trouvés, ou null si un point de la séquence est introuvable.
     * Utilisé aussi bien pour les points remarquables que pour les waypoints d'une Route.
     */
    private static int[] trouverIndicesSequenceGeo(Trajet trajet, List<PointGpx> cibles, double rayonKm) {
        if (trajet == null || cibles == null || cibles.isEmpty()) {
            throw new IllegalArgumentException("Le trajet et la liste de points cibles ne peuvent pas être vides");
        }
        List<PointGpx> trajetPoints = trajet.getPoints();
        int[] indices = new int[cibles.size()];
        int curseur = 0;

        for (int i = 0; i < cibles.size(); i++) {
            PointGpx cible = cibles.get(i);
            int trouve = -1;
            for (int j = curseur; j < trajetPoints.size(); j++) {
                if (cible.distanceTo(trajetPoints.get(j)) <= rayonKm) {
                    trouve = j;
                    break;
                }
            }
            if (trouve == -1) {
                return null; // ce point de la séquence n'apparaît pas après le précédent
            }
            indices[i] = trouve;
            curseur = trouve;
        }
        return indices;
    }
}