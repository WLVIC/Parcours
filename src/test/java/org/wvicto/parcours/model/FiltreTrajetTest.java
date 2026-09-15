package org.wvicto.parcours.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class FiltreTrajetTest {

    @Test
    void testFiltrerParProximite_TrajetPassePresDuPoint() {
        // Créer un trajet avec un point proche de (48.8566, 2.3522)
        PointGpx p1 = new PointGpx(48.8566, 2.3522, 0, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.8567, 2.3523, 0, LocalDateTime.now()); // ~150m de p1
        Trajet trajet1 = new Trajet("Trajet proche", LocalDate.now(), Arrays.asList(p1, p2));

        // Créer un trajet loin de (48.8566, 2.3522)
        PointGpx p3 = new PointGpx(48.8, 2.3, 0, LocalDateTime.now());
        PointGpx p4 = new PointGpx(48.8001, 2.3001, 0, LocalDateTime.now());
        Trajet trajet2 = new Trajet("Trajet loin", LocalDate.now(), Arrays.asList(p3, p4));

        List<Trajet> trajets = Arrays.asList(trajet1, trajet2);
        PointGpx pointRef = new PointGpx(48.8566, 2.3522, 0, null);

        List<Trajet> trajetsFiltres = FiltreTrajet.filtrerParProximite(trajets, pointRef, 0.5); // 500m de rayon

        assertEquals(1, trajetsFiltres.size());
        assertEquals("Trajet proche", trajetsFiltres.get(0).getNom());
    }

    @Test
    void testFiltrerParProximite_AucunTrajetProche() {
        PointGpx p1 = new PointGpx(48.8, 2.3, 0, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.8001, 2.3001, 0, LocalDateTime.now());
        Trajet trajet = new Trajet("Trajet loin", LocalDate.now(), Arrays.asList(p1, p2));

        List<Trajet> trajets = Arrays.asList(trajet);
        PointGpx pointRef = new PointGpx(48.8566, 2.3522, 0, null); // Très loin

        List<Trajet> trajetsFiltres = FiltreTrajet.filtrerParProximite(trajets, pointRef, 0.1); // 100m de rayon

        assertTrue(trajetsFiltres.isEmpty());
    }

    @Test
    void testFiltrerParZone_TrajetDansZone() {
        // Trajet dans la zone (48.85-48.86, 2.35-2.36)
        PointGpx p1 = new PointGpx(48.855, 2.355, 0, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.856, 2.356, 0, LocalDateTime.now());
        Trajet trajet1 = new Trajet("Trajet dans zone", LocalDate.now(), Arrays.asList(p1, p2));

        // Trajet hors zone
        PointGpx p3 = new PointGpx(48.8, 2.3, 0, LocalDateTime.now());
        PointGpx p4 = new PointGpx(48.801, 2.301, 0, LocalDateTime.now());
        Trajet trajet2 = new Trajet("Trajet hors zone", LocalDate.now(), Arrays.asList(p3, p4));

        List<Trajet> trajets = Arrays.asList(trajet1, trajet2);
        PointGpx coinNO = new PointGpx(48.86, 2.35, 0, null); // Nord-Ouest
        PointGpx coinSE = new PointGpx(48.85, 2.36, 0, null); // Sud-Est

        List<Trajet> trajetsFiltres = FiltreTrajet.filtrerParZone(trajets, coinNO, coinSE);

        assertEquals(1, trajetsFiltres.size());
        assertEquals("Trajet dans zone", trajetsFiltres.get(0).getNom());
    }

    @Test
    void testFiltrerParZone_AucunTrajetDansZone() {
        PointGpx p1 = new PointGpx(48.8, 2.3, 0, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.801, 2.301, 0, LocalDateTime.now());
        Trajet trajet = new Trajet("Trajet hors zone", LocalDate.now(), Arrays.asList(p1, p2));

        List<Trajet> trajets = Arrays.asList(trajet);
        PointGpx coinNO = new PointGpx(48.85, 2.35, 0, null);
        PointGpx coinSE = new PointGpx(48.86, 2.36, 0, null);

        List<Trajet> trajetsFiltres = FiltreTrajet.filtrerParZone(trajets, coinNO, coinSE);

        assertTrue(trajetsFiltres.isEmpty());
    }

    @Test
    void testFiltrerParPointPassage() {
        // Trajet passant par (48.8566, 2.3522)
        PointGpx p1 = new PointGpx(48.8566, 2.3522, 0, LocalDateTime.now());
        PointGpx p2 = new PointGpx(48.8567, 2.3523, 0, LocalDateTime.now());
        Trajet trajet1 = new Trajet("Trajet passant", LocalDate.now(), Arrays.asList(p1, p2));

        // Trajet ne passant pas par (48.8566, 2.3522)
        PointGpx p3 = new PointGpx(48.8, 2.3, 0, LocalDateTime.now());
        PointGpx p4 = new PointGpx(48.801, 2.301, 0, LocalDateTime.now());
        Trajet trajet2 = new Trajet("Trajet non passant", LocalDate.now(), Arrays.asList(p3, p4));

        List<Trajet> trajets = Arrays.asList(trajet1, trajet2);
        PointGpx pointPassage = new PointGpx(48.8566, 2.3522, 0, null);

        List<Trajet> trajetsFiltres = FiltreTrajet.filtrerParPointPassage(trajets, pointPassage);

        assertEquals(1, trajetsFiltres.size());
        assertEquals("Trajet passant", trajetsFiltres.get(0).getNom());
    }

    @Test
    void testFiltrerParProximite_AvecNull() {
        List<Trajet> trajets = Arrays.asList();
        assertThrows(IllegalArgumentException.class, () -> {
            FiltreTrajet.filtrerParProximite(trajets, null, 1.0);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            FiltreTrajet.filtrerParProximite(null, new PointGpx(0, 0, 0, null), 1.0);
        });
    }

    @Test
    void testFiltrerParZone_AvecNull() {
        List<Trajet> trajets = Arrays.asList();
        assertThrows(IllegalArgumentException.class, () -> {
            FiltreTrajet.filtrerParZone(trajets, null, new PointGpx(0, 0, 0, null));
        });
        assertThrows(IllegalArgumentException.class, () -> {
            FiltreTrajet.filtrerParZone(trajets, new PointGpx(0, 0, 0, null), null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            FiltreTrajet.filtrerParZone(null, new PointGpx(0, 0, 0, null), new PointGpx(0, 0, 0, null));
        });
    }
}