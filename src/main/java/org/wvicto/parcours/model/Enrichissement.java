package org.wvicto.parcours.model;

import java.util.Optional;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Marque portée par un fichier GPX écrit par l'application (une « copie enrichie ») : elle
 * dit quelle application l'a écrit, avec quelle version du format, et quel fichier
 * d'origine il prolonge.
 *
 * Le fichier d'origine est identifié par son contenu (taille et empreinte SHA-256) et non
 * par son nom ou son emplacement : un original renommé ou déplacé est toujours reconnu,
 * et un original modifié ne l'est plus. Le nom sert d'indice de lecture (pour un humain, et
 * pour essayer d'abord le bon fichier) ; il ne contient jamais de chemin.
 *
 * Dans le fichier, la marque est écrite à deux endroits : le texte lisible « enrichi :
 * Parcours » à la fin de metadata/desc, et l'élément parcours:enrichissement dans
 * metadata/extensions, que cette classe sait relire.
 *
 * @param application application qui a écrit le fichier (« Parcours »)
 * @param version     version du niveau d'enrichissement (1 pour commencer)
 * @param source      nom du fichier d'origine, sans chemin
 * @param taille      taille du fichier d'origine, en octets
 * @param sha256      empreinte SHA-256 du fichier d'origine, en hexadécimal minuscule
 */
public record Enrichissement(String application, int version, String source, long taille, String sha256) {

    public static final String APPLICATION = "Parcours";
    public static final int VERSION_COURANTE = 1;

    /** Espace de noms des extensions de l'application (un identifiant, pas une adresse web). */
    public static final String ESPACE_DE_NOMS = "urn:wvicto:parcours:1";
    public static final String PREFIXE = "parcours";

    public static final String BALISE_ENRICHISSEMENT = PREFIXE + ":enrichissement";
    /** Altitudes d'un point : attributs « gpx » (fichier d'origine) et « ign » (service d'altimétrie). */
    public static final String BALISE_ALTITUDES = PREFIXE + ":alt";

    /** Texte lisible ajouté à la fin de metadata/desc. */
    public static final String TEXTE_DESCRIPTION = "enrichi : " + APPLICATION;

    /**
     * Relit la marque dans l'élément racine (gpx) d'un document, ou renvoie un résultat
     * vide s'il n'y en a pas, ou si elle est illisible (attribut manquant ou mal formé).
     */
    public static Optional<Enrichissement> lireDans(Element racine) {
        Element metadata = enfant(racine, "metadata");
        Element extensions = metadata == null ? null : enfant(metadata, "extensions");
        Element marque = extensions == null ? null : enfant(extensions, BALISE_ENRICHISSEMENT);
        if (marque == null) {
            return Optional.empty();
        }
        try {
            String application = marque.getAttribute("application");
            String source = marque.getAttribute("source");
            String sha256 = marque.getAttribute("sha256");
            if (application.isEmpty() || source.isEmpty() || sha256.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Enrichissement(
                application,
                Integer.parseInt(marque.getAttribute("version")),
                source,
                Long.parseLong(marque.getAttribute("taille")),
                sha256));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Premier enfant direct portant ce nom de balise, ou null. */
    private static Element enfant(Element parent, String balise) {
        for (Node noeud = parent.getFirstChild(); noeud != null; noeud = noeud.getNextSibling()) {
            if (noeud.getNodeType() == Node.ELEMENT_NODE && balise.equals(((Element) noeud).getTagName())) {
                return (Element) noeud;
            }
        }
        return null;
    }
}