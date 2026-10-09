package org.wvicto.parcours.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.wvicto.parcours.model.PointGpx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Récupère les altitudes auprès du service d'altimétrie de l'IGN (Géoplateforme).
 *
 * Les points sont envoyés par lots dans une requête POST (au format JSON) : un trajet
 * tient le plus souvent en un ou deux lots. Le service limite le débit à 5 requêtes par
 * seconde et par adresse IP ; on laisse donc une courte pause entre deux lots.
 *
 * Un point pour lequel le service n'a pas de donnée reçoit l'altitude null (et non 0) :
 * PointGpx.avecAltitudeExterne(null) la traite comme « altitude externe absente ».
 *
 * Toutes les erreurs (réseau, service surchargé, réponse incohérente) sont signalées par
 * une ElevationException dont le message peut être affiché tel quel.
 */
public class IgnElevationService implements ElevationService {

    private static final String URL_PAR_DEFAUT =
        "https://data.geopf.fr/altimetrie/1.0/calcul/alti/rest/elevation.json";
    private static final String RESSOURCE = "ign_rge_alti_wld";

    /** Nombre de points par requête (le service en accepte davantage : on reste prudent). */
    private static final int TAILLE_LOT = 2000;
    private static final long PAUSE_ENTRE_LOTS_MS = 250;
    private static final Duration DELAI_CONNEXION = Duration.ofSeconds(10);
    private static final Duration DELAI_REPONSE = Duration.ofSeconds(30);

    /**
     * Par précaution : une valeur en dessous de ce seuil (aucun point habité ou visité à
     * pied n'est à moins de 1000 m sous le niveau de la mer) est une valeur « pas de
     * donnée » du service, pas une vraie altitude.
     */
    private static final double ALTITUDE_MINIMALE_VRAISEMBLABLE = -1000.0;

    private final String urlApi;
    private final HttpClient client;

    public IgnElevationService() {
        this(URL_PAR_DEFAUT);
    }

    /** Permet de viser un autre serveur (par exemple un faux serveur pour les tests). */
    public IgnElevationService(String urlApi) {
        this.urlApi = urlApi;
        this.client = HttpClient.newBuilder().connectTimeout(DELAI_CONNEXION).build();
    }

    @Override
    public List<Double> getElevations(List<PointGpx> points) throws ElevationException {
        List<Double> altitudes = new ArrayList<>(points.size());
        for (int debut = 0; debut < points.size(); debut += TAILLE_LOT) {
            if (debut > 0) {
                pauseEntreLots();
            }
            List<PointGpx> lot = points.subList(debut, Math.min(debut + TAILLE_LOT, points.size()));
            altitudes.addAll(interrogerLot(lot));
        }
        return altitudes;
    }

    private List<Double> interrogerLot(List<PointGpx> lot) throws ElevationException {
        HttpRequest requete = HttpRequest.newBuilder(URI.create(urlApi))
            .timeout(DELAI_REPONSE)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(construireCorps(lot)))
            .build();

        HttpResponse<String> reponse;
        try {
            reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ElevationException("Récupération des altitudes interrompue", e);
        } catch (IOException e) {
            throw new ElevationException(
                "Service d'altimétrie de l'IGN injoignable (connexion ou délai dépassé) : " + e.getMessage(), e);
        }

        if (reponse.statusCode() != 200) {
            throw new ElevationException(messageErreurHttp(reponse.statusCode(), reponse.body()));
        }
        return lireAltitudes(reponse.body(), lot.size());
    }

    /** Corps JSON de la requête : longitudes et latitudes séparées par « | », dans l'ordre des points. */
    private String construireCorps(List<PointGpx> lot) {
        StringBuilder longitudes = new StringBuilder();
        StringBuilder latitudes = new StringBuilder();
        for (int i = 0; i < lot.size(); i++) {
            if (i > 0) {
                longitudes.append('|');
                latitudes.append('|');
            }
            // Locale.ROOT : toujours un point décimal, jamais une virgule
            longitudes.append(String.format(Locale.ROOT, "%.6f", lot.get(i).getLongitude()));
            latitudes.append(String.format(Locale.ROOT, "%.6f", lot.get(i).getLatitude()));
        }

        JsonObject corps = new JsonObject();
        corps.addProperty("lon", longitudes.toString());
        corps.addProperty("lat", latitudes.toString());
        corps.addProperty("resource", RESSOURCE);
        corps.addProperty("delimiter", "|");
        corps.addProperty("zonly", "false");
        return corps.toString();
    }

    private List<Double> lireAltitudes(String corps, int nombreAttendu) throws ElevationException {
        JsonArray resultats;
        try {
            resultats = JsonParser.parseString(corps).getAsJsonObject().getAsJsonArray("elevations");
        } catch (RuntimeException e) {
            throw new ElevationException("Réponse illisible du service d'altimétrie de l'IGN", e);
        }

        if (resultats == null || resultats.size() != nombreAttendu) {
            int recu = resultats == null ? 0 : resultats.size();
            throw new ElevationException(String.format(
                "Le service d'altimétrie de l'IGN a renvoyé %d altitudes pour %d points", recu, nombreAttendu));
        }

        List<Double> altitudes = new ArrayList<>(nombreAttendu);
        for (JsonElement element : resultats) {
            altitudes.add(lireAltitude(element));
        }
        return altitudes;
    }

    /** Altitude (champ « z ») d'un résultat, ou null si le service n'a pas de donnée pour ce point. */
    private static Double lireAltitude(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonElement z = element.getAsJsonObject().get("z");
        if (z == null || !z.isJsonPrimitive() || !z.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double altitude = z.getAsDouble();
        return altitude < ALTITUDE_MINIMALE_VRAISEMBLABLE ? null : altitude;
    }

    private static String messageErreurHttp(int statut, String corps) {
        if (statut == 429) {
            return "Trop de requêtes vers le service d'altimétrie de l'IGN (HTTP 429) : réessaie dans un instant";
        }
        String detail = "";
        try {
            JsonObject erreur = JsonParser.parseString(corps).getAsJsonObject().getAsJsonObject("error");
            if (erreur != null && erreur.has("description")) {
                detail = " : " + erreur.get("description").getAsString();
            }
        } catch (RuntimeException ignoree) {
            // corps qui n'est pas du JSON : on se contente du code HTTP
        }
        return "Erreur HTTP " + statut + " du service d'altimétrie de l'IGN" + detail;
    }

    private static void pauseEntreLots() throws ElevationException {
        try {
            Thread.sleep(PAUSE_ENTRE_LOTS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ElevationException("Récupération des altitudes interrompue", e);
        }
    }
}