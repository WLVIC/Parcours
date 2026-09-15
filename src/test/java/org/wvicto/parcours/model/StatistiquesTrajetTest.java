package org.wvicto.parcours.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class StatistiquesTrajetTest {

	@Test
	void testGetDistanceTotaleKm_AvecDeuxPoints() {
	    // Paris (48.8566, 2.3522) et Versailles (48.8049, 2.1204) -> ~17.7 km
	    PointGpx paris = new PointGpx(48.8566, 2.3522, 0, LocalDateTime.now());
	    PointGpx versailles = new PointGpx(48.8049, 2.1204, 0, LocalDateTime.now());
	    Trajet trajet = new Trajet("Paris-Versailles", LocalDate.now(), Arrays.asList(paris, versailles));

	    StatistiquesTrajet stats = trajet.getStatistiques();
	    double distance = stats.getDistanceTotaleKm();

	    // Fourchette élargie pour accepter 17.7 km
	    assertTrue(distance > 17 && distance < 19,
	        "Distance devrait être ~17.7 km, mais était : " + distance);
	}

    @Test
    void testGetDistanceTotaleKm_AvecUnSeulPoint() {
        PointGpx point = new PointGpx(48.8566, 2.3522, 0, LocalDateTime.now());
        Trajet trajet = new Trajet("Trajet court", LocalDate.now(), Arrays.asList(point));

        StatistiquesTrajet stats = trajet.getStatistiques();
        assertEquals(0.0, stats.getDistanceTotaleKm(), 0.001);
    }

    @Test
    void testGetDistanceTotaleKm_AucunPoint() {
        Trajet trajet = new Trajet("Trajet vide", LocalDate.now(), Arrays.asList());
        StatistiquesTrajet stats = trajet.getStatistiques();
        assertEquals(0.0, stats.getDistanceTotaleKm(), 0.001);
    }

    @Test
    void testGetPentesPourcent_Montee() {
        // Deux points avec dénivelé positif : +10m sur ~111m (latitude +0.001° ≈ 111m)
        PointGpx p1 = new PointGpx(48.8566, 2.3522, 100, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.8576, 2.3522, 110, LocalDateTime.now()); // +10m, ~111m horizontal
        Trajet trajet = new Trajet("Montée", LocalDate.now(), Arrays.asList(p1, p2));

        StatistiquesTrajet stats = trajet.getStatistiques();
        List<Double> pentes = stats.getPentesPourcent();

        assertEquals(1, pentes.size());
        assertTrue(pentes.get(0) > 8 && pentes.get(0) < 10,
            "Pente devrait être ~9%, mais était : " + pentes.get(0));
    }

    @Test
    void testGetPentesPourcent_Descente() {
        // Deux points avec dénivelé négatif : -10m
        PointGpx p1 = new PointGpx(48.8566, 2.3522, 110, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.8576, 2.3522, 100, LocalDateTime.now()); // -10m
        Trajet trajet = new Trajet("Descente", LocalDate.now(), Arrays.asList(p1, p2));

        StatistiquesTrajet stats = trajet.getStatistiques();
        List<Double> pentes = stats.getPentesPourcent();

        assertEquals(1, pentes.size());
        assertTrue(pentes.get(0) < -8 && pentes.get(0) > -10,
            "Pente devrait être ~-9%, mais était : " + pentes.get(0));
    }

    @Test
    void testGetPenteMax() {
        PointGpx p1 = new PointGpx(48.8566, 2.3522, 100, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.8576, 2.3522, 110, LocalDateTime.now()); // +10m -> pente ~9%
        PointGpx p3 = new PointGpx(48.8586, 2.3522, 150, LocalDateTime.now()); // +40m sur ~111m -> pente ~36%
        Trajet trajet = new Trajet("Trajet avec montée", LocalDate.now(), Arrays.asList(p1, p2, p3));

        StatistiquesTrajet stats = trajet.getStatistiques();
        double penteMax = stats.getPenteMax();

        assertTrue(penteMax > 35 && penteMax < 37,
            "Pente max devrait être ~36%, mais était : " + penteMax);
    }

    @Test
    void testGetDenivelePositif() {
        PointGpx p1 = new PointGpx(48.8566, 2.3522, 100, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.8576, 2.3522, 150, LocalDateTime.now()); // +50m
        PointGpx p3 = new PointGpx(48.8586, 2.3522, 120, LocalDateTime.now()); // -30m
        PointGpx p4 = new PointGpx(48.8596, 2.3522, 200, LocalDateTime.now()); // +80m
        Trajet trajet = new Trajet("Trajet avec D+", LocalDate.now(), Arrays.asList(p1, p2, p3, p4));

        StatistiquesTrajet stats = trajet.getStatistiques();
        double denivelePositif = stats.getDenivelePositif();

        assertEquals(130.0, denivelePositif, 0.001); // 50 + 80 = 130m
    }

    @Test
    void testGetDeniveleNegatif() {
        PointGpx p1 = new PointGpx(48.8566, 2.3522, 200, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.8576, 2.3522, 150, LocalDateTime.now()); // -50m
        PointGpx p3 = new PointGpx(48.8586, 2.3522, 180, LocalDateTime.now()); // +30m
        PointGpx p4 = new PointGpx(48.8596, 2.3522, 100, LocalDateTime.now()); // -80m
        Trajet trajet = new Trajet("Trajet avec D-", LocalDate.now(), Arrays.asList(p1, p2, p3, p4));

        StatistiquesTrajet stats = trajet.getStatistiques();
        double deniveleNegatif = stats.getDeniveleNegatif();

        assertEquals(130.0, deniveleNegatif, 0.001); // 50 + 80 = 130m
    }

    @Test
    void testGetPenteMoyenne() {
        PointGpx p1 = new PointGpx(48.8566, 2.3522, 100, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.8575, 2.3522, 110, LocalDateTime.now()); // ~100m, +10m
        PointGpx p3 = new PointGpx(48.8585, 2.3522, 105, LocalDateTime.now()); // ~100m, -5m
        Trajet trajet = new Trajet("Trajet test", LocalDate.now(), Arrays.asList(p1, p2, p3));

        StatistiquesTrajet stats = trajet.getStatistiques();
        double penteMoyenne = stats.getPenteMoyenne();

        // Fourchette élargie pour accepter ~2.75%
        assertTrue(penteMoyenne > 2.5 && penteMoyenne < 3.0,
            "Pente moyenne devrait être ~2.75%, mais était : " + penteMoyenne);
    }

    @Test
    void testConstructeur_AvecTrajetNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            new StatistiquesTrajet(null);
        });
    }
}