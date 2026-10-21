package org.wvicto.parcours.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GpxParserTest {

    @TempDir
    File dossier;

    private File ecrire(String nom, String contenu) throws Exception {
        File f = new File(dossier, nom);
        Files.writeString(f.toPath(), contenu, StandardCharsets.UTF_8);
        return f;
    }

    private static String gpx(String trkContenu) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<gpx version=\"1.1\" creator=\"test\" xmlns=\"http://www.topografix.com/GPX/1/1\">"
            + "<trk><name>T</name>" + trkContenu + "</trk></gpx>";
    }

    private static String pt(String contenu) {
        return "<trkpt lat=\"48.0\" lon=\"2.0\">" + contenu + "</trkpt>";
    }

    private PointGpx unPoint(String contenuPoint) throws Exception {
        File f = ecrire("a.gpx", gpx("<trkseg>" + pt(contenuPoint) + "</trkseg>"));
        return GpxParser.parseFile(f).getPoints().get(0);
    }

    @Test
    void altitudePresente() throws Exception {
        assertEquals(123.5, unPoint("<ele>123.5</ele>").getAltitudeGpx().getAsDouble(), 1e-9);
    }

    @Test
    void altitudeAbsenteVideOuIllisible_resteAbsente() throws Exception {
        assertTrue(unPoint("").getAltitudeGpx().isEmpty());
        assertTrue(unPoint("<ele></ele>").getAltitudeGpx().isEmpty());
        assertTrue(unPoint("<ele>abc</ele>").getAltitudeGpx().isEmpty());
        assertTrue(unPoint("<ele>NaN</ele>").getAltitudeGpx().isEmpty());
        assertTrue(unPoint("<ele>Infinity</ele>").getAltitudeGpx().isEmpty());
    }

    @Test
    void altitudeZeroReelleEstConservee() throws Exception {
        assertEquals(0.0, unPoint("<ele>0</ele>").getAltitudeGpx().getAsDouble(), 1e-9);
    }

    @Test
    void heure_ZuluEtDecalage() throws Exception {
        assertEquals(LocalDateTime.of(2026, 10, 7, 8, 0, 0),
            unPoint("<time>2026-10-07T08:00:00Z</time>").getTimestamp());
        assertEquals(LocalDateTime.of(2026, 10, 7, 7, 42, 10),
            unPoint("<time>2026-10-07T09:42:10+02:00</time>").getTimestamp());
    }

    @Test
    void heure_absenteOuIllisible_estNull() throws Exception {
        assertNull(unPoint("").getTimestamp());
        assertNull(unPoint("<time></time>").getTimestamp());
        assertNull(unPoint("<time>hier</time>").getTimestamp());
    }

    @Test
    void extensionParcoursAlt_remplaceEle() throws Exception {
        PointGpx p = unPoint("<ele>999</ele><extensions><parcours:alt xmlns:parcours=\"urn:wvicto:parcours:1\" gpx=\"100\" ign=\"110\"/></extensions>");
        // le nom de balise est lu tel quel ("parcours:alt")
        assertEquals(100.0, p.getAltitudeGpx().getAsDouble(), 1e-9);
        assertEquals(110.0, p.getAltitudeExterne().getAsDouble(), 1e-9);
    }

    @Test
    void extensionParcoursAlt_attributUnique() throws Exception {
        PointGpx p = unPoint("<ele>110</ele><extensions><parcours:alt xmlns:parcours=\"urn:wvicto:parcours:1\" ign=\"110\"/></extensions>");
        assertTrue(p.getAltitudeGpx().isEmpty());
        assertEquals(110.0, p.getAltitudeExterne().getAsDouble(), 1e-9);
    }

    @Test
    void commentaireDeLaTrace_pasCeluiDUnPoint() throws Exception {
        File f = ecrire("c.gpx", gpx("<cmt>ma note</cmt><trkseg>" + pt("<cmt>point</cmt>") + "</trkseg>"));
        assertEquals("ma note", GpxParser.parseFile(f).getCommentaire());
        File g = ecrire("d.gpx", gpx("<trkseg>" + pt("<cmt>point</cmt>") + "</trkseg>"));
        assertEquals("", GpxParser.parseFile(g).getCommentaire());
    }

    @Test
    void fichierSourceMemorise() throws Exception {
        File f = ecrire("e.gpx", gpx("<trkseg>" + pt("") + "</trkseg>"));
        assertEquals(f, GpxParser.parseFile(f).getFichierSource());
    }

    @Test
    void marqueAbsenteSurFichierOrdinaire() throws Exception {
        File f = ecrire("f.gpx", gpx("<trkseg>" + pt("") + "</trkseg>"));
        assertTrue(GpxParser.lireEnrichissement(f).isEmpty());
        assertNull(GpxParser.parseFile(f).getEnrichissement());
    }

    private static String avecMarque(String element) {
        return "<?xml version=\"1.0\"?><gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:parcours=\"urn:wvicto:parcours:1\">"
            + "<metadata><extensions>" + element + "</extensions></metadata>"
            + "<trk><name>T</name><trkseg>" + pt("") + "</trkseg></trk></gpx>";
    }

    @Test
    void marquePresente() throws Exception {
        File f = ecrire("g.gpx", avecMarque("<parcours:enrichissement application=\"Parcours\" version=\"1\" source=\"o.gpx\" taille=\"42\" sha256=\"abc\"/>"));
        Optional<Enrichissement> e = GpxParser.lireEnrichissement(f);
        assertTrue(e.isPresent());
        assertEquals("Parcours", e.get().application());
        assertEquals(1, e.get().version());
        assertEquals("o.gpx", e.get().source());
        assertEquals(42L, e.get().taille());
        assertEquals("abc", e.get().sha256());
        assertEquals(e.get(), GpxParser.parseFile(f).getEnrichissement());
    }

    @Test
    void marqueMalformee_estIgnoree() throws Exception {
        File f = ecrire("h.gpx", avecMarque("<parcours:enrichissement application=\"Parcours\" version=\"x\" taille=\"y\"/>"));
        assertTrue(GpxParser.lireEnrichissement(f).isEmpty());
    }

    @Test
    void plusieursSegmentsAssembles() throws Exception {
        File f = ecrire("i.gpx", gpx("<trkseg>" + pt("") + "</trkseg><trkseg>" + pt("") + pt("") + "</trkseg>"));
        List<PointGpx> pts = GpxParser.parseFile(f).getPoints();
        assertEquals(3, pts.size());
    }

    @Test
    void sansTrace_exception() throws Exception {
        File f = ecrire("j.gpx", "<gpx version=\"1.1\"></gpx>");
        assertThrows(IllegalArgumentException.class, () -> GpxParser.parseFile(f));
    }
}