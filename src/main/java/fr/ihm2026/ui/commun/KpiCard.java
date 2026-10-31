package fr.ihm2026.ui.commun;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Carte d'indicateur (tableaux de bord enseignant / apprenant). */
public class KpiCard extends VBox {

    public KpiCard(String valeur, String libelle, String variante) {
        getStyleClass().addAll("card", "kpi");
        if (variante != null) {
            getStyleClass().add(variante);
        }
        Label v = new Label(valeur);
        v.getStyleClass().add("kpi-valeur");
        Label l = new Label(libelle);
        l.getStyleClass().add("kpi-libelle");
        l.setWrapText(true);
        getChildren().addAll(v, l);
        setSpacing(2);
    }
}
