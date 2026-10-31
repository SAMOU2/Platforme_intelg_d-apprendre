package fr.ihm2026.ui.commun;

import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/** Dialogues de confirmation explicites (IHM-06, IHM-JFX8). */
public final class Dialogue {

    private Dialogue() {
    }

    /** Retourne true si l'utilisateur confirme l'action destructive ou irréversible. */
    public static boolean confirmer(Window owner, String titre, String message, String libelleAction) {
        ButtonType action = new ButtonType(libelleAction, ButtonBar.ButtonData.OK_DONE);
        ButtonType annuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, action, annuler);
        alert.initOwner(owner);
        alert.setTitle(titre);
        alert.setHeaderText(titre);
        alert.getDialogPane().getStyleClass().add("dialogue");
        Optional<ButtonType> choix = alert.showAndWait();
        return choix.isPresent() && choix.get() == action;
    }

    public static void info(Window owner, String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, new ButtonType("Fermer", ButtonBar.ButtonData.OK_DONE));
        alert.initOwner(owner);
        alert.setTitle(titre);
        alert.setHeaderText(titre);
        alert.getDialogPane().getStyleClass().add("dialogue");
        alert.showAndWait();
    }

    public static void erreur(Window owner, String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, new ButtonType("Fermer", ButtonBar.ButtonData.OK_DONE));
        alert.initOwner(owner);
        alert.setTitle(titre);
        alert.setHeaderText(titre);
        alert.getDialogPane().getStyleClass().add("dialogue");
        alert.showAndWait();
    }
}
