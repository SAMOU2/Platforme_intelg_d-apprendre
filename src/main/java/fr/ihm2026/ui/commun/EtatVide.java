package fr.ihm2026.ui.commun;

import javafx.scene.control.Label;

/** État vide explicite : jamais un écran blanc (IHM-14, IHM-JFX9). */
public class EtatVide extends Label {

    public EtatVide(String message) {
        super(message);
        getStyleClass().add("etat-vide");
        setWrapText(true);
    }
}
