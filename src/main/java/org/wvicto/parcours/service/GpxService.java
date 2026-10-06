package org.wvicto.parcours.service;

import org.wvicto.parcours.model.GpxParser;
import org.wvicto.parcours.model.PointGpx;
import org.wvicto.parcours.model.Trajet;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

public class GpxService {
    private final ElevationService elevationService;

    public GpxService(ElevationService elevationService) {
        this.elevationService = elevationService;
    }

    public GpxService() {
        this(new OpenElevationService());
    }

    /**
     * Enrichit les points d'un trajet avec des altitudes via le service d'élévation.
     * @param trajet Le trajet à enrichir.
     * @return Un nouveau trajet avec les altitudes mises à jour.
     * @throws Exception Si la récupération des altitudes échoue.
     */
    public Trajet enrichirAltitudes(Trajet trajet) throws Exception {
        List<PointGpx> points = trajet.getPoints();
        List<Double> elevations = elevationService.getElevations(points);

        List<PointGpx> enrichedPoints = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            PointGpx point = points.get(i);
            PointGpx enrichedPoint = new PointGpx(
                point.getLatitude(),
                point.getLongitude(),
                point.getAltitude(),          // Altitude originale conservée
                point.getTimestamp()
            );
            if (i < elevations.size()) {
                enrichedPoint.setAltitudeEnrichie(elevations.get(i));  // Altitude enrichie
            } else {
                enrichedPoint.setAltitudeEnrichie(0.0);
            }
            enrichedPoints.add(enrichedPoint);
        }
        return new Trajet(trajet.getNom(), trajet.getDate(), enrichedPoints);
    }

    // Méthodes statiques pour la compatibilité descendante (sans injection de dépendance)
    public static List<Trajet> chargerTrajets(List<File> files) throws Exception {
        List<Trajet> trajets = new ArrayList<>();
        for (File file : files) {
            trajets.add(GpxParser.parseFile(file));
        }
        return trajets;
    }

    public static List<Trajet> chargerDossier(File directory) throws Exception {
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".gpx"));
        return chargerTrajets(List.of(files));
    }
}