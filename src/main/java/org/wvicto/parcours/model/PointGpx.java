package org.wvicto.parcours.model;

import java.time.LocalDateTime;
import java.util.OptionalDouble;

/**
 * Un point géographique, éventuellement horodaté. Immuable : pour « modifier » un point
 * on en crée un nouveau (avecPosition, avecAltitudeExterne).
 *
 * Un point peut porter deux altitudes, chacune pouvant être absente :
 * - l'altitude du fichier GPX (absente pour une trace Cartes IGN, ou un point créé à la main) ;
 * - l'altitude externe, récupérée auprès d'un service d'élévation (absente tant qu'on ne
 *   l'a pas demandée, ou si le service n'a rien renvoyé pour ce point).
 */
public final class PointGpx {

    /** Quelle altitude lire : celle du GPX, celle du service externe, ou la meilleure disponible. */
    public enum SourceAltitude { GPX, EXTERNE, MEILLEURE }

    private final double latitude;
    private final double longitude;
    private final Double altitudeGpx;      // null = absente
    private final Double altitudeExterne;  // null = absente
    private final LocalDateTime timestamp;

    private PointGpx(double latitude, double longitude, Double altitudeGpx,
                     Double altitudeExterne, LocalDateTime timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeGpx = altitudeGpx;
        this.altitudeExterne = altitudeExterne;
        this.timestamp = timestamp;
    }

    /** Point dont l'altitude GPX est connue (cas d'un point lu dans un fichier avec balise ele). */
    public PointGpx(double latitude, double longitude, double altitude, LocalDateTime timestamp) {
        this(latitude, longitude, Double.valueOf(altitude), null, timestamp);
    }

    /** Point sans altitude, horodaté (balise ele absente du fichier GPX). */
    public static PointGpx sansAltitude(double latitude, double longitude, LocalDateTime timestamp) {
        return new PointGpx(latitude, longitude, null, null, timestamp);
    }

    /** Point sans altitude ni horodatage (clic sur la carte, point remarquable, projection...). */
    public static PointGpx sansAltitude(double latitude, double longitude) {
        return sansAltitude(latitude, longitude, null);
    }

    // ==================== Lecture ====================

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public OptionalDouble getAltitudeGpx() {
        return altitudeGpx == null ? OptionalDouble.empty() : OptionalDouble.of(altitudeGpx);
    }

    public OptionalDouble getAltitudeExterne() {
        return altitudeExterne == null ? OptionalDouble.empty() : OptionalDouble.of(altitudeExterne);
    }

    /** Point d'accès unique à l'altitude : tout le reste du code passe par ici. */
    public OptionalDouble getAltitude(SourceAltitude source) {
        return switch (source) {
            case GPX -> getAltitudeGpx();
            case EXTERNE -> getAltitudeExterne();
            case MEILLEURE -> altitudeExterne != null ? getAltitudeExterne() : getAltitudeGpx();
        };
    }

    // ==================== « Modification » (renvoie un nouveau point) ====================

    /** Même point déplacé : conserve les deux altitudes et l'horodatage. */
    public PointGpx avecPosition(double nouvelleLatitude, double nouvelleLongitude) {
        return new PointGpx(nouvelleLatitude, nouvelleLongitude, altitudeGpx, altitudeExterne, timestamp);
    }

    /** Même point avec une altitude externe (null si le service n'a rien renvoyé). */
    public PointGpx avecAltitudeExterne(Double altitude) {
        return new PointGpx(latitude, longitude, altitudeGpx, altitude, timestamp);
    }

    /** Même point sans horodatage (ex : point promu en waypoint de route). */
    public PointGpx sansHorodatage() {
        return new PointGpx(latitude, longitude, altitudeGpx, altitudeExterne, null);
    }

    /**
     * Point situé à la fraction 'ratio' (0 = ce point, 1 = l'autre) du segment qui les relie.
     * Une altitude n'est interpolée que si elle est connue aux deux extrémités, sinon elle
     * reste absente. Le point obtenu n'a pas d'horodatage.
     */
    public PointGpx interpoleVers(PointGpx autre, double ratio) {
        return new PointGpx(
            latitude + ratio * (autre.latitude - latitude),
            longitude + ratio * (autre.longitude - longitude),
            interpoler(altitudeGpx, autre.altitudeGpx, ratio),
            interpoler(altitudeExterne, autre.altitudeExterne, ratio),
            null);
    }

    private static Double interpoler(Double debut, Double fin, double ratio) {
        return (debut == null || fin == null) ? null : debut + ratio * (fin - debut);
    }
    
    @Override
    public String toString() {
        return String.format(
            "PointGpx{latitude=%.6f, longitude=%.6f, altitudeGpx=%s, altitudeExterne=%s, timestamp=%s}",
            latitude, longitude, altitudeGpx, altitudeExterne, timestamp
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