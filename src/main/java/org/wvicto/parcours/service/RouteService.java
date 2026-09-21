package org.wvicto.parcours.service;

import org.wvicto.parcours.model.PointGpx;
import org.wvicto.parcours.model.Route;
import org.wvicto.parcours.model.Trajet;

import java.util.ArrayList;
import java.util.List;

/**
 * Génère une Route (&lt;rte&gt;) à partir d'un Trajet (&lt;trk&gt;) désigné comme "typique"
 * d'un itinéraire, en simplifiant son tracé par l'algorithme de Douglas-Peucker : on ne
 * garde que les points nécessaires pour représenter fidèlement la forme du trajet, à une
 * tolérance donnée.
 */
public class RouteService {

    /**
     * @param trajet le trajet enregistré, désigné comme représentatif de la route
     * @param nom le nom de la route à créer (ex: "Domicile - Travail (via Seine)")
     * @param toleranceKm plus la tolérance est grande, moins la route conservera de points.
     *                     Une valeur de 0.02 à 0.05 km (20 à 50 m) est un bon point de départ.
     */
	public static Route genererDepuisTrajet(Trajet trajet, String nom, double toleranceKm) {
	    List<PointGpx> points = trajet.getPoints();
	    if (points.size() < 2) {
	        throw new IllegalArgumentException("Le trajet doit contenir au moins 2 points");
	    }
	    List<PointGpx> simplifies = new ArrayList<>();
	    simplifies.add(sansHorodatage(points.get(0)));
	    douglasPeucker(points, 0, points.size() - 1, toleranceKm, simplifies);
	    simplifies.add(sansHorodatage(points.get(points.size() - 1)));
	    return new Route(nom, simplifies);
	}
	
	/**
	 * Une Route est un itinéraire de référence intemporel : l'horodatage d'un point
	 * du trajet source (l'instant précis où on y est passé lors de CETTE sortie) n'a
	 * pas de sens une fois le point promu en waypoint de route. On ne garde que la
	 * position et l'altitude (utile pour le profil altimétrique de la route).
	 */
	private static PointGpx sansHorodatage(PointGpx point) {
	    return new PointGpx(point.getLatitude(), point.getLongitude(), point.getAltitude(), null);
	}
	
	/**
     * Simplification récursive de Douglas-Peucker : dans le segment [debut, fin], ne conserve
     * le point le plus éloigné de la ligne droite (debut→fin) que s'il s'en écarte de plus de
     * 'toleranceKm' ; sinon, tous les points intermédiaires sont considérés redondants et ignorés.
     */
    private static void douglasPeucker(List<PointGpx> points, int debut, int fin,
                                        double toleranceKm, List<PointGpx> resultat) {
        double distanceMax = 0;
        int indexMax = -1;

        for (int i = debut + 1; i < fin; i++) {
            double distance = distancePointSegment(points.get(i), points.get(debut), points.get(fin));
            if (distance > distanceMax) {
                distanceMax = distance;
                indexMax = i;
            }
        }

        if (distanceMax > toleranceKm && indexMax != -1) {
            douglasPeucker(points, debut, indexMax, toleranceKm, resultat);
            resultat.add(sansHorodatage(points.get(indexMax)));
            douglasPeucker(points, indexMax, fin, toleranceKm, resultat);
        }
        // sinon : le segment est déjà assez rectiligne, on ne garde pas de point intermédiaire
    }

    /**
     * Distance approximative (en km) entre le point p et le segment [a, b].
     * Le point le plus proche sur le segment est calculé par interpolation linéaire en
     * coordonnées lat/lon (approximation raisonnable à l'échelle d'un trajet local), puis
     * la distance réelle jusqu'à p est calculée avec la formule de Haversine (PointGpx.distanceTo).
     */
    private static double distancePointSegment(PointGpx p, PointGpx a, PointGpx b) {
        double dLat = b.getLatitude() - a.getLatitude();
        double dLon = b.getLongitude() - a.getLongitude();

        if (dLat == 0 && dLon == 0) {
            return p.distanceTo(a);
        }

        double t = ((p.getLatitude() - a.getLatitude()) * dLat + (p.getLongitude() - a.getLongitude()) * dLon)
                / (dLat * dLat + dLon * dLon);
        t = Math.max(0, Math.min(1, t)); // borne le point projeté au segment (pas à la droite infinie)

        PointGpx projection = new PointGpx(a.getLatitude() + t * dLat, a.getLongitude() + t * dLon, 0, null);
        return p.distanceTo(projection);
    }
}