package org.wvicto.parcours.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.wvicto.parcours.model.PointRemarquable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Sauvegarde et chargement des points remarquables au format JSON.
 */
public class PointRemarquableService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void sauvegarder(List<PointRemarquable> points, File fichier) throws IOException {
        try (FileWriter writer = new FileWriter(fichier)) {
            GSON.toJson(points, writer);
        }
    }

    public static List<PointRemarquable> charger(File fichier) throws IOException {
        if (!fichier.exists()) {
            return new ArrayList<>();
        }
        try (FileReader reader = new FileReader(fichier)) {
            Type listType = new TypeToken<ArrayList<PointRemarquable>>() {}.getType();
            List<PointRemarquable> points = GSON.fromJson(reader, listType);
            return points != null ? points : new ArrayList<>();
        }
    }
}