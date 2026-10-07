package org.wvicto.parcours.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.wvicto.parcours.model.PointGpx.SourceAltitude;
import org.wvicto.parcours.model.ProfilTrajet.PointProfil;

class ProfilTrajetTest {

    private static final LocalDateTime HEURE = LocalDateTime.of(2026, 10, 7, 8, 0, 0);

    /**
     * Construit un trajet rectiligne : un point tous les 0,0005° de latitude (environ 55,6 m),
     * toutes les 10 secondes. Une valeur null dans la liste donne un point sans altitude GPX.
     */
    private static List<PointGpx> points(Double... altitudesGpx) {
        List<PointGpx> points = new ArrayList<>();
        for (int i = 0; i < altitudesGpx.length; i++) {
            double latitude = 48.0 + i * 0.0005;
            LocalDateTime heure = HEURE.plusSeconds(10L * i);
            points.add(altitudesGpx[i] == null
                ? PointGpx.sansAltitude(latitude, 2.0, heure)
                : new PointGpx(latitude, 2.0, altitudesGpx[i], heure));
        }
        return points;
    }

    private static Trajet trajet(List<PointGpx> points) {
        return new Trajet("Test", LocalDate.of(2026, 10, 7), points);
    }

    private static Trajet trajet(Double... altitudesGpx) {
        return trajet(points(altitudesGpx));
    }

    // ==================== Cas de base ====================

    @Test
    void testCalculer_TrajetVide_DonneUnProfilVide() {
        assertTrue(new ProfilTrajet(trajet(new ArrayList<>())).calculer().isEmpty());
    }

    @Test
    void testCalculer_PremierPoint_DistanceEtPenteNulles() {
        List<PointProfil> profil = new ProfilTrajet(trajet(100.0, 101.0)).calculer();

        assertEquals(0.0, profil.get(0).distanceKm(), 1e-9);
        assertEquals(0.0, profil.get(0).pentePourcent(), 1e-9);
        assertEquals(100.0, profil.get(0).altitude(), 1e-9);
    }

    @Test
    void testCalculer_AltitudesConnues_CalculeDistanceEtPente() {
        List<PointProfil> profil = new ProfilTrajet(trajet(100.0, 101.0)).calculer();

        // 1 m de dénivelé sur ~55,6 m : environ 1,8 %
        assertEquals(0.0556, profil.get(1).distanceKm(), 0.001);
        assertEquals(1.8, profil.get(1).pentePourcent(), 0.05);
        assertEquals(101.0, profil.get(1).altitude(), 1e-9);
    }

    // ==================== Altitude absente ====================

    @Test
    void testCalculer_PointSansAltitude_AltitudeNaN() {
        List<PointProfil> profil = new ProfilTrajet(trajet(100.0, 101.0, null, 103.0, 104.0)).calculer();

        assertTrue(Double.isNaN(profil.get(2).altitude()), "Le point 2 n'a pas d'altitude");
        assertEquals(101.0, profil.get(1).altitude(), 1e-9);
        assertEquals(103.0, profil.get(3).altitude(), 1e-9);
    }

    @Test
    void testCalculer_PointSansAltitude_PenteNaNSurLesDeuxSegmentsAdjacents() {
        List<PointProfil> profil = new ProfilTrajet(trajet(100.0, 101.0, null, 103.0, 104.0)).calculer();

        assertFalse(Double.isNaN(profil.get(1).pentePourcent()), "Segment 0-1 : altitudes connues");
        assertTrue(Double.isNaN(profil.get(2).pentePourcent()), "Segment 1-2 : altitude inconnue au point 2");
        assertTrue(Double.isNaN(profil.get(3).pentePourcent()), "Segment 2-3 : altitude inconnue au point 2");
        assertFalse(Double.isNaN(profil.get(4).pentePourcent()), "Segment 3-4 : altitudes connues");
    }

    @Test
    void testCalculer_PointSansAltitude_VitesseEtDistanceRestentCalculables() {
        List<PointProfil> profil = new ProfilTrajet(trajet(100.0, null, 102.0)).calculer();

        assertEquals(0.1112, profil.get(2).distanceKm(), 0.002);
        // ~55,6 m en 10 s : environ 20 km/h
        assertEquals(20.0, profil.get(1).vitesseKmh(), 0.5);
    }

    // ==================== Source d'altitude ====================

    /** Deux points ayant chacun une altitude GPX (100, 101) et une altitude externe (500, 502). */
    private static Trajet trajetEnrichi() {
        List<PointGpx> points = points(100.0, 101.0);
        points.set(0, points.get(0).avecAltitudeExterne(500.0));
        points.set(1, points.get(1).avecAltitudeExterne(502.0));
        return trajet(points);
    }

    @Test
    void testCalculer_SourceGpx_IgnoreLAltitudeExterne() {
        List<PointProfil> profil = new ProfilTrajet(trajetEnrichi(), SourceAltitude.GPX).calculer();

        assertEquals(100.0, profil.get(0).altitude(), 1e-9);
        assertEquals(101.0, profil.get(1).altitude(), 1e-9);
    }

    @Test
    void testCalculer_SourceExterne_UtiliseLAltitudeExterne() {
        List<PointProfil> profil = new ProfilTrajet(trajetEnrichi(), SourceAltitude.EXTERNE).calculer();

        assertEquals(500.0, profil.get(0).altitude(), 1e-9);
        assertEquals(502.0, profil.get(1).altitude(), 1e-9);
    }

    @Test
    void testCalculer_SourceExterne_SansEnrichissement_ToutesLesAltitudesSontNaN() {
        List<PointProfil> profil = new ProfilTrajet(trajet(100.0, 101.0), SourceAltitude.EXTERNE).calculer();

        for (PointProfil point : profil) {
            assertTrue(Double.isNaN(point.altitude()));
        }
    }

    @Test
    void testCalculer_ParDefaut_PrendLaMeilleureAltitude() {
        List<PointProfil> profil = new ProfilTrajet(trajetEnrichi()).calculer();

        assertEquals(500.0, profil.get(0).altitude(), 1e-9, "L'altitude externe est préférée à celle du GPX");
    }

    @Test
    void testCalculer_ParDefaut_RetombeSurLeGpxSansEnrichissement() {
        List<PointProfil> profil = new ProfilTrajet(trajet(100.0, 101.0)).calculer();

        assertEquals(100.0, profil.get(0).altitude(), 1e-9);
    }

    // ==================== Arguments invalides ====================

    @Test
    void testConstructeur_TrajetNull_Exception() {
        assertThrows(IllegalArgumentException.class, () -> new ProfilTrajet(null));
    }

    @Test
    void testConstructeur_SourceNull_Exception() {
        assertThrows(IllegalArgumentException.class, () -> new ProfilTrajet(trajet(100.0, 101.0), null));
    }

    // ==================== Lissage de la pente ====================

    @Test
    void testLisserPente_UnPointSansAltitudeResteSansAltitude() {
        List<PointProfil> brut = new ProfilTrajet(trajet(100.0, 100.0, null, 100.0, 100.0)).calculer();

        List<PointProfil> lisse = ProfilTrajet.lisserPente(brut, 5);

        assertTrue(Double.isNaN(lisse.get(2).altitude()), "Le lissage ne doit pas boucher le trou");
    }

    @Test
    void testLisserPente_LesPointsVoisinsDuTrouNeSontPasContamines() {
        List<PointProfil> brut = new ProfilTrajet(trajet(100.0, 100.0, null, 100.0, 100.0)).calculer();

        List<PointProfil> lisse = ProfilTrajet.lisserPente(brut, 5);

        // Les valeurs NaN sont ignorées dans la médiane : les points connus restent à 100 m
        for (int i : new int[] {0, 1, 3, 4}) {
            assertEquals(100.0, lisse.get(i).altitude(), 1e-9, "Point " + i);
        }
        assertEquals(0.0, lisse.get(1).pentePourcent(), 1e-9, "Segment 0-1 : plat");
        assertTrue(Double.isNaN(lisse.get(2).pentePourcent()), "Segment 1-2 : touche le trou");
        assertTrue(Double.isNaN(lisse.get(3).pentePourcent()), "Segment 2-3 : touche le trou");
        assertEquals(0.0, lisse.get(4).pentePourcent(), 1e-9, "Segment 3-4 : plat");
    }

    @Test
    void testLisserPente_LaMedianeIgnoreUnPointAberrant() {
        List<PointProfil> brut = new ProfilTrajet(trajet(100.0, 100.0, 500.0, 100.0, 100.0)).calculer();

        List<PointProfil> lisse = ProfilTrajet.lisserPente(brut, 5);

        assertEquals(100.0, lisse.get(2).altitude(), 1e-9, "Le point à 500 m est une aberration");
        assertEquals(0.0, lisse.get(2).pentePourcent(), 1e-9);
        assertEquals(0.0, lisse.get(3).pentePourcent(), 1e-9);
    }

    @Test
    void testLisserPente_TrajetEntierSansAltitude_ToutResteNaN() {
        List<PointProfil> brut = new ProfilTrajet(trajet(null, null, null)).calculer();

        List<PointProfil> lisse = ProfilTrajet.lisserPente(brut, 5);

        for (int i = 1; i < lisse.size(); i++) {
            assertTrue(Double.isNaN(lisse.get(i).altitude()), "Altitude du point " + i);
            assertTrue(Double.isNaN(lisse.get(i).pentePourcent()), "Pente du point " + i);
        }
    }
}