package org.wvicto.parcours.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import org.wvicto.parcours.model.Route;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sauvegarde et chargement des routes de référence (&lt;rte&gt;) au format JSON.
 *
 * Route contient des PointGpx, qui portent un LocalDateTime : Gson ne sait pas
 * sérialiser ce type par réflexion (pas de constructeur sans argument), d'où
 * l'adaptateur dédié ci-dessous (conversion en chaîne ISO-8601 et inversement).
 * En pratique, RouteService ne produit plus de PointGpx avec horodatage — cet
 * adaptateur reste un filet de sécurité, pas une nécessité active.
 */
public class RoutePersistenceService {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>)
            (src, type, ctx) -> src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toString()))
        .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>)
            (json, type, ctx) -> json.isJsonNull() ? null : LocalDateTime.parse(json.getAsString()))
        .create();

    public static void sauvegarder(List<Route> routes, File fichier) throws IOException {
        try (FileWriter writer = new FileWriter(fichier)) {
            GSON.toJson(routes, writer);
        }
    }

    public static List<Route> charger(File fichier) throws IOException {
        if (!fichier.exists()) {
            return new ArrayList<>();
        }
        try (FileReader reader = new FileReader(fichier)) {
            Type listType = new TypeToken<ArrayList<Route>>() {}.getType();
            List<Route> routes = GSON.fromJson(reader, listType);
            return routes != null ? routes : new ArrayList<>();
        }
    }
}