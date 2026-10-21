package org.wvicto.parcours.model;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GpxParser {

    private static final DateTimeFormatter[] TIME_FORMATTERS = {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    private static Document parseDocument(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);
        document.getDocumentElement().normalize();
        return document;
    }

    public static Trajet parseFile(File file) throws Exception {
        Document document = parseDocument(file);

        Element root = document.getDocumentElement();
        NodeList trkList = root.getElementsByTagName("trk");

        if (trkList.getLength() == 0) {
            throw new IllegalArgumentException("Aucune trace (trk) trouvée dans le fichier GPX");
        }

        Element trk = (Element) trkList.item(0);
        String nom = getElementTextContent(trk, "name", "Trajet sans nom");
        LocalDate date = parseDate(trk);
        List<PointGpx> points = parseTrackPoints(trk);

        Trajet trajet = new Trajet(nom, date, points);
        trajet.setFichierSource(file);
        trajet.setCommentaire(commentaireDeLaTrace(trk));
        Enrichissement.lireDans(root).ifPresent(trajet::setEnrichissement);
        return trajet;
    }

    /**
     * Relit seulement la marque d'enrichissement d'un fichier GPX : vide si le fichier n'est
     * pas une copie enrichie par l'application.
     */
    public static Optional<Enrichissement> lireEnrichissement(File file) throws Exception {
        return Enrichissement.lireDans(parseDocument(file).getDocumentElement());
    }

    /**
     * Commentaire de la trace : la balise cmt enfant DIRECTE de trk (et non celle d'un point,
     * que getElementsByTagName trouverait aussi).
     */
    private static String commentaireDeLaTrace(Element trk) {
        for (Node noeud = trk.getFirstChild(); noeud != null; noeud = noeud.getNextSibling()) {
            if (noeud.getNodeType() == Node.ELEMENT_NODE && "cmt".equals(((Element) noeud).getTagName())) {
                return noeud.getTextContent().strip();
            }
        }
        return "";
    }

    private static LocalDate parseDate(Element trk) {
        String dateStr = getElementTextContent(trk, "time");
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                return zdt.toLocalDate();
            } catch (DateTimeParseException e) {
                for (DateTimeFormatter formatter : TIME_FORMATTERS) {
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(dateStr, formatter);
                        return ldt.toLocalDate();
                    } catch (DateTimeParseException ignored) {}
                }
            }
        }
        return LocalDate.now();
    }

    private static List<PointGpx> parseTrackPoints(Element trk) {
        List<PointGpx> points = new ArrayList<>();
        NodeList trksegList = trk.getElementsByTagName("trkseg");

        for (int i = 0; i < trksegList.getLength(); i++) {
            Element trkseg = (Element) trksegList.item(i);
            NodeList trkptList = trkseg.getElementsByTagName("trkpt");

            for (int j = 0; j < trkptList.getLength(); j++) {
                Element trkpt = (Element) trkptList.item(j);
                PointGpx point = parseTrackPoint(trkpt);
                if (point != null) {
                    points.add(point);
                }
            }
        }
        return points;
    }

    /**
     * Lit un point de trace. Deux altitudes peuvent exister : celle du fichier d'origine
     * (balise ele) et celle d'un service externe. Dans une copie enrichie, l'élément
     * parcours:alt du point donne les deux (attributs gpx et ign, chacun pouvant manquer) et
     * remplace ele, qui n'y contient que la meilleure des deux, sans dire d'où elle vient.
     *
     * Une altitude absente, vide ou illisible reste absente (et non 0). De même pour
     * l'heure : un point sans balise time, ou avec une heure illisible, n'a pas d'horodatage
     * (on n'invente pas « maintenant »).
     */
    private static PointGpx parseTrackPoint(Element trkpt) {
        double latitude = Double.parseDouble(trkpt.getAttribute("lat"));
        double longitude = Double.parseDouble(trkpt.getAttribute("lon"));
        Double altitudeGpx = null;
        Double altitudeExterne = null;
        LocalDateTime timestamp = null;
        Element altitudesEnrichies = null;

        NodeList children = trkpt.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                switch (element.getTagName()) {
                    case "ele":
                        altitudeGpx = lireDecimal(element.getTextContent());
                        break;
                    case "time":
                        timestamp = parseTimestamp(element.getTextContent());
                        break;
                    case "extensions":
                        altitudesEnrichies = trouverAltitudesEnrichies(element);
                        break;
                }
            }
        }

        if (altitudesEnrichies != null) {
            altitudeGpx = lireDecimal(altitudesEnrichies.getAttribute("gpx"));
            altitudeExterne = lireDecimal(altitudesEnrichies.getAttribute("ign"));
        }

        PointGpx point = altitudeGpx == null
            ? PointGpx.sansAltitude(latitude, longitude, timestamp)
            : new PointGpx(latitude, longitude, altitudeGpx, timestamp);
        return altitudeExterne == null ? point : point.avecAltitudeExterne(altitudeExterne);
    }

    private static Element trouverAltitudesEnrichies(Element extensions) {
        for (Node noeud = extensions.getFirstChild(); noeud != null; noeud = noeud.getNextSibling()) {
            if (noeud.getNodeType() == Node.ELEMENT_NODE
                    && Enrichissement.BALISE_ALTITUDES.equals(((Element) noeud).getTagName())) {
                return (Element) noeud;
            }
        }
        return null;
    }

    /** Nombre fini lu dans un texte, ou null si le texte est vide, illisible, NaN ou infini. */
    private static Double lireDecimal(String texte) {
        if (texte == null) {
            return null;
        }
        try {
            double valeur = Double.parseDouble(texte.trim());
            return Double.isFinite(valeur) ? valeur : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Heure d'un point, exprimée en UTC (les heures du modèle n'ont pas de fuseau : une heure
     * écrite avec un décalage, comme 09:42:10+02:00, est donc ramenée à 07:42:10). Null si le
     * texte est vide ou illisible.
     */
    private static LocalDateTime parseTimestamp(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        String texte = timeStr.trim();
        try {
            return OffsetDateTime.parse(texte).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // pas d'indicateur de fuseau : on essaie les autres formats
        }
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(texte, formatter);
            } catch (DateTimeParseException e) {
                try {
                    ZonedDateTime zdt = ZonedDateTime.parse(texte, formatter);
                    return zdt.toLocalDateTime();
                } catch (DateTimeParseException ignored) {}
            }
        }
        return null;
    }

    private static String getElementTextContent(Element parent, String tagName) {
        return getElementTextContent(parent, tagName, null);
    }

    private static String getElementTextContent(Element parent, String tagName, String defaultValue) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String content = node.getTextContent().trim();
                return content.isEmpty() ? defaultValue : content;
            }
        }
        return defaultValue;
    }
}