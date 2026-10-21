package org.wvicto.parcours.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wvicto.parcours.model.Enrichissement;
import org.wvicto.parcours.model.GpxParser;
import org.wvicto.parcours.model.PointGpx;
import org.wvicto.parcours.model.Trajet;
import org.wvicto.parcours.service.EcritureGpxException;
import org.wvicto.parcours.service.GpxWriter;

class GpxWriterTest {

    private static final String BALISES_GPX_11 = "version=\"1.1\" creator=\"Geovelo\" "
        + "xmlns=\"http://www.topografix.com/GPX/1/1\"";

    /** Fichier d'origine complet : métadonnées, waypoint, commentaire, 5 points avec altitude et heure. */
    private static final String GPX_COMPLET = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx %s>
          <metadata>
            <name>Boulot direct</name>
            <desc>Trajet du matin</desc>
            <author><name>Wladimir</name></author>
            <bounds minlat="47.0" minlon="5.0" maxlat="47.004" maxlon="5.004"/>
          </metadata>
          <wpt lat="47.001" lon="5.001"><name>Boulangerie</name></wpt>
          <trk>
            <name>Boulot direct</name>
            <cmt>vent de face</cmt>
            <trkseg>
              <trkpt lat="47.000" lon="5.000"><ele>200.0</ele><time>2026-09-15T07:00:00Z</time></trkpt>
              <trkpt lat="47.001" lon="5.001"><ele>201.0</ele><time>2026-09-15T07:00:10Z</time></trkpt>
              <trkpt lat="47.002" lon="5.002"><ele>202.0</ele><time>2026-09-15T07:00:20Z</time></trkpt>
              <trkpt lat="47.003" lon="5.003"><ele>203.0</ele><time>2026-09-15T07:00:30Z</time></trkpt>
              <trkpt lat="47.004" lon="5.004"><ele>204.0</ele><time>2026-09-15T07:00:40Z</time></trkpt>
            </trkseg>
          </trk>
        </gpx>
        """.formatted(BALISES_GPX_11);

    /** Fichier d'origine minimal : ni métadonnées, ni commentaire, ni heure, ni altitude. */
    private static final String GPX_MINIMAL = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx %s>
          <trk>
            <trkseg>
              <trkpt lat="47.000" lon="5.000"/>
              <trkpt lat="47.001" lon="5.001"/>
            </trkseg>
          </trk>
        </gpx>
        """.formatted(BALISES_GPX_11);

    @TempDir
    Path temp;

    private Path dossierSource;
    private Path dossierSortie;
    private final GpxWriter ecrivain = new GpxWriter();

    @BeforeEach
    void preparerLesDossiers() throws Exception {
        dossierSource = Files.createDirectory(temp.resolve("source"));
        dossierSortie = temp.resolve("enrichis");   // n'existe pas : l'écrivain doit le créer
    }

    // ==================== Outils ====================

    private Path ecrireSource(String nom, String contenu) throws Exception {
        return ecrireSource(dossierSource, nom, contenu);
    }

    private Path ecrireSource(Path dossier, String nom, String contenu) throws Exception {
        Files.createDirectories(dossier);
        return Files.writeString(dossier.resolve(nom), contenu, StandardCharsets.UTF_8);
    }

    private static String sha256(Path fichier) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fichier)));
    }

    private static Document lireXml(Path fichier) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fichier.toFile());
    }

    private static List<Element> enfants(Element parent) {
        List<Element> resultat = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                resultat.add((Element) n);
            }
        }
        return resultat;
    }

    private static List<String> nomsDesEnfants(Element parent) {
        return enfants(parent).stream().map(Element::getTagName).toList();
    }

    private static Element enfant(Element parent, String balise) {
        return enfants(parent).stream().filter(e -> e.getTagName().equals(balise)).findFirst().orElse(null);
    }

    private static List<Element> elements(Document document, String balise) {
        NodeList liste = document.getElementsByTagName(balise);
        List<Element> resultat = new ArrayList<>();
        for (int i = 0; i < liste.getLength(); i++) {
            resultat.add((Element) liste.item(i));
        }
        return resultat;
    }

    /** Le même trajet, avec l'altitude externe 300, 301, 302... sur ses points. */
    private static Trajet avecAltitudesExternes(Trajet trajet) {
        List<PointGpx> points = trajet.getPoints();
        List<PointGpx> enrichis = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            enrichis.add(points.get(i).avecAltitudeExterne(300.0 + i));
        }
        return trajet.avecPoints(enrichis);
    }

    private Trajet charger(Path fichier) throws Exception {
        return GpxParser.parseFile(fichier.toFile());
    }

    private long fichiersTemporaires(Path dossier) throws Exception {
        if (!Files.exists(dossier)) {
            return 0;
        }
        try (Stream<Path> fichiers = Files.list(dossier)) {
            return fichiers.filter(f -> f.getFileName().toString().endsWith(".tmp")).count();
        }
    }

    // ==================== La marque ====================

    @Test
    void testCopie_PorteLaMarqueTechniqueDeLOriginal() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);

        File copie = ecrivain.ecrireCopieEnrichie(avecAltitudesExternes(charger(source)), dossierSortie.toFile());

        Enrichissement marque = GpxParser.lireEnrichissement(copie).orElseThrow();
        assertEquals("Parcours", marque.application());
        assertEquals(1, marque.version());
        assertEquals("boulot.gpx", marque.source(), "Le nom seul, sans chemin");
        assertEquals(Files.size(source), marque.taille());
        assertEquals(sha256(source), marque.sha256());
    }

    @Test
    void testCopie_AjouteLaMarqueLisibleALaSuiteDeLaDescriptionExistante() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);

        File copie = ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile());

        Element desc = enfant(enfant(lireXml(copie.toPath()).getDocumentElement(), "metadata"), "desc");
        assertEquals("Trajet du matin\nenrichi : Parcours", desc.getTextContent());
    }

    @Test
    void testCopie_DeclareLEspaceDeNomsEtLesElementsSontLusCommeTels() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        File copie = ecrivain.ecrireCopieEnrichie(avecAltitudesExternes(charger(source)), dossierSortie.toFile());

        // Relecture avec un analyseur qui tient compte des espaces de noms : le fichier doit être valide
        DocumentBuilderFactory fabrique = DocumentBuilderFactory.newInstance();
        fabrique.setNamespaceAware(true);
        Document document = fabrique.newDocumentBuilder().parse(copie);

        assertEquals(5, document.getElementsByTagNameNS(Enrichissement.ESPACE_DE_NOMS, "alt").getLength());
        assertEquals(1, document.getElementsByTagNameNS(Enrichissement.ESPACE_DE_NOMS, "enrichissement").getLength());
        assertEquals(5, document.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkpt").getLength(),
            "Les balises GPX restent dans l'espace de noms GPX");
    }

    // ==================== L'original ====================

    @Test
    void testOriginal_RestePareilOctetPourOctet_EtRienNEstAjouteACoteDeLui() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        byte[] avant = Files.readAllBytes(source);

        ecrivain.ecrireCopieEnrichie(avecAltitudesExternes(charger(source)), dossierSortie.toFile());

        assertArrayEquals(avant, Files.readAllBytes(source));
        try (Stream<Path> fichiers = Files.list(dossierSource)) {
            assertEquals(1, fichiers.count(), "Aucun fichier ajouté dans le dossier de l'original");
        }
    }

    @Test
    void testCopie_ApresEcriture_IlNeResteAucunFichierTemporaire() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);

        ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile());

        assertEquals(0, fichiersTemporaires(dossierSortie));
        try (Stream<Path> fichiers = Files.list(dossierSortie)) {
            assertEquals(1, fichiers.count());
        }
    }

    @Test
    void testCopie_PorteLeNomDeLOriginal_DansLeDossierDeDestination() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);

        File copie = ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile());

        assertEquals(dossierSortie.resolve("boulot.gpx"), copie.toPath());
        assertTrue(Files.isRegularFile(copie.toPath()));
    }

    // ==================== Ce que la copie conserve ====================

    @Test
    void testCopie_ConserveLesMetadonneesDuFichierDOrigine() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);

        File copie = ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile());

        Document document = lireXml(copie.toPath());
        Element racine = document.getDocumentElement();
        assertEquals("Geovelo", racine.getAttribute("creator"));
        assertEquals("1.1", racine.getAttribute("version"));
        Element metadata = enfant(racine, "metadata");
        assertEquals("Boulot direct", enfant(metadata, "name").getTextContent());
        assertEquals("Wladimir", enfant(enfant(metadata, "author"), "name").getTextContent());
        assertEquals("Boulangerie", enfant(enfant(racine, "wpt"), "name").getTextContent(),
            "Les waypoints sont recopiés");
    }

    @Test
    void testCopie_RespecteLOrdreDesBalisesDuSchemaGpx() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);

        File copie = ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile());

        Element racine = lireXml(copie.toPath()).getDocumentElement();
        assertEquals(List.of("metadata", "wpt", "trk"), nomsDesEnfants(racine));
        assertEquals(List.of("name", "desc", "author", "bounds", "extensions"),
            nomsDesEnfants(enfant(racine, "metadata")));
        assertEquals(List.of("name", "cmt", "trkseg"), nomsDesEnfants(enfant(racine, "trk")));
    }

    @Test
    void testCopie_SansMetadonneesDansLOriginal_EnCreeEnPremier() throws Exception {
        Path source = ecrireSource("minimal.gpx", GPX_MINIMAL);

        File copie = ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile());

        Element racine = lireXml(copie.toPath()).getDocumentElement();
        assertEquals(List.of("metadata", "trk"), nomsDesEnfants(racine));
        Element metadata = enfant(racine, "metadata");
        assertEquals(List.of("desc", "extensions"), nomsDesEnfants(metadata));
        assertEquals("enrichi : Parcours", enfant(metadata, "desc").getTextContent());
    }

    // ==================== Les points et les deux altitudes ====================

    @Test
    void testRelecture_RetrouveLesPointsLesHeuresEtLesDeuxAltitudes() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Trajet original = charger(source);
        File copie = ecrivain.ecrireCopieEnrichie(avecAltitudesExternes(original), dossierSortie.toFile());

        Trajet relu = charger(copie.toPath());

        List<PointGpx> avant = original.getPoints();
        List<PointGpx> apres = relu.getPoints();
        assertEquals(5, apres.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(avant.get(i).getLatitude(), apres.get(i).getLatitude(), 1e-12, "Latitude " + i);
            assertEquals(avant.get(i).getLongitude(), apres.get(i).getLongitude(), 1e-12, "Longitude " + i);
            assertEquals(avant.get(i).getTimestamp(), apres.get(i).getTimestamp(), "Heure " + i);
            assertEquals(200.0 + i, apres.get(i).getAltitudeGpx().getAsDouble(), 1e-12, "Altitude GPX " + i);
            assertEquals(300.0 + i, apres.get(i).getAltitudeExterne().getAsDouble(), 1e-12, "Altitude externe " + i);
        }
        assertEquals(copie, relu.getFichierSource());
        assertTrue(relu.getEnrichissement() != null, "La copie rechargée sait qu'elle est enrichie");
    }

    @Test
    void testPoints_LaBaliseEleContientLaMeilleureAltitude_EtParcoursAltLesDeux() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        LocalDateTime t = LocalDateTime.of(2026, 9, 15, 7, 0, 0);
        List<PointGpx> points = Arrays.asList(
            new PointGpx(47.000, 5.000, 100.0, t).avecAltitudeExterne(110.0),            // les deux
            new PointGpx(47.001, 5.001, 120.0, t),                                        // fichier seul
            PointGpx.sansAltitude(47.002, 5.002, t).avecAltitudeExterne(130.0),          // service seul
            PointGpx.sansAltitude(47.003, 5.003, t));                                     // aucune
        Trajet trajet = charger(source).avecPoints(points);

        File copie = ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile());

        List<Element> trkpts = elements(lireXml(copie.toPath()), "trkpt");
        // Les deux : ele = service, extension avec les deux valeurs
        assertEquals("110.0", enfant(trkpts.get(0), "ele").getTextContent());
        Element alt0 = enfant(enfant(trkpts.get(0), "extensions"), Enrichissement.BALISE_ALTITUDES);
        assertEquals("100.0", alt0.getAttribute("gpx"));
        assertEquals("110.0", alt0.getAttribute("ign"));
        assertEquals(List.of("ele", "time", "extensions"), nomsDesEnfants(trkpts.get(0)));
        // Fichier seul : ele = fichier, pas d'attribut ign
        assertEquals("120.0", enfant(trkpts.get(1), "ele").getTextContent());
        Element alt1 = enfant(enfant(trkpts.get(1), "extensions"), Enrichissement.BALISE_ALTITUDES);
        assertEquals("120.0", alt1.getAttribute("gpx"));
        assertFalse(alt1.hasAttribute("ign"));
        // Service seul : ele = service, pas d'attribut gpx
        assertEquals("130.0", enfant(trkpts.get(2), "ele").getTextContent());
        Element alt2 = enfant(enfant(trkpts.get(2), "extensions"), Enrichissement.BALISE_ALTITUDES);
        assertFalse(alt2.hasAttribute("gpx"));
        assertEquals("130.0", alt2.getAttribute("ign"));
        // Aucune : ni ele, ni extension
        assertEquals(List.of("time"), nomsDesEnfants(trkpts.get(3)));

        // Et la relecture redonne exactement ces quatre situations
        List<PointGpx> relus = charger(copie.toPath()).getPoints();
        assertEquals(100.0, relus.get(0).getAltitudeGpx().getAsDouble(), 1e-12);
        assertEquals(110.0, relus.get(0).getAltitudeExterne().getAsDouble(), 1e-12);
        assertEquals(120.0, relus.get(1).getAltitudeGpx().getAsDouble(), 1e-12);
        assertTrue(relus.get(1).getAltitudeExterne().isEmpty(), "120 est une altitude du fichier, pas du service");
        assertTrue(relus.get(2).getAltitudeGpx().isEmpty());
        assertEquals(130.0, relus.get(2).getAltitudeExterne().getAsDouble(), 1e-12);
        assertTrue(relus.get(3).getAltitudeGpx().isEmpty());
        assertTrue(relus.get(3).getAltitudeExterne().isEmpty());
    }

    @Test
    void testPoints_SansHeure_RestentSansHeure() throws Exception {
        Path source = ecrireSource("minimal.gpx", GPX_MINIMAL);

        File copie = ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile());

        assertEquals(0, elements(lireXml(copie.toPath()), "time").size(), "Aucune heure inventée");
        for (PointGpx point : charger(copie.toPath()).getPoints()) {
            assertNull(point.getTimestamp());
        }
    }

    @Test
    void testPoints_LHeureEstEcriteEnUtcAvecSesFractionsDeSeconde() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        LocalDateTime t = LocalDateTime.of(2026, 9, 15, 7, 0, 0, 500_000_000);
        Trajet trajet = charger(source).avecPoints(List.of(
            new PointGpx(47.0, 5.0, 100.0, t),
            new PointGpx(47.001, 5.001, 101.0, t.plusSeconds(10).withNano(0))));

        File copie = ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile());

        List<Element> heures = elements(lireXml(copie.toPath()), "time");
        assertEquals("2026-09-15T07:00:00.500Z", heures.get(0).getTextContent());
        assertEquals("2026-09-15T07:00:10Z", heures.get(1).getTextContent());
        assertEquals(t, charger(copie.toPath()).getPoints().get(0).getTimestamp());
    }

    @Test
    void testPoints_LesCoordonneesSontEcritesSansExposant() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Trajet trajet = charger(source).avecPoints(List.of(
            new PointGpx(0.00001234, -0.0000005, 1.0, null),
            new PointGpx(0.00002, 0.0000001, 2.0, null)));

        File copie = ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile());

        for (Element trkpt : elements(lireXml(copie.toPath()), "trkpt")) {
            assertFalse(trkpt.getAttribute("lat").contains("E"), trkpt.getAttribute("lat"));
            assertFalse(trkpt.getAttribute("lon").contains("E"), trkpt.getAttribute("lon"));
        }
        List<PointGpx> relus = charger(copie.toPath()).getPoints();
        assertEquals(0.00001234, relus.get(0).getLatitude(), 1e-15);
        assertEquals(-0.0000005, relus.get(0).getLongitude(), 1e-15);
    }

    // ==================== Trajet raccourci ====================

    @Test
    void testTrajetRaccourci_LaCopieEstPlusCourte_EtLeCadreEnglobantEstRecalcule() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Trajet trajet = charger(source);
        trajet.supprimerFin(2);   // par exemple, la fin faite en voiture

        File copie = ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile());

        assertEquals(3, charger(copie.toPath()).getPoints().size());
        Element bounds = enfant(enfant(lireXml(copie.toPath()).getDocumentElement(), "metadata"), "bounds");
        assertEquals(47.0, Double.parseDouble(bounds.getAttribute("minlat")), 1e-12);
        assertEquals(47.002, Double.parseDouble(bounds.getAttribute("maxlat")), 1e-12);
        assertEquals(5.002, Double.parseDouble(bounds.getAttribute("maxlon")), 1e-12);
        assertEquals(5, charger(source).getPoints().size(), "L'original garde ses 5 points");
    }

    // ==================== Commentaire ====================

    @Test
    void testCommentaire_DeLOriginalEstConserveUneSeuleFois() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Trajet trajet = charger(source);
        assertEquals("vent de face", trajet.getCommentaire());

        File copie = ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile());

        Element trace = enfant(lireXml(copie.toPath()).getDocumentElement(), "trk");
        assertEquals(1, enfants(trace).stream().filter(e -> e.getTagName().equals("cmt")).count());
        assertEquals("vent de face", enfant(trace, "cmt").getTextContent());
        assertEquals("vent de face", charger(copie.toPath()).getCommentaire());
    }

    @Test
    void testCommentaire_ModifieDansLeTrajet_RemplaceCeluiDeLOriginal() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Trajet trajet = charger(source);
        trajet.setCommentaire("crevaison au km 12");

        File copie = ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile());

        Element trace = enfant(lireXml(copie.toPath()).getDocumentElement(), "trk");
        assertEquals(List.of("name", "cmt", "trkseg"), nomsDesEnfants(trace));
        assertEquals("crevaison au km 12", enfant(trace, "cmt").getTextContent());
    }

    @Test
    void testCommentaire_Vide_NEstPasEcrit() throws Exception {
        Path source = ecrireSource("minimal.gpx", GPX_MINIMAL);

        File copie = ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile());

        assertEquals(0, elements(lireXml(copie.toPath()), "cmt").size());
    }

    @Test
    void testCommentaire_VidePourUnTrajetQuiEnAvait_LeSupprimeDeLaCopie() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Trajet trajet = charger(source);
        trajet.setCommentaire("");

        File copie = ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile());

        assertEquals(0, elements(lireXml(copie.toPath()), "cmt").size());
    }

    // ==================== Refus ====================

    @Test
    void testRefus_TrajetSansFichierSource_RienNEstEcrit() throws Exception {
        Trajet sansSource = new Trajet("Créé dans l'appli", LocalDate.of(2026, 10, 11),
            List.of(PointGpx.sansAltitude(47.0, 5.0)));

        EcritureGpxException erreur = assertThrows(EcritureGpxException.class,
            () -> ecrivain.ecrireCopieEnrichie(sansSource, dossierSortie.toFile()));

        assertTrue(erreur.getMessage().contains("fichier source"), erreur.getMessage());
        assertFalse(Files.exists(dossierSortie), "Le dossier n'est même pas créé");
    }

    @Test
    void testRefus_TrajetLisse_NeSecritJamais() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Trajet lisse = charger(source).lisse(3);

        assertThrows(EcritureGpxException.class, () -> ecrivain.ecrireCopieEnrichie(lisse, dossierSortie.toFile()));
        assertFalse(Files.exists(dossierSortie));
    }

    @Test
    void testRefus_SousTrajet_NeSecritJamais() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Trajet portion = charger(source).sousTrajet(1, 3);

        assertThrows(EcritureGpxException.class, () -> ecrivain.ecrireCopieEnrichie(portion, dossierSortie.toFile()));
    }

    @Test
    void testRefus_TrajetSansPoint() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Trajet vide = charger(source).avecPoints(new ArrayList<>());

        assertThrows(EcritureGpxException.class, () -> ecrivain.ecrireCopieEnrichie(vide, dossierSortie.toFile()));
    }

    @Test
    void testRefus_FichierGpx10_MessageExplicite() throws Exception {
        String gpx10 = GPX_COMPLET.replace("version=\"1.1\"", "version=\"1.0\"");
        Path source = ecrireSource("ancien.gpx", gpx10);

        EcritureGpxException erreur = assertThrows(EcritureGpxException.class,
            () -> ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile()));

        assertTrue(erreur.getMessage().contains("1.1"), erreur.getMessage());
        assertFalse(Files.exists(dossierSortie));
    }

    @Test
    void testRefus_FichierDejaEnrichi_OnNEnrichitPasUneCopie() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        File copie = ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile());
        Trajet rechargee = charger(copie.toPath());
        Path autreDossier = temp.resolve("autre");

        EcritureGpxException erreur = assertThrows(EcritureGpxException.class,
            () -> ecrivain.ecrireCopieEnrichie(rechargee, autreDossier.toFile()));

        assertTrue(erreur.getMessage().contains("déjà une copie enrichie"), erreur.getMessage());
    }

    @Test
    void testRefus_DossierDeDestinationEgalAuDossierDeLOriginal_L_OriginalEstIntact() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        byte[] avant = Files.readAllBytes(source);

        assertThrows(EcritureGpxException.class,
            () -> ecrivain.ecrireCopieEnrichie(charger(source), dossierSource.toFile()));

        assertArrayEquals(avant, Files.readAllBytes(source), "L'original n'est jamais écrasé");
        assertEquals(0, fichiersTemporaires(dossierSource), "Le fichier temporaire est nettoyé");
    }

    @Test
    void testRefus_UnAutreFichierDuMemeNomExisteDeja_IlNEstPasRemplace() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Files.createDirectories(dossierSortie);
        Path existant = Files.writeString(dossierSortie.resolve("boulot.gpx"), "mon propre fichier");

        EcritureGpxException erreur = assertThrows(EcritureGpxException.class,
            () -> ecrivain.ecrireCopieEnrichie(charger(source), dossierSortie.toFile()));

        assertTrue(erreur.getMessage().contains("existe déjà"), erreur.getMessage());
        assertEquals("mon propre fichier", Files.readString(existant));
        assertEquals(0, fichiersTemporaires(dossierSortie));
    }

    @Test
    void testRefus_CopieDUnAutreOriginalDeMemeNom_IlNEstPasRemplace() throws Exception {
        Path sourceA = ecrireSource(dossierSource.resolve("a"), "boulot.gpx", GPX_COMPLET);
        Path sourceB = ecrireSource(dossierSource.resolve("b"), "boulot.gpx",
            GPX_COMPLET.replace("Boulot direct", "Boulot via Seine"));
        File copieA = ecrivain.ecrireCopieEnrichie(charger(sourceA), dossierSortie.toFile());
        byte[] contenuCopieA = Files.readAllBytes(copieA.toPath());

        assertThrows(EcritureGpxException.class,
            () -> ecrivain.ecrireCopieEnrichie(charger(sourceB), dossierSortie.toFile()));

        assertArrayEquals(contenuCopieA, Files.readAllBytes(copieA.toPath()), "La copie de A est intacte");
    }

    @Test
    void testCopieDejaEcriteDuMemeOriginal_EstRemplacee() throws Exception {
        Path source = ecrireSource("boulot.gpx", GPX_COMPLET);
        Trajet trajet = charger(source);
        File premiere = ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile());
        trajet.supprimerFin(2);

        File seconde = ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile());

        assertEquals(premiere, seconde);
        assertEquals(3, charger(seconde.toPath()).getPoints().size(), "La copie reflète le trajet raccourci");
        assertEquals(0, fichiersTemporaires(dossierSortie));
    }

    @Test
    void testRefus_FichierSourceIllisible_Exception() throws Exception {
        Path source = ecrireSource("casse.gpx", GPX_COMPLET);
        Trajet trajet = charger(source);
        Files.writeString(source, "ceci n'est pas du XML");

        assertThrows(EcritureGpxException.class, () -> ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile()));
        assertFalse(Files.exists(dossierSortie));
    }

    @Test
    void testRefus_FichierSourceAvecDoctype_Exception() throws Exception {
        Path source = ecrireSource("doctype.gpx", GPX_COMPLET);
        Trajet trajet = charger(source);
        Files.writeString(source, GPX_COMPLET.replace("<gpx ", "<!DOCTYPE gpx [<!ENTITY x \"y\">]>\n<gpx "));

        assertThrows(EcritureGpxException.class, () -> ecrivain.ecrireCopieEnrichie(trajet, dossierSortie.toFile()));
    }
}