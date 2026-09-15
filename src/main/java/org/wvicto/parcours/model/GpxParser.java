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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class GpxParser {

    private static final DateTimeFormatter[] TIME_FORMATTERS = {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    public static Trajet parseFile(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);
        document.getDocumentElement().normalize();

        Element root = document.getDocumentElement();
        NodeList trkList = root.getElementsByTagName("trk");

        if (trkList.getLength() == 0) {
            throw new IllegalArgumentException("Aucune trace (trk) trouvée dans le fichier GPX");
        }

        Element trk = (Element) trkList.item(0);
        String nom = getElementTextContent(trk, "name", "Trajet sans nom");
        LocalDate date = parseDate(trk);
        List<PointGpx> points = parseTrackPoints(trk);

        return new Trajet(nom, date, points);
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

    private static PointGpx parseTrackPoint(Element trkpt) {
        double latitude = Double.parseDouble(trkpt.getAttribute("lat"));
        double longitude = Double.parseDouble(trkpt.getAttribute("lon"));
        double altitude = 0.0;
        LocalDateTime timestamp = LocalDateTime.now();

        NodeList children = trkpt.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                switch (element.getTagName()) {
                    case "ele":
                        altitude = Double.parseDouble(element.getTextContent());
                        break;
                    case "time":
                        timestamp = parseTimestamp(element.getTextContent());
                        break;
                }
            }
        }
        return new PointGpx(latitude, longitude, altitude, timestamp);
    }

    private static LocalDateTime parseTimestamp(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return LocalDateTime.now();
        }
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(timeStr, formatter);
            } catch (DateTimeParseException e) {
                try {
                    ZonedDateTime zdt = ZonedDateTime.parse(timeStr, formatter);
                    return zdt.toLocalDateTime();
                } catch (DateTimeParseException ignored) {}
            }
        }
        return LocalDateTime.now();
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