package org.wvicto.parcours.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.wvicto.parcours.model.PointGpx.SourceAltitude;

/**
 * Tests de StatistiquesTrajet liés aux altitudes absentes et au choix de la source
 * d'altitude. (Les cas « normaux » sont dans StatistiquesTrajetTest.)
 */
class StatistiquesTrajetAltitudeTest {

    private static final LocalDateTime HEURE = LocalDateTime.of(2026, 10, 7, 8, 0, 0);

    /**
     * Trajet rectiligne : un point tous les 0,0005° de latitude (environ 55,6 m).
     * Une valeur null donne un point sans altitude GPX.
     */
    private static List<PointGpx> points(Double... altitudesGpx) {
        List<PointGpx> points = new ArrayList<>();
        for (int i = 0; i < altitudesGpx.length; i++) {
            double latitude = 48.0 + i * 0.0005;
            points.add(altitudesGpx[i] == null
                ? PointGpx.sansAltitude(latitude, 2.0, HEURE)
                : new PointGpx(latitude, 2.0, altitudesGpx[i], HEURE));
        }
        return points;
    }

    private static Trajet trajet(List<PointGpx> points) {
        return new Trajet("Test", LocalDate.of(2026, 10, 7), points);
    }

    private static Trajet trajet(Double... altitudesGpx) {
        return trajet(points(altitudesGpx));
    }

    // ==================== Segments sans altitude ====================

    @Test
    void testDenivele_IgnoreLesSegmentsSansAltitude() {
        // Segments : 100->110 (+10), 110->? (ignoré), ?->130 (ignoré), 130->125 (-5)
        StatistiquesTrajet stats = trajet(100.0, 110.0, null, 130.0, 125.0).getStatistiques();

        assertEquals(10.0, stats.getDenivelePositif(), 0.001);
        assertEquals(5.0, stats.getDeniveleNegatif(), 0.001);
    }

    @Test
    void testPentes_ListeAligneeSurLesSegments_NaNPourLesSegmentsSansAltitude() {
        StatistiquesTrajet stats = trajet(100.0, 110.0, null, 130.0, 125.0).getStatistiques();

        List<Double> pentes = stats.getPentesPourcent();

        assertEquals(4, pentes.size(), "Un segment de moins que de points");
        assertFalse(Double.isNaN(pentes.get(0)));
        assertTrue(Double.isNaN(pentes.get(1)));
        assertTrue(Double.isNaN(pentes.get(2)));
        assertFalse(Double.isNaN(pentes.get(3)));
    }

    @Test
    void testPenteMaxEtMoyenne_IgnorentLesSegmentsSansAltitude() {
        // Seuls les segments 0-1 (+10 m, ~18 %) et 3-4 (-5 m, ~-9 %) comptent
        StatistiquesTrajet stats = trajet(100.0, 110.0, null, 130.0, 125.0).getStatistiques();

        assertEquals(18.0, stats.getPenteMax(), 0.2);
        assertEquals(4.5, stats.getPenteMoyenne(), 0.2, "Moyenne de ~18 % et ~-9 %");
    }

    // ==================== Aucun segment exploitable ====================

    @Test
    void testTrajetSansAucuneAltitude_StatistiquesNaN() {
        StatistiquesTrajet stats = trajet(null, null, null).getStatistiques();

        assertTrue(Double.isNaN(stats.getPenteMax()));
        assertTrue(Double.isNaN(stats.getPenteMoyenne()));
        assertTrue(Double.isNaN(stats.getDenivelePositif()));
        assertTrue(Double.isNaN(stats.getDeniveleNegatif()));
    }

    @Test
    void testTrajetSansAucuneAltitude_LaDistanceResteCalculee() {
        StatistiquesTrajet stats = trajet(null, null, null).getStatistiques();

        assertEquals(0.1112, stats.getDistanceTotaleKm(), 0.002);
    }

    @Test
    void testAltitudesUneUneSurDeux_AucunSegmentExploitable() {
        StatistiquesTrajet stats = trajet(100.0, null, 120.0, null).getStatistiques();

        assertTrue(Double.isNaN(stats.getDenivelePositif()),
            "Chaque segment a une extrémité sans altitude : rien à compter");
    }

    // ==================== Source d'altitude ====================

    /** Deux points : altitude GPX 100 puis 110, altitude externe 100 puis 150. */
    private static Trajet trajetEnrichi() {
        List<PointGpx> points = points(100.0, 110.0);
        points.set(0, points.get(0).avecAltitudeExterne(100.0));
        points.set(1, points.get(1).avecAltitudeExterne(150.0));
        return trajet(points);
    }

    @Test
    void testDenivele_SourceGpx() {
        StatistiquesTrajet stats = new StatistiquesTrajet(trajetEnrichi(), SourceAltitude.GPX);

        assertEquals(10.0, stats.getDenivelePositif(), 0.001);
    }

    @Test
    void testDenivele_SourceExterne() {
        StatistiquesTrajet stats = new StatistiquesTrajet(trajetEnrichi(), SourceAltitude.EXTERNE);

        assertEquals(50.0, stats.getDenivelePositif(), 0.001);
    }

    @Test
    void testDenivele_SourceExterne_SansEnrichissement_NaN() {
        StatistiquesTrajet stats = new StatistiquesTrajet(trajet(100.0, 110.0), SourceAltitude.EXTERNE);

        assertTrue(Double.isNaN(stats.getDenivelePositif()));
    }

    @Test
    void testDenivele_ParDefaut_PrendLaMeilleureAltitude() {
        // Même choix que le graphique (ProfilTrajet) : les deux affichages restent cohérents
        assertEquals(50.0, trajetEnrichi().getStatistiques().getDenivelePositif(), 0.001);
    }

    @Test
    void testDenivele_ParDefaut_RetombeSurLeGpxSansEnrichissement() {
        assertEquals(10.0, trajet(100.0, 110.0).getStatistiques().getDenivelePositif(), 0.001);
    }

    // ==================== Arguments invalides ====================

    @Test
    void testConstructeur_SourceNull_Exception() {
        assertThrows(IllegalArgumentException.class,
            () -> new StatistiquesTrajet(trajet(100.0, 110.0), null));
    }
}