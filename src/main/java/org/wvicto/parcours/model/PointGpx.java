package org.wvicto.parcours.model;

import java.time.LocalDateTime;

public class PointGpx {
    private double latitude;
    private double longitude;
    private double altitude;
    private LocalDateTime timestamp;

    public PointGpx(double latitude, double longitude, double altitude, LocalDateTime timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.timestamp = timestamp;
    }

    // Getters et Setters
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format(
            "PointGpx{latitude=%.6f, longitude=%.6f, altitude=%.2f, timestamp=%s}",
            latitude, longitude, altitude, timestamp
        );
    }

    /**
     * Calcule la distance (en kilomètres) entre ce point et un autre point GPS.
     * Utilise la formule de Haversine.
     */
    public double distanceTo(PointGpx other) {
        final int R = 6371; // Rayon de la Terre en km
        double lat1 = Math.toRadians(this.latitude);
        double lon1 = Math.toRadians(this.longitude);
        double lat2 = Math.toRadians(other.latitude);
        double lon2 = Math.toRadians(other.longitude);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}