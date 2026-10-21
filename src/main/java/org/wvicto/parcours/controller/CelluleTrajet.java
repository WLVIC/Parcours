package org.wvicto.parcours.controller;

import org.wvicto.parcours.model.Trajet;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldListCell;

/**
 * Ligne de la liste des trajets : le texte habituel, précédé d'une pastille qui dit si le
 * trajet est enrichi (✔ vert), à enrichir (○ orange), ou sans fichier (rien). Le symbole
 * porte l'information ; la couleur ne fait que la renforcer.
 */
class CelluleTrajet extends TextFieldListCell<Trajet> {

    @Override
    public void updateItem(Trajet trajet, boolean vide) {
        super.updateItem(trajet, vide);
        if (vide || trajet == null) {
            setGraphic(null);
            setTooltip(null);
            return;
        }
        switch (trajet.getEtatEnrichissement()) {
            case ENRICHI -> {
                setGraphic(pastille("✔", "#2e7d32"));
                String source = trajet.getEnrichissement().source();
                setTooltip(new Tooltip("Copie enrichie (altitudes IGN) de « " + source + " »"));
            }
            case A_ENRICHIR -> {
                setGraphic(pastille("○", "#e65100"));
                setTooltip(new Tooltip("Original brut : altitudes IGN non encore récupérées"));
            }
            case SANS_FICHIER -> {
                setGraphic(null);
                setTooltip(new Tooltip("Trajet calculé, sans fichier GPX"));
            }
        }
    }

    private static Label pastille(String symbole, String couleur) {
        Label label = new Label(symbole);
        label.setStyle("-fx-text-fill: " + couleur + "; -fx-font-weight: bold;");
        return label;
    }
}