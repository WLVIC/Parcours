package org.wvicto.parcours.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class TrajetTest {

    private static final LocalDateTime HEURE = LocalDateTime.of(2026, 10, 7, 8, 0, 0);

    /** Trois points sur un même méridien, avec une altitude GPX et une altitude externe chacun. */
    private static Trajet trajetAvecDeuxAltitudes() {
        PointGpx p0 = new PointGpx(48.0, 2.0, 100, HEURE).avecAltitudeExterne(200.0);
        PointGpx p1 = new PointGpx(48.3, 2.0, 110, HEURE.plusSeconds(10)).avecAltitudeExterne(210.0);
        PointGpx p2 = new PointGpx(48.0, 2.0, 120, HEURE.plusSeconds(20)).avecAltitudeExterne(220.0);
        return new Trajet("Test", LocalDate.of(2026, 10, 7), Arrays.asList(p0, p1, p2));
    }

    @Test
    void testLisse_ConserveLesDeuxAltitudesDeChaquePoint() {
        // C'est le bug d'origine : lisse() recréait les points et perdait l'altitude externe.
        List<PointGpx> lisses = trajetAvecDeuxAltitudes().lisse(3).getPoints();

        for (int i = 0; i < 3; i++) {
            assertEquals(100.0 + 10 * i, lisses.get(i).getAltitudeGpx().getAsDouble(), 0.001,
                "Altitude GPX du point " + i);
            assertEquals(200.0 + 10 * i, lisses.get(i).getAltitudeExterne().getAsDouble(), 0.001,
                "Altitude externe du point " + i);
        }
    }

    @Test
    void testLisse_ConserveLHorodatage() {
        List<PointGpx> lisses = trajetAvecDeuxAltitudes().lisse(3).getPoints();

        assertEquals(HEURE, lisses.get(0).getTimestamp());
        assertEquals(HEURE.plusSeconds(10), lisses.get(1).getTimestamp());
        assertEquals(HEURE.plusSeconds(20), lisses.get(2).getTimestamp());
    }

    @Test
    void testLisse_MoyenneLesPositionsSurLaFenetre() {
        List<PointGpx> lisses = trajetAvecDeuxAltitudes().lisse(3).getPoints();

        // Latitudes d'origine : 48.0 ; 48.3 ; 48.0, fenêtre de 3 points, rétrécie aux extrémités
        assertEquals(48.15, lisses.get(0).getLatitude(), 1e-9);  // moyenne des points 0 et 1
        assertEquals(48.1, lisses.get(1).getLatitude(), 1e-9);   // moyenne des points 0, 1 et 2
        assertEquals(48.15, lisses.get(2).getLatitude(), 1e-9);  // moyenne des points 1 et 2
    }

    @Test
    void testLisse_NeModifiePasLeTrajetDOrigine() {
        Trajet original = trajetAvecDeuxAltitudes();

        original.lisse(3);

        assertEquals(48.3, original.getPoints().get(1).getLatitude(), 1e-9);
    }

    @Test
    void testLisse_GardeLeMemeNombreDePointsEtAjouteLisseAuNom() {
        Trajet lisse = trajetAvecDeuxAltitudes().lisse(3);

        assertEquals(3, lisse.getPoints().size());
        assertEquals("Test (lissé)", lisse.getNom());
    }

    @Test
    void testLisse_ConserveLAbsenceDAltitude() {
        PointGpx p0 = PointGpx.sansAltitude(48.0, 2.0, HEURE);
        PointGpx p1 = PointGpx.sansAltitude(48.001, 2.0, HEURE.plusSeconds(10));
        Trajet trajet = new Trajet("Sans altitude", LocalDate.of(2026, 10, 7), new ArrayList<>(Arrays.asList(p0, p1)));

        for (PointGpx point : trajet.lisse(3).getPoints()) {
            assertTrue(point.getAltitudeGpx().isEmpty(), "Le lissage ne doit pas inventer d'altitude GPX");
            assertTrue(point.getAltitudeExterne().isEmpty(), "Ni d'altitude externe");
        }
    }
}