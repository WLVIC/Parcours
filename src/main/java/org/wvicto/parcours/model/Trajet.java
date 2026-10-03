package org.wvicto.parcours.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.wvicto.parcours.util.Constants;

public class Trajet {
    private String nom;
    private LocalDate date;
    private List<PointGpx> points;

    public Trajet(String nom, LocalDate date, List<PointGpx> points) {
        this.nom = nom;
        this.date = date;
        this.points = new ArrayList<>(points); // Copie défensive
    }

    // Méthode pour couper le trajet (modifie l'objet courant)
    public void couperTrajet(int indexDebut, int indexFin) {
        if (indexDebut < 0 || indexFin >= points.size() || indexDebut > indexFin) {
            throw new IllegalArgumentException("Indices invalides");
        }
        this.points = new ArrayList<>(points.subList(indexDebut, indexFin + 1));
    }

    // Méthode pour supprimer le début
    public void supprimerDebut(int nombreDePoints) {
        if (nombreDePoints < 0 || nombreDePoints >= points.size()) {
            throw new IllegalArgumentException("Nombre de points invalide");
        }
        this.points = new ArrayList<>(points.subList(nombreDePoints, points.size()));
    }

    // Méthode pour supprimer la fin
    public void supprimerFin(int nombreDePoints) {
        if (nombreDePoints < 0 || nombreDePoints >= points.size()) {
            throw new IllegalArgumentException("Nombre de points invalide");
        }
        this.points = new ArrayList<>(points.subList(0, points.size() - nombreDePoints));
    }

    // Méthode pour obtenir un sous-trajet (sans modifier l'original)
    public Trajet sousTrajet(int indexDebut, int indexFin) {
        if (indexDebut < 0 || indexFin >= points.size() || indexDebut > indexFin) {
            throw new IllegalArgumentException("Indices invalides");
        }
        List<PointGpx> sousPoints = new ArrayList<>(points.subList(indexDebut, indexFin + 1));
        return new Trajet(this.nom + " (sous-trajet)", this.date, sousPoints);
    }

    /**
     * Renvoie un nouveau Trajet dont les positions (latitude/longitude) sont lissées par
     * barycentre glissant : chaque point est remplacé par le barycentre des points dans
     * une fenêtre de 'tailleFenetre' points autour de lui (rétrécie automatiquement près
     * des extrémités). Réduit la gigue GPS sur la position horizontale — utile pour le
     * tracé affiché et pour tout calcul qui en dépend (distance, vitesse). L'altitude et
     * l'horodatage de chaque point restent ceux du point d'origine à cette position dans
     * la liste (seule la position est lissée ici, pas l'altitude — voir ProfilTrajet pour
     * le lissage de l'altitude, qui répond à un bruit de nature différente).
     */
    public Trajet lisse(int tailleFenetre) {
        int demiFenetre = tailleFenetre / 2;
        List<PointGpx> lisses = new ArrayList<>(points.size());

        for (int i = 0; i < points.size(); i++) {
            int debut = Math.max(0, i - demiFenetre);
            int fin = Math.min(points.size() - 1, i + demiFenetre);
            PointGpx barycentre = PointGpx.barycentre(points.subList(debut, fin + 1));

            PointGpx original = points.get(i);
            lisses.add(new PointGpx(
                barycentre.getLatitude(), barycentre.getLongitude(),
                original.getAltitude(), original.getTimestamp()));
        }

        return new Trajet(this.nom + " (lissé)", this.date, lisses);
    }

    // Getters et Setters
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public List<PointGpx> getPoints() { return new ArrayList<>(points); }
    public void setPoints(List<PointGpx> points) { this.points = new ArrayList<>(points); }
    
    //Accès aux statistiques
    public StatistiquesTrajet getStatistiques() {
        return new StatistiquesTrajet(this);
    }

    @Override
    public String toString() {
        return String.format(
            "🚴 %s - %s (%.1f km, %d points)",
            nom,
            date,
            getStatistiques().getDistanceTotaleKm(),
            points.size()
        );
    }
    
    // ==================== FILTRAGE GÉOGRAPHIQUE ====================
    public boolean containsPoint(PointGpx point, double radiusKm) {
        if (point == null) {
            throw new IllegalArgumentException("Le point ne peut pas être null");
        }
        return points.stream()
                .anyMatch(p -> p.distanceTo(point) <= radiusKm);
    }

    public boolean isWithinBoundingBox(PointGpx coinNordOuest, PointGpx coinSudEst) {
        if (coinNordOuest == null || coinSudEst == null) {
            throw new IllegalArgumentException("Les coins de la boîte ne peuvent pas être null");
        }
        double minLat = Math.min(coinNordOuest.getLatitude(), coinSudEst.getLatitude());
        double maxLat = Math.max(coinNordOuest.getLatitude(), coinSudEst.getLatitude());
        double minLon = Math.min(coinNordOuest.getLongitude(), coinSudEst.getLongitude());
        double maxLon = Math.max(coinNordOuest.getLongitude(), coinSudEst.getLongitude());

        return points.stream()
                .allMatch(p ->
                    p.getLatitude() >= minLat && p.getLatitude() <= maxLat &&
                    p.getLongitude() >= minLon && p.getLongitude() <= maxLon
                );
    }

    public boolean passesThrough(PointGpx point) {
        return containsPoint(point, Constants.DEFAULT_RADIUS_KM); // 100m de tolérance
    }
}