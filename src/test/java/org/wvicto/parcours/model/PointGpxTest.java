package org.wvicto.parcours.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.wvicto.parcours.model.PointGpx.SourceAltitude;

class PointGpxTest {

    private static final LocalDateTime HEURE = LocalDateTime.of(2026, 10, 7, 8, 0, 0);

    // ==================== Création ====================

    @Test
    void testConstructeur_AltitudeGpxConnue_AltitudeExterneAbsente() {
        PointGpx point = new PointGpx(48.0, 2.0, 100, HEURE);

        assertEquals(100.0, point.getAltitudeGpx().getAsDouble(), 0.001);
        assertTrue(point.getAltitudeExterne().isEmpty(), "Pas d'altitude externe tant qu'on n'en a pas demandé");
    }

    @Test
    void testConstructeur_UneAltitudeEgaleAZeroResteUneVraieAltitude() {
        PointGpx point = new PointGpx(48.0, 2.0, 0, HEURE);

        assertTrue(point.getAltitudeGpx().isPresent(), "0 m est une altitude connue, pas une absence");
        assertEquals(0.0, point.getAltitudeGpx().getAsDouble(), 0.001);
    }

    @Test
    void testSansAltitude_AucuneSourceNeDonneUneAltitude() {
        PointGpx point = PointGpx.sansAltitude(48.0, 2.0);

        for (SourceAltitude source : SourceAltitude.values()) {
            assertTrue(point.getAltitude(source).isEmpty(), "Source " + source + " devrait être vide");
        }
        assertNull(point.getTimestamp());
    }

    @Test
    void testSansAltitude_ConserveLHorodatage() {
        PointGpx point = PointGpx.sansAltitude(48.0, 2.0, HEURE);

        assertEquals(HEURE, point.getTimestamp());
        assertTrue(point.getAltitude(SourceAltitude.MEILLEURE).isEmpty());
    }

    // ==================== Immuabilité ====================

    @Test
    void testAvecAltitudeExterne_NeModifiePasLePointDOrigine() {
        PointGpx original = new PointGpx(48.0, 2.0, 100, HEURE);

        PointGpx enrichi = original.avecAltitudeExterne(250.0);

        assertNotSame(original, enrichi);
        assertTrue(original.getAltitudeExterne().isEmpty(), "L'original ne doit pas avoir changé");
        assertEquals(250.0, enrichi.getAltitudeExterne().getAsDouble(), 0.001);
    }

    @Test
    void testAvecAltitudeExterne_ConserveLeResteDuPoint() {
        PointGpx original = new PointGpx(48.1, 2.2, 100, HEURE);

        PointGpx enrichi = original.avecAltitudeExterne(250.0);

        assertEquals(48.1, enrichi.getLatitude(), 1e-9);
        assertEquals(2.2, enrichi.getLongitude(), 1e-9);
        assertEquals(100.0, enrichi.getAltitudeGpx().getAsDouble(), 0.001);
        assertEquals(HEURE, enrichi.getTimestamp());
    }

    @Test
    void testAvecAltitudeExterne_Null_RepresenteUneAltitudeAbsente() {
        PointGpx point = new PointGpx(48.0, 2.0, 100, HEURE).avecAltitudeExterne(null);

        assertTrue(point.getAltitudeExterne().isEmpty());
    }

    @Test
    void testAvecPosition_ConserveAltitudesEtHorodatage() {
        PointGpx original = new PointGpx(48.0, 2.0, 100, HEURE).avecAltitudeExterne(250.0);

        PointGpx deplace = original.avecPosition(49.0, 3.0);

        assertEquals(49.0, deplace.getLatitude(), 1e-9);
        assertEquals(3.0, deplace.getLongitude(), 1e-9);
        assertEquals(100.0, deplace.getAltitudeGpx().getAsDouble(), 0.001);
        assertEquals(250.0, deplace.getAltitudeExterne().getAsDouble(), 0.001);
        assertEquals(HEURE, deplace.getTimestamp());
        assertEquals(48.0, original.getLatitude(), 1e-9, "L'original ne doit pas avoir bougé");
    }

    @Test
    void testSansHorodatage_ConserveLesAltitudes() {
        PointGpx original = new PointGpx(48.0, 2.0, 100, HEURE).avecAltitudeExterne(250.0);

        PointGpx copie = original.sansHorodatage();

        assertNull(copie.getTimestamp());
        assertEquals(100.0, copie.getAltitudeGpx().getAsDouble(), 0.001);
        assertEquals(250.0, copie.getAltitudeExterne().getAsDouble(), 0.001);
        assertEquals(HEURE, original.getTimestamp(), "L'original garde son horodatage");
    }

    // ==================== Choix de la source d'altitude ====================

    @Test
    void testGetAltitude_Gpx_IgnoreLAltitudeExterne() {
        PointGpx point = new PointGpx(48.0, 2.0, 100, HEURE).avecAltitudeExterne(250.0);

        assertEquals(100.0, point.getAltitude(SourceAltitude.GPX).getAsDouble(), 0.001);
    }

    @Test
    void testGetAltitude_Externe_IgnoreLAltitudeGpx() {
        PointGpx point = new PointGpx(48.0, 2.0, 100, HEURE).avecAltitudeExterne(250.0);

        assertEquals(250.0, point.getAltitude(SourceAltitude.EXTERNE).getAsDouble(), 0.001);
    }

    @Test
    void testGetAltitude_Externe_VideQuandPasEnrichi() {
        PointGpx point = new PointGpx(48.0, 2.0, 100, HEURE);

        assertTrue(point.getAltitude(SourceAltitude.EXTERNE).isEmpty());
    }

    @Test
    void testGetAltitude_Meilleure_PrefereLExterne() {
        PointGpx point = new PointGpx(48.0, 2.0, 100, HEURE).avecAltitudeExterne(250.0);

        assertEquals(250.0, point.getAltitude(SourceAltitude.MEILLEURE).getAsDouble(), 0.001);
    }

    @Test
    void testGetAltitude_Meilleure_RetombeSurLeGpxSansAltitudeExterne() {
        PointGpx point = new PointGpx(48.0, 2.0, 100, HEURE);

        assertEquals(100.0, point.getAltitude(SourceAltitude.MEILLEURE).getAsDouble(), 0.001);
    }

    @Test
    void testGetAltitude_Meilleure_RetombeSurLeGpxSiLeServiceNAPasRepondu() {
        PointGpx point = new PointGpx(48.0, 2.0, 100, HEURE).avecAltitudeExterne(null);

        assertEquals(100.0, point.getAltitude(SourceAltitude.MEILLEURE).getAsDouble(), 0.001);
    }

    // ==================== Interpolation ====================

    @Test
    void testInterpoleVers_InterpoleLaPositionEtLesAltitudes() {
        PointGpx debut = new PointGpx(48.0, 2.0, 100, HEURE).avecAltitudeExterne(300.0);
        PointGpx fin = new PointGpx(48.004, 2.008, 200, HEURE).avecAltitudeExterne(500.0);

        PointGpx milieu = debut.interpoleVers(fin, 0.25);

        assertEquals(48.001, milieu.getLatitude(), 1e-9);
        assertEquals(2.002, milieu.getLongitude(), 1e-9);
        assertEquals(125.0, milieu.getAltitudeGpx().getAsDouble(), 0.001);
        assertEquals(350.0, milieu.getAltitudeExterne().getAsDouble(), 0.001);
        assertNull(milieu.getTimestamp(), "Un point interpolé n'a pas d'horodatage");
    }

    @Test
    void testInterpoleVers_AltitudeAbsenteAUneExtremite_ResteAbsente() {
        PointGpx debut = PointGpx.sansAltitude(48.0, 2.0);
        PointGpx fin = new PointGpx(48.002, 2.002, 200, HEURE);

        PointGpx milieu = debut.interpoleVers(fin, 0.5);

        assertTrue(milieu.getAltitude(SourceAltitude.MEILLEURE).isEmpty(),
            "On n'invente pas une altitude quand une extrémité n'en a pas");
        assertEquals(48.001, milieu.getLatitude(), 1e-9, "La position, elle, est bien interpolée");
    }

    @Test
    void testInterpoleVers_LesDeuxAltitudesSontIndependantes() {
        PointGpx debut = new PointGpx(48.0, 2.0, 100, HEURE);                       // pas d'altitude externe
        PointGpx fin = new PointGpx(48.002, 2.002, 200, HEURE).avecAltitudeExterne(500.0);

        PointGpx milieu = debut.interpoleVers(fin, 0.5);

        assertEquals(150.0, milieu.getAltitudeGpx().getAsDouble(), 0.001);
        assertTrue(milieu.getAltitudeExterne().isEmpty(), "L'externe manque au point de départ");
    }

    @Test
    void testInterpoleVers_RatioZeroEtUn_DonnentLesExtremites() {
        PointGpx debut = new PointGpx(48.0, 2.0, 100, HEURE);
        PointGpx fin = new PointGpx(48.002, 2.002, 200, HEURE);

        assertEquals(100.0, debut.interpoleVers(fin, 0.0).getAltitudeGpx().getAsDouble(), 0.001);
        assertEquals(200.0, debut.interpoleVers(fin, 1.0).getAltitudeGpx().getAsDouble(), 0.001);
    }
}