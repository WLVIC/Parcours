package org.wvicto.parcours.model;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.wvicto.parcours.util.Constants;

public class Trajet {
    private String nom;
    private LocalDate date;
    private List<PointGpx> points;

    /**
     * Fichier GPX d'où vient ce trajet, ou null s'il n'en vient pas (trajet lissé, sous-trajet
     * extrait, créé dans l'application). Seul un trajet qui a un fichier source peut être
     * enregistré en copie enrichie : la copie reprend les métadonnées de ce fichier.
     */
    private File fichierSource;

    /** Commentaire de la trace (balise trk/cmt du GPX). Vide s'il n'y en a pas, jamais null. */
    private String commentaire = "";

    /** Marque lue dans le fichier source si c'est lui-même une copie enrichie, sinon null. */
    private Enrichissement enrichissement;

    public Trajet(String nom, LocalDate date, List<PointGpx> points) {
        this.nom = nom;
        this.date = date;
        this.points = new ArrayList<>(points); // Copie défensive
    }

    /**
     * Même trajet avec d'autres points : conserve le nom, la date, le fichier source, le
     * commentaire et la marque. À utiliser quand les points changent mais que c'est toujours
     * le même enregistrement (par exemple après la récupération des altitudes).
     * Ne pas l'utiliser quand les positions sont transformées (lissage) : le résultat ne
     * serait plus l'enregistrement du fichier source.
     */
    public Trajet avecPoints(List<PointGpx> nouveauxPoints) {
        Trajet copie = new Trajet(nom, date, nouveauxPoints);
        copie.fichierSource = fichierSource;
        copie.commentaire = commentaire;
        copie.enrichissement = enrichissement;
        return copie;
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

    // Méthode pour obtenir un sous-trajet (sans modifier l'original) ; le sous-trajet n'a pas
    // de fichier source : ce n'est pas l'enregistrement du fichier, seulement une portion
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
     *
     * Le trajet lissé n'a volontairement pas de fichier source : ses positions sont
     * déplacées, il ne peut donc jamais être enregistré à la place de la trace réelle.
     */
    public Trajet lisse(int tailleFenetre) {
        int demiFenetre = tailleFenetre / 2;
        List<PointGpx> lisses = new ArrayList<>(points.size());

        for (int i = 0; i < points.size(); i++) {
            int debut = Math.max(0, i - demiFenetre);
            int fin = Math.min(points.size() - 1, i + demiFenetre);

            double sommeLat = 0, sommeLon = 0;
            for (int j = debut; j <= fin; j++) {
                sommeLat += points.get(j).getLatitude();
                sommeLon += points.get(j).getLongitude();
            }
            int n = fin - debut + 1;

            // avecPosition conserve les deux altitudes et l'horodatage du point d'origine
            lisses.add(points.get(i).avecPosition(sommeLat / n, sommeLon / n));
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

    public File getFichierSource() { return fichierSource; }
    public void setFichierSource(File fichierSource) { this.fichierSource = fichierSource; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire == null ? "" : commentaire; }

    /** Marque du fichier source s'il s'agit d'une copie enrichie, null sinon. */
    public Enrichissement getEnrichissement() { return enrichissement; }

    /** État déduit du contenu du trajet : marque présente, fichier connu, ou ni l'un ni l'autre. */
    public EtatEnrichissement getEtatEnrichissement() {
        if (enrichissement != null) {
            return EtatEnrichissement.ENRICHI;
        }
        return fichierSource != null ? EtatEnrichissement.A_ENRICHIR : EtatEnrichissement.SANS_FICHIER;
    }
    public void setEnrichissement(Enrichissement enrichissement) { this.enrichissement = enrichissement; }

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