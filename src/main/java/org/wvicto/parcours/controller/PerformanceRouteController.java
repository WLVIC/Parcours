package org.wvicto.parcours.controller;

import javafx.fxml.FXML;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.util.StringConverter;
import org.wvicto.parcours.model.PerformanceRoute;
import org.wvicto.parcours.model.Route;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Affiche, pour une route donnée, le temps de parcours de chaque trajet correspondant
 * en fonction de sa date (nuage de points), ainsi que le record actuel.
 */
public class PerformanceRouteController {
    @FXML
    private Label titreLabel;
    @FXML
    private Label nombreLabel;
    @FXML
    private Label recordLabel;
    @FXML
    private ScatterChart<Number, Number> graphiquePerformance;
    @FXML
    private NumberAxis axeDate;

    /**
     * Alimente la fenêtre. À appeler juste après le chargement du FXML, avant d'afficher
     * la fenêtre (pas de logique de chargement ici : les données arrivent déjà calculées).
     */
    public void afficher(Route route, List<PerformanceRoute.Performance> performances) {
        titreLabel.setText("Temps de parcours : " + route.getNom());
        nombreLabel.setText(performances.size() + " trajet(s) correspondant(s) trouvé(s)");

        PerformanceRoute.Performance record = PerformanceRoute.record(performances);
        if (record != null) {
            recordLabel.setText(String.format(
                "🏆 Record : %s, le %s",
                formaterDuree(record.duree()),
                record.trajet().getDate()
            ));
        }

        configurerAxeDate(performances);

        XYChart.Series<Number, Number> serie = new XYChart.Series<>();
        for (PerformanceRoute.Performance performance : performances) {
            double jour = performance.trajet().getDate().toEpochDay();
            double minutes = performance.duree().toSeconds() / 60.0;
            serie.getData().add(new XYChart.Data<>(jour, minutes));
        }
        graphiquePerformance.getData().add(serie);
    }

    /**
     * Fixe les bornes de l'axe X sur la plage réelle des dates, avec une semaine de
     * marge de chaque côté (plutôt que l'auto-ranging par défaut, qui part de 0).
     * Le pas des graduations s'adapte à l'étendue pour rester lisible (~6 graduations).
     */
    private void configurerAxeDate(List<PerformanceRoute.Performance> performances) {
        LocalDate dateMin = performances.stream()
            .map(p -> p.trajet().getDate())
            .min(LocalDate::compareTo)
            .orElseThrow();
        LocalDate dateMax = performances.stream()
            .map(p -> p.trajet().getDate())
            .max(LocalDate::compareTo)
            .orElseThrow();

        long borneInf = dateMin.minusWeeks(1).toEpochDay();
        long borneSup = dateMax.plusWeeks(1).toEpochDay();

        axeDate.setAutoRanging(false);
        axeDate.setLowerBound(borneInf);
        axeDate.setUpperBound(borneSup);
        axeDate.setTickUnit(Math.max(1, Math.round((borneSup - borneInf) / 6.0)));

        axeDate.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number valeur) {
                return LocalDate.ofEpochDay(valeur.longValue())
                    .format(DateTimeFormatter.ofPattern("dd/MM/yy"));
            }

            @Override
            public Number fromString(String texte) {
                return null; // axe en lecture seule, jamais utilisé dans ce sens
            }
        });
    }

    private String formaterDuree(Duration duree) {
        long minutes = duree.toMinutes();
        long secondes = duree.minusMinutes(minutes).getSeconds();
        return String.format("%d min %02d s", minutes, secondes);
    }
}