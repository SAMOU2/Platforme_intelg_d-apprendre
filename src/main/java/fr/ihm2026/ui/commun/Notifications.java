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
    private static final Object DERNIERE_NOTIFICATION = new Object();
    private static Popup courant;
    private static PauseTransition delai;
    private Notifications() { }
    public static void afficher(Window owner, Type type, String message) {
        if (owner == null || !owner.isShowing() || message == null || message.isBlank()) return;
        if (owner.getScene() != null)
            owner.getScene().getRoot().getProperties().put(DERNIERE_NOTIFICATION, type.name() + " — " + message);
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
    /** Relecture explicite, sans voler le focus à l'arrivée d'un message asynchrone. */
    public static void relire(Window owner) {
        if (owner == null || owner.getScene() == null) return;
        Object derniere = owner.getScene().getRoot().getProperties().get(DERNIERE_NOTIFICATION);
        Alert alerte = new Alert(Alert.AlertType.INFORMATION,
                derniere instanceof String texte ? texte : "Aucune notification à relire dans cette session.", ButtonType.CLOSE);
        alerte.initOwner(owner);
        alerte.setTitle("Dernière notification");
        alerte.setHeaderText("Dernier message reçu");
        Dialogue.appliquerTheme(alerte.getDialogPane(), owner);
        alerte.showAndWait();
    }

    /** Ne pas exposer le message d'un compte à l'utilisateur suivant dans la même fenêtre. */
    public static void effacer(Window owner) {
        if (owner != null && owner.getScene() != null)
            owner.getScene().getRoot().getProperties().remove(DERNIERE_NOTIFICATION);
        if (courant != null && courant.getOwnerWindow() == owner) {
            courant.hide();
            courant = null;
            if (delai != null) { delai.stop(); delai = null; }
        }
    }

    public static void erreurActive() {
        Window.getWindows().stream().filter(w -> w.isShowing() && w.isFocused() && !(w instanceof Popup))
                .findFirst().ifPresent(w -> afficher(w, Type.ERREUR,
                        "L'action n'a pas abouti. Consultez le message de l'écran et réessayez."));
    }
}
