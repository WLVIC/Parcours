package org.wvicto.parcours.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.wvicto.parcours.model.PointGpx;
import org.wvicto.parcours.model.PointGpx.SourceAltitude;
import org.wvicto.parcours.model.Trajet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

/**
 * Tests de IgnElevationService contre un faux serveur HTTP lancé sur ta machine : aucun
 * accès à Internet, et donc aucun risque de dépendre du vrai service de l'IGN.
 */
class IgnElevationServiceTest {

    private HttpServer serveur;
    private final List<String> methodesRecues = new ArrayList<>();
    private final List<JsonObject> corpsRecus = new ArrayList<>();

    @AfterEach
    void arreterLeServeur() {
        if (serveur != null) {
            serveur.stop(0);
        }
    }

    /**
     * Lance un faux serveur qui répond toujours avec le statut donné, et un corps fabriqué
     * à partir du corps JSON reçu. Renvoie un service branché sur ce faux serveur.
     */
    private IgnElevationService serviceAvecReponse(int statut, Function<JsonObject, String> fabriqueReponse)
            throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/elevation.json", echange -> {
            String corps = new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            methodesRecues.add(echange.getRequestMethod());
            JsonObject json = JsonParser.parseString(corps).getAsJsonObject();
            corpsRecus.add(json);

            byte[] reponse = fabriqueReponse.apply(json).getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().add("Content-Type", "application/json");
            echange.sendResponseHeaders(statut, reponse.length);
            echange.getResponseBody().write(reponse);
            echange.close();
        });
        serveur.start();
        return new IgnElevationService("http://127.0.0.1:" + serveur.getAddress().getPort() + "/elevation.json");
    }

    /** Réponse type du service : une altitude (z) égale à 100, 101, 102... pour chaque point reçu. */
    private static String reponseAvecAltitudesCroissantes(JsonObject requete) {
        int nombre = requete.get("lon").getAsString().split("\\|").length;
        StringBuilder sb = new StringBuilder("{\"elevations\":[");
        for (int i = 0; i < nombre; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"lon\":0.0,\"lat\":0.0,\"z\":").append(100 + i).append(",\"acc\":\"Variable\"}");
        }
        return sb.append("]}").toString();
    }

    private static List<PointGpx> pointsSansAltitude(int nombre) {
        List<PointGpx> points = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            points.add(PointGpx.sansAltitude(47.0 + i * 0.0001, 5.0 + i * 0.0001));
        }
        return points;
    }

    // ==================== Cas normaux ====================

    @Test
    void testGetElevations_UnLot_RetourneLesAltitudesDansLOrdre() throws Exception {
        IgnElevationService service = serviceAvecReponse(200, IgnElevationServiceTest::reponseAvecAltitudesCroissantes);

        List<Double> altitudes = service.getElevations(pointsSansAltitude(3));

        assertEquals(List.of(100.0, 101.0, 102.0), altitudes);
    }

    @Test
    void testGetElevations_EnvoieUnPostJsonAvecLesCoordonneesEtLaRessource() throws Exception {
        IgnElevationService service = serviceAvecReponse(200, IgnElevationServiceTest::reponseAvecAltitudesCroissantes);
        List<PointGpx> points = List.of(
            PointGpx.sansAltitude(47.123456, 5.654321),
            PointGpx.sansAltitude(47.2, 5.7));

        service.getElevations(points);

        assertEquals(1, corpsRecus.size());
        assertEquals("POST", methodesRecues.get(0));
        JsonObject corps = corpsRecus.get(0);
        assertEquals("5.654321|5.700000", corps.get("lon").getAsString(), "Longitudes, séparées par |");
        assertEquals("47.123456|47.200000", corps.get("lat").getAsString(), "Latitudes, séparées par |");
        assertEquals("ign_rge_alti_wld", corps.get("resource").getAsString());
    }

    @Test
    void testGetElevations_ListeVide_AucuneRequete() throws Exception {
        IgnElevationService service = serviceAvecReponse(200, IgnElevationServiceTest::reponseAvecAltitudesCroissantes);

        assertTrue(service.getElevations(new ArrayList<>()).isEmpty());
        assertTrue(corpsRecus.isEmpty(), "Pas de point : pas besoin d'interroger le service");
    }

    @Test
    void testGetElevations_PlusDeDeuxMillePoints_DecoupeEnPlusieursLots() throws Exception {
        IgnElevationService service = serviceAvecReponse(200, IgnElevationServiceTest::reponseAvecAltitudesCroissantes);

        List<Double> altitudes = service.getElevations(pointsSansAltitude(2001));

        assertEquals(2001, altitudes.size());
        assertEquals(2, corpsRecus.size(), "2001 points : un lot de 2000 puis un lot de 1");
        assertEquals(2000, corpsRecus.get(0).get("lon").getAsString().split("\\|").length);
        assertEquals(1, corpsRecus.get(1).get("lon").getAsString().split("\\|").length);
    }

    // ==================== Points sans donnée ====================

    @Test
    void testGetElevations_PointSansDonnee_DonneNullEtNonZero() throws Exception {
        String reponse = "{\"elevations\":["
            + "{\"lon\":5.0,\"lat\":47.0,\"z\":250.5,\"acc\":\"Variable\"},"
            + "{\"lon\":5.1,\"lat\":47.1,\"z\":-99999,\"acc\":\"Variable\"},"   // valeur « pas de donnée »
            + "{\"lon\":5.2,\"lat\":47.2,\"acc\":\"Variable\"},"                  // pas de champ z
            + "{\"lon\":5.3,\"lat\":47.3,\"z\":null,\"acc\":\"Variable\"}"        // z nul
            + "]}";
        IgnElevationService service = serviceAvecReponse(200, requete -> reponse);

        List<Double> altitudes = service.getElevations(pointsSansAltitude(4));

        assertEquals(250.5, altitudes.get(0), 1e-9);
        assertNull(altitudes.get(1), "Valeur -99999 : pas une altitude");
        assertNull(altitudes.get(2), "Champ z absent");
        assertNull(altitudes.get(3), "Champ z nul");
    }

    @Test
    void testGetElevations_UneVraieAltitudeNegativeEstConservee() throws Exception {
        // Par exemple près de la mer Morte, ou dans un polder
        String reponse = "{\"elevations\":[{\"lon\":5.0,\"lat\":47.0,\"z\":-3.5,\"acc\":\"Variable\"}]}";
        IgnElevationService service = serviceAvecReponse(200, requete -> reponse);

        assertEquals(-3.5, service.getElevations(pointsSansAltitude(1)).get(0), 1e-9);
    }

    // ==================== Erreurs ====================

    @Test
    void testGetElevations_Http429_MessageExplicite() throws Exception {
        IgnElevationService service = serviceAvecReponse(429, requete -> "Too Many Requests");

        ElevationException erreur = assertThrows(ElevationException.class,
            () -> service.getElevations(pointsSansAltitude(2)));

        assertTrue(erreur.getMessage().contains("429"), erreur.getMessage());
    }

    @Test
    void testGetElevations_ErreurAvecDescription_LaDescriptionEstRepriseDansLeMessage() throws Exception {
        String reponse = "{\"error\":{\"code\":\"BAD_PARAMETER\",\"description\":\"Ressource non acceptee\"}}";
        IgnElevationService service = serviceAvecReponse(400, requete -> reponse);

        ElevationException erreur = assertThrows(ElevationException.class,
            () -> service.getElevations(pointsSansAltitude(2)));

        assertTrue(erreur.getMessage().contains("400"), erreur.getMessage());
        assertTrue(erreur.getMessage().contains("Ressource non acceptee"), erreur.getMessage());
    }

    @Test
    void testGetElevations_NombreDeResultatsIncoherent_Exception() throws Exception {
        String reponse = "{\"elevations\":[{\"lon\":5.0,\"lat\":47.0,\"z\":250.5}]}";
        IgnElevationService service = serviceAvecReponse(200, requete -> reponse);

        // On envoie 3 points, le service n'en renvoie qu'un : pas question de décaler les altitudes
        assertThrows(ElevationException.class, () -> service.getElevations(pointsSansAltitude(3)));
    }

    @Test
    void testGetElevations_ReponseIllisible_Exception() throws Exception {
        IgnElevationService service = serviceAvecReponse(200, requete -> "<html>Maintenance</html>");

        assertThrows(ElevationException.class, () -> service.getElevations(pointsSansAltitude(2)));
    }

    @Test
    void testGetElevations_ServeurInjoignable_Exception() {
        // Aucun serveur n'écoute sur ce port
        IgnElevationService service = new IgnElevationService("http://127.0.0.1:1/elevation.json");

        assertThrows(ElevationException.class, () -> service.getElevations(pointsSansAltitude(2)));
    }

    // ==================== Avec GpxService (la chaîne complète) ====================

    @Test
    void testEnrichirAltitudes_PointSansDonnee_RetombeSurLAltitudeDuGpx() throws Exception {
        String reponse = "{\"elevations\":["
            + "{\"lon\":5.0,\"lat\":47.0,\"z\":300.0},"
            + "{\"lon\":5.1,\"lat\":47.1,\"z\":-99999}"
            + "]}";
        IgnElevationService ign = serviceAvecReponse(200, requete -> reponse);
        GpxService gpxService = new GpxService(ign);

        List<PointGpx> points = List.of(
            new PointGpx(47.0, 5.0, 100, null),
            new PointGpx(47.1, 5.1, 110, null));
        Trajet enrichi = gpxService.enrichirAltitudes(new Trajet("Test", LocalDate.of(2026, 10, 7), points));

        PointGpx premier = enrichi.getPoints().get(0);
        PointGpx second = enrichi.getPoints().get(1);
        assertEquals(300.0, premier.getAltitude(SourceAltitude.MEILLEURE).getAsDouble(), 1e-9);
        assertEquals(100.0, premier.getAltitude(SourceAltitude.GPX).getAsDouble(), 1e-9, "L'altitude GPX est conservée");
        assertTrue(second.getAltitudeExterne().isEmpty(), "Pas de donnée du service : altitude externe absente");
        assertEquals(110.0, second.getAltitude(SourceAltitude.MEILLEURE).getAsDouble(), 1e-9,
            "Sans altitude externe, on retombe sur celle du GPX (et pas sur 0)");
    }
}
