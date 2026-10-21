package org.wvicto.parcours.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.wvicto.parcours.model.Enrichissement;
import org.wvicto.parcours.model.EtatEnrichissement;
import org.wvicto.parcours.model.Trajet;

/**
 * Retrouve, parmi des trajets chargés, les fichiers d'origine dont une copie enrichie est
 * aussi chargée : ils sont « déjà traités » et n'ont plus à s'afficher.
 *
 * Un original est reconnu à son CONTENU, comme le dit la marque de la copie : même taille,
 * puis même empreinte SHA-256 (l'empreinte n'est calculée que si la taille correspond). Son
 * nom et son dossier n'interviennent pas. Conséquences :
 *  - copie supprimée ou non chargée : l'original reste « à enrichir » ;
 *  - original absent : la copie vit seule, rien n'est caché ;
 *  - original modifié depuis l'enrichissement : empreinte différente, il reste « à enrichir ».
 *
 * Deux fichiers d'origine strictement identiques (un doublon) sont cachés ensemble, puisque
 * la copie est la leur à tous les deux.
 */
public final class RapprochementOriginaux {

    private RapprochementOriginaux() {}

    /** Les trajets de la collection qui sont l'original d'une copie enrichie de la même collection. */
    public static List<Trajet> originauxTraites(Collection<Trajet> trajets) {
        List<Trajet> copies = new ArrayList<>();
        List<Trajet> candidats = new ArrayList<>();
        for (Trajet t : trajets) {
            if (t.getEtatEnrichissement() == EtatEnrichissement.ENRICHI) {
                copies.add(t);
            } else if (t.getEtatEnrichissement() == EtatEnrichissement.A_ENRICHIR) {
                candidats.add(t);
            }
        }

        Map<File, String> empreintes = new HashMap<>();   // une empreinte calculée une seule fois
        List<Trajet> traites = new ArrayList<>();
        for (Trajet candidat : candidats) {
            File fichier = candidat.getFichierSource();
            if (!fichier.isFile()) {
                continue;
            }
            for (Trajet copie : copies) {
                Enrichissement marque = copie.getEnrichissement();
                if (fichier.length() != marque.taille()) {
                    continue;
                }
                String empreinte = empreintes.computeIfAbsent(fichier, RapprochementOriginaux::empreinteOuNull);
                if (empreinte != null && empreinte.equalsIgnoreCase(marque.sha256())) {
                    traites.add(candidat);
                    break;
                }
            }
        }
        return traites;
    }

    private static String empreinteOuNull(File fichier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(fichier.toPath())));
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;   // illisible : on ne peut pas dire qu'il est traité
        }
    }
}