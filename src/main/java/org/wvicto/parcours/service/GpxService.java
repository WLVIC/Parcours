package org.wvicto.parcours.service;

import org.wvicto.parcours.model.GpxParser;
import org.wvicto.parcours.model.Trajet;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

public class GpxService {
    /**
     * Charge un ou plusieurs fichiers GPX et retourne une liste de trajets.
     * @param files Les fichiers GPX à charger.
     * @return Liste des trajets parsés.
     * @throws Exception Si un fichier est invalide.
     */
    public static List<Trajet> chargerTrajets(List<File> files) throws Exception {
        List<Trajet> trajets = new ArrayList<>();
        for (File file : files) {
            trajets.add(GpxParser.parseFile(file)); // Utilise le parseur existant
        }
        return trajets;
    }

    /**
     * Charge tous les fichiers GPX d'un dossier.
     * @param directory Le dossier à scanner.
     * @return Liste des trajets parsés.
     * @throws Exception Si un fichier est invalide.
     */
    public static List<Trajet> chargerDossier(File directory) throws Exception {
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".gpx"));
        return chargerTrajets(List.of(files));
    }
}