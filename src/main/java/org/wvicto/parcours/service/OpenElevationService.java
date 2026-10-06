package org.wvicto.parcours.service;

import org.wvicto.parcours.model.PointGpx;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OpenElevationService implements ElevationService {

    private static final String API_URL = "https://api.open-elevation.com/api/v1/lookup";
    private static final int BATCH_SIZE = 500; // Limite recommandée par Open-Elevation

    @Override
    public List<Double> getElevations(List<PointGpx> points) throws Exception {
        List<Double> elevations = new ArrayList<>(points.size());
        
        // Traiter les points par lots
        for (int i = 0; i < points.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, points.size());
            List<PointGpx> batch = points.subList(i, end);
            List<Double> batchElevations = fetchBatchElevations(batch);
            elevations.addAll(batchElevations);
        }
        
        return elevations;
    }

    private List<Double> fetchBatchElevations(List<PointGpx> batch) throws Exception {
        JsonArray locations = new JsonArray();
        for (PointGpx point : batch) {
            JsonObject location = new JsonObject();
            location.addProperty("latitude", point.getLatitude());
            location.addProperty("longitude", point.getLongitude());
            locations.add(location);
        }

        JsonObject requestBody = new JsonObject();
        requestBody.add("locations", locations);

        String response = sendPostRequest(API_URL, requestBody.toString());
        return parseResponse(response, batch.size());
    }

    private String sendPostRequest(String url, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        try (OutputStream outputStream = connection.getOutputStream()) {
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            outputStream.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("Erreur HTTP " + responseCode + " : " + connection.getResponseMessage());
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private List<Double> parseResponse(String jsonResponse, int expectedCount) {
        List<Double> elevations = new ArrayList<>(expectedCount);
        JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();
        JsonArray results = response.getAsJsonArray("results");

        for (JsonElement element : results) {
            JsonObject result = element.getAsJsonObject();
            if (result.has("elevation") && !result.get("elevation").isJsonNull()) {
                elevations.add(result.get("elevation").getAsDouble());
            } else {
                elevations.add(0.0);
            }
        }
        return elevations;
    }
}