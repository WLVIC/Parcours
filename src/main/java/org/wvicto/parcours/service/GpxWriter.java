package org.wvicto.parcours.service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.wvicto.parcours.model.Enrichissement;
import org.wvicto.parcours.model.GpxParser;
import org.wvicto.parcours.model.PointGpx;
import org.wvicto.parcours.model.PointGpx.SourceAltitude;
import org.wvicto.parcours.model.Trajet;

/**
 * Écrit une « copie enrichie » du fichier GPX d'un trajet : le fichier d'origine n'est
 * jamais modifié, ni remplacé.
 *
 * Principe : on relit le fichier d'origine comme un document XML, on garde tout ce qui n'est
 * pas la liste des points (métadonnées, créateur, nom, waypoints, extensions du fichier...),
 * et on reconstruit les points de la trace à partir du Trajet tel qu'il est en mémoire. Un
 * trajet dont on a coupé un bout donne ainsi naturellement une copie plus courte.
 *
 * Ce qui est ajouté à la copie :
 * - dans chaque point, ele = la meilleure altitude disponible (la seule que lisent les
 *   autres logiciels), et parcours:alt avec les deux altitudes, attributs gpx (fichier
 *   d'origine) et ign (service d'altimétrie), chacun absent si on ne l'a pas ;
 * - dans metadata : « enrichi : Parcours » à la fin de desc, et l'élément
 *   parcours:enrichissement (application, version, nom, taille et empreinte SHA-256 de
 *   l'original, voir Enrichissement) ;
 * - dans trk : le commentaire du Trajet (cmt), s'il n'est pas vide.
 *
 * Ce qui est perdu : les données propres à chaque point que le modèle ne retient pas
 * (fréquence cardiaque, cadence, température...), et les séparations en plusieurs segments
 * (trkseg) de la trace, regroupés en un seul. Seule la première trace (trk) est réécrite, les
 * suivantes sont recopiées telles quelles.
 *
 * Limites actuelles : seuls les fichiers GPX 1.1 sont pris en charge (metadata et extensions
 * n'existent pas en GPX 1.0).
 *
 * L'écriture est sûre : la copie est écrite sous un nom temporaire, relue (nombre de points
 * et marque) puis seulement renommée. Un fichier existant n'est remplacé que s'il s'agit
 * déjà d'une copie enrichie du même original.
 */
public class GpxWriter {

    private static final String VERSION_GPX_PRISE_EN_CHARGE = "1.1";

    /**
     * Écrit la copie enrichie du trajet dans le dossier donné (créé au besoin), sous le même
     * nom que le fichier d'origine. Renvoie le fichier écrit.
     *
     * @throws EcritureGpxException si la copie ne peut ou ne doit pas être écrite ; l'original
     *                              et tout fichier existant sont alors laissés intacts
     */
    public File ecrireCopieEnrichie(Trajet trajet, File dossierDestination) throws EcritureGpxException {
        File source = trajet.getFichierSource();
        if (source == null || !source.isFile()) {
            throw new EcritureGpxException("ce trajet n'a pas de fichier source (trajet lissé, extrait "
                + "d'un autre, ou fichier introuvable) : une copie enrichie ne peut pas être écrite");
        }
        List<PointGpx> points = trajet.getPoints();
        if (points.isEmpty()) {
            throw new EcritureGpxException("le trajet ne contient aucun point");
        }

        Document document = lireDocument(source);
        Element racine = document.getDocumentElement();
        verifierSource(racine);

        Enrichissement marque = new Enrichissement(Enrichissement.APPLICATION, Enrichissement.VERSION_COURANTE,
            source.getName(), source.length(), empreinteSha256(source));

        retirerTextesBlancs(racine);
        Element trace = premiereTrace(racine);
        ecrireCommentaire(document, trace, trajet.getCommentaire());
        remplacerPoints(document, trace, points);
        marquerMetadonnees(document, racine, marque, points);
        racine.setAttribute("xmlns:" + Enrichissement.PREFIXE, Enrichissement.ESPACE_DE_NOMS);

        return enregistrer(document, source, dossierDestination, points.size(), marque);
    }

    // ==================== Lecture du fichier d'origine ====================

    private static Document lireDocument(File source) throws EcritureGpxException {
        try {
            DocumentBuilderFactory fabrique = DocumentBuilderFactory.newInstance();
            fabrique.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrique.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder constructeur = fabrique.newDocumentBuilder();
            return constructeur.parse(source);
        } catch (Exception e) {
            throw new EcritureGpxException("le fichier d'origine n'a pas pu être relu (" + e.getMessage() + ")", e);
        }
    }

    private static void verifierSource(Element racine) throws EcritureGpxException {
        if (!"gpx".equals(racine.getTagName())) {
            throw new EcritureGpxException("le fichier d'origine n'est pas un fichier GPX");
        }
        if (!VERSION_GPX_PRISE_EN_CHARGE.equals(racine.getAttribute("version"))) {
            throw new EcritureGpxException("seuls les fichiers GPX 1.1 sont pris en charge pour l'instant "
                + "(ce fichier est en version « " + racine.getAttribute("version") + " »)");
        }
        if (Enrichissement.lireDans(racine).isPresent()) {
            throw new EcritureGpxException("ce fichier est déjà une copie enrichie ; on n'enrichit pas une copie");
        }
    }

    private static String empreinteSha256(File fichier) throws EcritureGpxException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(fichier.toPath())));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new EcritureGpxException("l'empreinte du fichier d'origine n'a pas pu être calculée", e);
        }
    }

    // ==================== Modification du document ====================

    /**
     * Retire les textes qui ne contiennent que des espaces et retours à la ligne (la mise en
     * page du fichier d'origine), pour que le fichier écrit soit indenté proprement. Sans
     * conséquence sur les données : en GPX, ces blancs n'ont pas de sens.
     */
    private static void retirerTextesBlancs(Node noeud) {
        Node enfant = noeud.getFirstChild();
        while (enfant != null) {
            Node suivant = enfant.getNextSibling();
            if (enfant.getNodeType() == Node.TEXT_NODE && enfant.getTextContent().isBlank()) {
                noeud.removeChild(enfant);
            } else {
                retirerTextesBlancs(enfant);
            }
            enfant = suivant;
        }
    }

    private static Element premiereTrace(Element racine) throws EcritureGpxException {
        // Même choix que GpxParser : la première balise trk du fichier
        if (racine.getElementsByTagName("trk").getLength() == 0) {
            throw new EcritureGpxException("le fichier d'origine ne contient aucune trace (trk)");
        }
        return (Element) racine.getElementsByTagName("trk").item(0);
    }

    /** Remplace le commentaire de la trace par celui du Trajet (le supprime s'il est vide). */
    private static void ecrireCommentaire(Document document, Element trace, String commentaire) {
        for (Element existant : enfants(trace, "cmt")) {
            trace.removeChild(existant);
        }
        if (commentaire == null || commentaire.isBlank()) {
            return;
        }
        Element cmt = document.createElement("cmt");
        cmt.setTextContent(commentaire);
        // Ordre du schéma GPX : name, cmt, desc, ...
        Element nom = premierEnfant(trace, "name");
        trace.insertBefore(cmt, nom != null ? nom.getNextSibling() : trace.getFirstChild());
    }

    /** Remplace tous les segments de la trace par un seul, construit depuis les points du Trajet. */
    private static void remplacerPoints(Document document, Element trace, List<PointGpx> points) {
        for (Element segment : enfants(trace, "trkseg")) {
            trace.removeChild(segment);
        }
        Element nouveauSegment = document.createElement("trkseg");
        for (PointGpx point : points) {
            nouveauSegment.appendChild(elementPoint(document, point));
        }
        trace.appendChild(nouveauSegment);   // trkseg vient en dernier dans trk
    }

    private static Element elementPoint(Document document, PointGpx point) {
        Element trkpt = document.createElement("trkpt");
        trkpt.setAttribute("lat", decimal(point.getLatitude()));
        trkpt.setAttribute("lon", decimal(point.getLongitude()));

        OptionalDouble meilleure = connue(point.getAltitude(SourceAltitude.MEILLEURE));
        if (meilleure.isPresent()) {
            trkpt.appendChild(elementTexte(document, "ele", decimal(meilleure.getAsDouble())));
        }
        if (point.getTimestamp() != null) {
            trkpt.appendChild(elementTexte(document, "time", point.getTimestamp().toInstant(ZoneOffset.UTC).toString()));
        }

        OptionalDouble gpx = connue(point.getAltitudeGpx());
        OptionalDouble ign = connue(point.getAltitudeExterne());
        if (gpx.isPresent() || ign.isPresent()) {
            Element altitudes = document.createElement(Enrichissement.BALISE_ALTITUDES);
            if (gpx.isPresent()) {
                altitudes.setAttribute("gpx", decimal(gpx.getAsDouble()));
            }
            if (ign.isPresent()) {
                altitudes.setAttribute("ign", decimal(ign.getAsDouble()));
            }
            Element extensions = document.createElement("extensions");
            extensions.appendChild(altitudes);
            trkpt.appendChild(extensions);
        }
        return trkpt;
    }

    private static void marquerMetadonnees(Document document, Element racine, Enrichissement marque,
                                           List<PointGpx> points) {
        Element metadata = premierEnfant(racine, "metadata");
        if (metadata == null) {
            metadata = document.createElement("metadata");
            racine.insertBefore(metadata, racine.getFirstChild());   // metadata vient en premier dans gpx
        }

        // Texte lisible : ajouté à la suite de la description existante, jamais à sa place
        Element desc = premierEnfant(metadata, "desc");
        if (desc == null) {
            desc = document.createElement("desc");
            desc.setTextContent(Enrichissement.TEXTE_DESCRIPTION);
            Element nom = premierEnfant(metadata, "name");   // ordre du schéma : name, desc, ...
            metadata.insertBefore(desc, nom != null ? nom.getNextSibling() : metadata.getFirstChild());
        } else {
            String existante = desc.getTextContent().strip();
            desc.setTextContent(existante.isEmpty()
                ? Enrichissement.TEXTE_DESCRIPTION
                : existante + "\n" + Enrichissement.TEXTE_DESCRIPTION);
        }

        // Le cadre englobant d'origine ne vaut plus si le trajet a été raccourci
        Element bounds = premierEnfant(metadata, "bounds");
        if (bounds != null) {
            mettreAJourCadre(bounds, points);
        }

        Element extensions = premierEnfant(metadata, "extensions");
        if (extensions == null) {
            extensions = document.createElement("extensions");
            metadata.appendChild(extensions);   // extensions vient en dernier dans metadata
        }
        Element element = document.createElement(Enrichissement.BALISE_ENRICHISSEMENT);
        element.setAttribute("application", marque.application());
        element.setAttribute("version", Integer.toString(marque.version()));
        element.setAttribute("source", marque.source());
        element.setAttribute("taille", Long.toString(marque.taille()));
        element.setAttribute("sha256", marque.sha256());
        extensions.appendChild(element);
    }

    private static void mettreAJourCadre(Element bounds, List<PointGpx> points) {
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (PointGpx point : points) {
            minLat = Math.min(minLat, point.getLatitude());
            maxLat = Math.max(maxLat, point.getLatitude());
            minLon = Math.min(minLon, point.getLongitude());
            maxLon = Math.max(maxLon, point.getLongitude());
        }
        bounds.setAttribute("minlat", decimal(minLat));
        bounds.setAttribute("minlon", decimal(minLon));
        bounds.setAttribute("maxlat", decimal(maxLat));
        bounds.setAttribute("maxlon", decimal(maxLon));
    }

    // ==================== Écriture sûre du fichier ====================

    private File enregistrer(Document document, File source, File dossierDestination, int nombreDePoints,
                             Enrichissement marque) throws EcritureGpxException {
        Path dossier = dossierDestination.toPath();
        Path temporaire = null;
        boolean termine = false;
        try {
            Files.createDirectories(dossier);
            temporaire = Files.createTempFile(dossier, "parcours-", ".gpx.tmp");
            ecrireDocument(document, temporaire);
            verifierFichierEcrit(temporaire, nombreDePoints, marque);

            Path cible = dossier.resolve(source.getName());
            verifierCible(cible, source, marque);
            deplacer(temporaire, cible);
            termine = true;
            return cible.toFile();
        } catch (IOException e) {
            throw new EcritureGpxException("erreur d'écriture dans « " + dossierDestination + " » (" + e.getMessage() + ")", e);
        } finally {
            if (!termine && temporaire != null) {
                try {
                    Files.deleteIfExists(temporaire);
                } catch (IOException ignoree) {
                    // fichier temporaire resté sur place : sans conséquence sur les données
                }
            }
        }
    }

    private static void ecrireDocument(Document document, Path destination) throws EcritureGpxException, IOException {
        try (OutputStream sortie = Files.newOutputStream(destination)) {
            TransformerFactory fabrique = TransformerFactory.newInstance();
            fabrique.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformateur = fabrique.newTransformer();
            transformateur.setOutputProperty(OutputKeys.INDENT, "yes");
            transformateur.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformateur.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            document.setXmlStandalone(true);
            transformateur.transform(new DOMSource(document), new StreamResult(sortie));
        } catch (javax.xml.transform.TransformerException e) {
            throw new EcritureGpxException("le fichier n'a pas pu être écrit (" + e.getMessage() + ")", e);
        }
    }

    /** Relit le fichier écrit : mêmes nombres de points, et marque présente et identique. */
    private static void verifierFichierEcrit(Path fichier, int nombreDePoints, Enrichissement marque)
            throws EcritureGpxException {
        try {
            Trajet relu = GpxParser.parseFile(fichier.toFile());
            Optional<Enrichissement> marqueRelue = GpxParser.lireEnrichissement(fichier.toFile());
            if (relu.getPoints().size() != nombreDePoints || marqueRelue.isEmpty() || !marqueRelue.get().equals(marque)) {
                throw new EcritureGpxException("la copie écrite ne correspond pas au trajet (relecture incohérente) : "
                    + "elle n'a pas été enregistrée");
            }
        } catch (EcritureGpxException e) {
            throw e;
        } catch (Exception e) {
            throw new EcritureGpxException("la copie écrite n'a pas pu être relue (" + e.getMessage() + ") : "
                + "elle n'a pas été enregistrée", e);
        }
    }

    /**
     * Un fichier du même nom ne peut être remplacé que si c'est déjà une copie enrichie du même
     * original. Jamais l'original lui-même, jamais un autre fichier.
     */
    private static void verifierCible(Path cible, File source, Enrichissement marque)
            throws EcritureGpxException, IOException {
        if (!Files.exists(cible)) {
            return;
        }
        if (Files.isSameFile(cible, source.toPath())) {
            throw new EcritureGpxException("la copie remplacerait le fichier d'origine : "
                + "choisis un autre dossier de destination");
        }
        Optional<Enrichissement> existante;
        try {
            existante = GpxParser.lireEnrichissement(cible.toFile());
        } catch (Exception e) {
            existante = Optional.empty();   // illisible : ce n'est pas une copie à nous
        }
        if (existante.isEmpty() || !existante.get().sha256().equals(marque.sha256())) {
            throw new EcritureGpxException("un autre fichier nommé « " + cible.getFileName()
                + " » existe déjà dans « " + cible.getParent() + " » : il n'est pas remplacé");
        }
    }

    private static void deplacer(Path temporaire, Path cible) throws IOException {
        try {
            Files.move(temporaire, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporaire, cible, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ==================== Petits outils ====================

    private static OptionalDouble connue(OptionalDouble altitude) {
        return altitude.isPresent() && Double.isFinite(altitude.getAsDouble()) ? altitude : OptionalDouble.empty();
    }

    /** Nombre en notation décimale simple : jamais d'exposant, que le format GPX n'accepte pas. */
    private static String decimal(double valeur) {
        return BigDecimal.valueOf(valeur).toPlainString();
    }

    private static Element elementTexte(Document document, String balise, String texte) {
        Element element = document.createElement(balise);
        element.setTextContent(texte);
        return element;
    }

    /** Enfants directs portant ce nom de balise. */
    private static List<Element> enfants(Element parent, String balise) {
        List<Element> resultat = new ArrayList<>();
        for (Node noeud = parent.getFirstChild(); noeud != null; noeud = noeud.getNextSibling()) {
            if (noeud.getNodeType() == Node.ELEMENT_NODE && balise.equals(((Element) noeud).getTagName())) {
                resultat.add((Element) noeud);
            }
        }
        return resultat;
    }

    private static Element premierEnfant(Element parent, String balise) {
        List<Element> trouves = enfants(parent, balise);
        return trouves.isEmpty() ? null : trouves.get(0);
    }
}