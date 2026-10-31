package fr.ihm2026.ui.commun;

import javafx.scene.control.TextArea;

/** Console de sortie : sortie réelle, erreurs situées, message pédagogique (IHM-JFX7). */
public class ConsoleSortie extends TextArea {

    public ConsoleSortie() {
        super();
        getStyleClass().add("console");
        setEditable(false);
        setWrapText(false);
        setPrefRowCount(8);
        setText("La sortie du programme s'affichera ici.\n");
    }

    public void succes(String sortie) {
        getStyleClass().removeAll("erreur", "succes");
        getStyleClass().add("succes");
        setText(sortie + "\n");
    }

    public void erreur(String message, int ligne) {
        getStyleClass().removeAll("erreur", "succes");
        getStyleClass().add("erreur");
        setText("✗ " + message + (ligne > 0 ? "\n  → vérifiez la ligne " + ligne : "") + "\n");
    }

    public void attente() {
        getStyleClass().removeAll("erreur", "succes");
        setText("Exécution en cours…\n");
    }
}
