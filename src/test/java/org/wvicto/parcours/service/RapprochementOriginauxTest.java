package org.wvicto.parcours.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wvicto.parcours.model.EtatEnrichissement;
import org.wvicto.parcours.model.GpxParser;
import org.wvicto.parcours.model.Trajet;
import org.wvicto.parcours.service.GpxWriter;
import org.wvicto.parcours.service.RapprochementOriginaux;

class RapprochementOriginauxTest {

    @TempDir
    File racine;

    private static String gpx(String nom, double lat) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<gpx version=\"1.1\" creator=\"test\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>" + nom + "</name><trkseg>"
            + "<trkpt lat=\"" + lat + "\" lon=\"2.0\"><ele>100</ele><time>2026-10-07T08:00:00Z</time></trkpt>"
            + "<trkpt lat=\"" + (lat + 0.001) + "\" lon=\"2.0\"><ele>101</ele><time>2026-10-07T08:00:10Z</time></trkpt>"
            + "</trkseg></trk></gpx>";
    }

    private File ecrire(String chemin, String contenu) throws Exception {
        File f = new File(racine, chemin);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), contenu, StandardCharsets.UTF_8);
        return f;
    }

    /** Écrit une copie enrichie de l'original dans un sous-dossier, et renvoie son fichier. */
    private File enrichir(File original) throws Exception {
        Trajet t = GpxParser.parseFile(original);
        return new GpxWriter().ecrireCopieEnrichie(t, new File(original.getParentFile(), "enrichis"));
    }

    private static List<Trajet> charger(File... fichiers) throws Exception {
        List<Trajet> liste = new ArrayList<>();
        for (File f : fichiers) {
            liste.add(GpxParser.parseFile(f));
        }
        return liste;
    }

    @Test
    void originalAvecSaCopie_estTraite() throws Exception {
        File original = ecrire("a.gpx", gpx("A", 48.0));
        File copie = enrichir(original);
        List<Trajet> tous = charger(original, copie);

        List<Trajet> traites = RapprochementOriginaux.originauxTraites(tous);

        assertEquals(1, traites.size());
        assertEquals(original, traites.get(0).getFichierSource());
        assertEquals(EtatEnrichissement.ENRICHI, tous.get(1).getEtatEnrichissement());
        assertEquals(EtatEnrichissement.A_ENRICHIR, tous.get(0).getEtatEnrichissement());
    }

    @Test
    void ordreDeChargementSansImportance() throws Exception {
        File original = ecrire("a.gpx", gpx("A", 48.0));
        File copie = enrichir(original);
        assertEquals(1, RapprochementOriginaux.originauxTraites(charger(copie, original)).size());
    }

    @Test
    void copieSeule_rienACacher() throws Exception {
        File original = ecrire("a.gpx", gpx("A", 48.0));
        File copie = enrichir(original);
        assertTrue(RapprochementOriginaux.originauxTraites(charger(copie)).isEmpty());
    }

    @Test
    void originalSansCopieChargee_resteAEnrichir() throws Exception {
        File original = ecrire("a.gpx", gpx("A", 48.0));
        enrichir(original);                       // la copie existe sur disque mais n'est pas chargée
        assertTrue(RapprochementOriginaux.originauxTraites(charger(original)).isEmpty());
    }

    @Test
    void originalModifieDepuis_resteAEnrichir() throws Exception {
        File original = ecrire("a.gpx", gpx("A", 48.0));
        File copie = enrichir(original);
        Files.writeString(original.toPath(), gpx("A", 48.0).replace("<ele>100</ele>", "<ele>105</ele>"));  // même taille, contenu différent
        assertTrue(RapprochementOriginaux.originauxTraites(charger(original, copie)).isEmpty());
    }

    @Test
    void originalRenommeOuDeplace_estToujoursReconnu() throws Exception {
        File original = ecrire("a.gpx", gpx("A", 48.0));
        File copie = enrichir(original);
        File deplace = new File(racine, "ailleurs/renomme.gpx");
        deplace.getParentFile().mkdirs();
        Files.move(original.toPath(), deplace.toPath());
        assertEquals(1, RapprochementOriginaux.originauxTraites(charger(deplace, copie)).size());
    }

    @Test
    void autresOriginauxNeSontPasCaches() throws Exception {
        File a = ecrire("a.gpx", gpx("A", 48.0));
        File b = ecrire("b.gpx", gpx("B", 49.0));
        File copieA = enrichir(a);
        List<Trajet> traites = RapprochementOriginaux.originauxTraites(charger(a, b, copieA));
        assertEquals(1, traites.size());
        assertEquals(a, traites.get(0).getFichierSource());
    }

    @Test
    void trajetSansFichier_neSeraJamaisCache() throws Exception {
        File original = ecrire("a.gpx", gpx("A", 48.0));
        File copie = enrichir(original);
        Trajet lisse = GpxParser.parseFile(original).lisse(3);
        assertEquals(EtatEnrichissement.SANS_FICHIER, lisse.getEtatEnrichissement());
        List<Trajet> tous = charger(copie);
        tous.add(lisse);
        assertTrue(RapprochementOriginaux.originauxTraites(tous).isEmpty());
    }

    @Test
    void fichierOriginalSupprimeDuDisque_nEstPasTraite() throws Exception {
        File original = ecrire("a.gpx", gpx("A", 48.0));
        File copie = enrichir(original);
        List<Trajet> tous = charger(original, copie);
        Files.delete(original.toPath());
        assertTrue(RapprochementOriginaux.originauxTraites(tous).isEmpty());
    }
}