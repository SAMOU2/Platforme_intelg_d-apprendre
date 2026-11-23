package fr.ihm2026.ui.commun;

import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

/** Dialogues de confirmation explicites (IHM-06, IHM-JFX8). */
public final class Dialogue {

    private Dialogue() {
    }

    /**
     * Retourne le bouton portant ce type dans le dialogue.
     *
     * <p>{@code DialogPane.lookupButton} est declare {@code Node} : la classe n'impose pas le
     * type concret, qui n'est un {@code Button} que si {@code createButton} n'a pas ete
     * surcharge. On verifie donc le type plutot que de convertir a l'aveugle.</p>
     */
    public static Button bouton(DialogPane pane, ButtonType type) {
        Node noeud = pane.lookupButton(type);
        if (noeud instanceof Button bouton)
            return bouton;
        throw new IllegalStateException("Le bouton " + type + " n'est pas un Button : " + noeud);
    }

    public static void appliquerTheme(DialogPane pane, Window owner) {
        pane.getStyleClass().add("dialogue");
        if (owner != null && owner.getScene() != null)
            pane.getStylesheets().setAll(owner.getScene().getStylesheets());
    }

    /** Retourne true si l'utilisateur confirme l'action destructive ou irréversible. */
    public static boolean confirmer(Window owner, String titre, String message, String libelleAction) {
        ButtonType action = new ButtonType(libelleAction, ButtonBar.ButtonData.OK_DONE);
        ButtonType annuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, action, annuler);
        alert.initOwner(owner);
        alert.setTitle(titre);
        alert.setHeaderText(titre);
        appliquerTheme(alert.getDialogPane(), owner);
        Optional<ButtonType> choix = alert.showAndWait();
        return choix.isPresent() && choix.get() == action;
    }

    public static void info(Window owner, String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, new ButtonType("Fermer", ButtonBar.ButtonData.OK_DONE));
        alert.initOwner(owner);
        alert.setTitle(titre);
        alert.setHeaderText(titre);
        appliquerTheme(alert.getDialogPane(), owner);
        alert.showAndWait();
        Notifications.afficher(owner, Notifications.Type.SUCCES, "Action terminée : " + titre);
    }

    public static void erreur(Window owner, String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, new ButtonType("Fermer", ButtonBar.ButtonData.OK_DONE));
        alert.initOwner(owner);
        alert.setTitle(titre);
        alert.setHeaderText(titre);
        appliquerTheme(alert.getDialogPane(), owner);
        alert.showAndWait();
    }
}
