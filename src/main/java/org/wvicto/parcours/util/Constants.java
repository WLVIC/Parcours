package org.wvicto.parcours.util;

/**
 * Classe pour centraliser les constantes du projet.
 * Permet de modifier les valeurs par défaut en un seul endroit.
 */
public class Constants {
    // Rayon par défaut pour les vérifications de proximité (en km)
    public static final double DEFAULT_RADIUS_KM = 0.025; // 25m

    // Emplacement par défaut du fichier JSON des points remarquables
    public static final String FICHIER_POINTS_REMARQUABLES =
        System.getProperty("user.home") + java.io.File.separator
        + ".parcours" + java.io.File.separator + "points_remarquables.json";

    // Emplacement par défaut du fichier JSON des routes de référence (<rte>)
    public static final String FICHIER_ROUTES =
        System.getProperty("user.home") + java.io.File.separator
        + ".parcours" + java.io.File.separator + "routes.json";

    // Autres constantes utiles (exemples pour plus tard)
    // public static final double MIN_DISTANCE_KM = 0.01; // 10m
    // public static final double MAX_PENTE_POURCENT = 20.0; // 20%
}