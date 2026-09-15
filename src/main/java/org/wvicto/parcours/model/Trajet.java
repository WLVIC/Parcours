package org.wvicto.parcours.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
        return containsPoint(point, 0.1); // 100m de tolérance
    }
}
