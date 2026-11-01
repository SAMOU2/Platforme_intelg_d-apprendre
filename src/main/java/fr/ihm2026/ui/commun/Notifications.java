package fr.ihm2026.ui.commun;

import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

/** Une notification non modale à la fois, disponible pour tous les rôles. */
public final class Notifications {
    public enum Type { INFORMATION, SUCCES, AVERTISSEMENT, ERREUR }
    private static Popup courant;
    private static PauseTransition delai;
    private Notifications() { }
    public static void afficher(Window owner, Type type, String message) {
        if (owner == null || !owner.isShowing() || message == null || message.isBlank()) return;
        if (courant != null) courant.hide();
        if (delai != null) delai.stop();
        Label texte = new Label(type.name() + " — " + message);
        texte.setWrapText(true);
        texte.setMaxWidth(360);
        texte.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        Button fermer = new Button("Fermer");
        fermer.setAccessibleText("Fermer la notification");
        VBox carte = new VBox(8, texte, fermer);
        carte.getStyleClass().addAll("notification", "notification-" + type.name().toLowerCase(java.util.Locale.ROOT));
        // Les popups possèdent leur propre scène : copier les feuilles de la fenêtre propriétaire.
        if (owner.getScene() != null) carte.getStylesheets().setAll(owner.getScene().getStylesheets());
        Popup popup = new Popup();
        popup.getContent().add(carte);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        fermer.setOnAction(e -> popup.hide());
        courant = popup;
        popup.show(owner, owner.getX() + Math.max(12, owner.getWidth() - 410), owner.getY() + 65);
        // Les erreurs restent visibles jusqu'à fermeture explicite.
        if (type != Type.ERREUR) {
            delai = new PauseTransition(Duration.seconds(8));
            delai.setOnFinished(e -> popup.hide());
            delai.play();
        }
    }
    public static void erreurActive() {
        Window.getWindows().stream().filter(w -> w.isShowing() && w.isFocused() && !(w instanceof Popup))
                .findFirst().ifPresent(w -> afficher(w, Type.ERREUR,
                        "L'action n'a pas abouti. Consultez le message de l'écran et réessayez."));
    }
}
