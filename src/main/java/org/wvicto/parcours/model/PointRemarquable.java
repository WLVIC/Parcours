package org.wvicto.parcours.model;

/**
 * Un lieu d'intérêt défini par l'utilisateur (maison, travail, boulangerie, etc.),
 * enregistré par clic-droit sur la carte.
 */
public class PointRemarquable {
    private String nom;
    private String categorie; // libre : "Maison", "Travail", "Commerce"...
    private double latitude;
    private double longitude;

    public PointRemarquable(String nom, String categorie, double latitude, double longitude) {
        this.nom = nom;
        this.categorie = categorie;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    /**
     * Convertit ce point en PointGpx pour réutiliser distanceTo() (formule de Haversine).
     * Altitude à 0 et sans horodatage : ce ne sont pas des points de trace GPS.
     */
    public PointGpx versPointGpx() {
        return new PointGpx(latitude, longitude, 0.0, null);
    }

    public double distanceTo(PointGpx point) {
        return versPointGpx().distanceTo(point);
    }

    @Override
    public String toString() {
        return String.format("📍 %s (%s)", nom, categorie);
    }
}