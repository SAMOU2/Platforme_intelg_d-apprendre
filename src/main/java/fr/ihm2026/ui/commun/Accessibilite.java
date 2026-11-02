package fr.ihm2026.ui.commun;

import java.util.List;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;

/** Aide locale et déplacement entre régions ; aucun raccourci ne soumet un formulaire. */
public final class Accessibilite {
    private Accessibilite() { }

    public static void region(Node noeud, String nom) {
        noeud.setAccessibleRole(javafx.scene.AccessibleRole.PARENT);
        noeud.setFocusTraversable(true);
        noeud.setAccessibleText(nom);
        noeud.setAccessibleHelp("F6 : région suivante. Tab : commande suivante. F1 : aide clavier.");
    }

    /** À installer une seule fois par scène. Tab et les commandes d'édition restent natifs. */
    public static void installer(Scene scene, Supplier<List<Node>> regions) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isAltDown() || e.isControlDown() || e.isMetaDown()) return;
            if (e.getCode() == KeyCode.F1 && !e.isShiftDown()) {
                e.consume();
                aide(scene.getWindow());
            } else if (e.getCode() == KeyCode.F8 && !e.isShiftDown()) {
                e.consume();
                Notifications.relire(scene.getWindow());
            } else if (e.getCode() == KeyCode.F6) {
                List<Node> disponibles = regions.get().stream()
                        .filter(n -> n != null && n.isVisible() && !n.isDisabled()
                                && n.getScene() == scene && n.getBoundsInParent().getWidth() > 0)
                        .toList();
                if (disponibles.isEmpty()) return;
                int courant = -1;
                for (int i = 0; i < disponibles.size(); i++) {
                    for (Node n = scene.getFocusOwner(); n != null; n = n.getParent()) {
                        if (n == disponibles.get(i)) { courant = i; break; }
                    }
                }
                int cible = courant < 0 ? (e.isShiftDown() ? disponibles.size() - 1 : 0)
                        : Math.floorMod(courant + (e.isShiftDown() ? -1 : 1), disponibles.size());
                disponibles.get(cible).requestFocus();
                e.consume();
            }
        });
    }

    public static void aide(Window owner) {
        String texte = "Tab : commande suivante. Maj + Tab : commande précédente.\n"
                + "F6 : région suivante (commandes de fenêtre, navigation, contenu).\n"
                + "Maj + F6 : région précédente. F1 : cette aide.\n"
                + "F8 : relire la dernière notification, même après sa disparition.\n"
                + "Entrée ou Espace : activer un bouton. Flèches : parcourir une liste ou lire un champ de texte.\n"
                + "Échap : fermer cette aide. Alt + F4 : fermer la fenêtre.\n\n"
                + "Avec NVDA sous Windows, utilisez aussi ses commandes de lecture pour les titres, cours et messages. "
                + "Les changements de texte ne sont pas tous annoncés automatiquement. "
                + "Le résultat de connexion est accessible au clavier dans le formulaire.\n\n"
                + "Le bouton Afficher révèle votre mot de passe : ne l'utilisez que si vous le souhaitez. "
                + "Les raccourcis ne valident ni ne soumettent un test à votre place.\n\n"
                + "Cette version apporte une première base d'accessibilité. Les éditeurs de code, tableaux, "
                + "cours riches et notifications doivent encore être vérifiés avec un lecteur d'écran.";
        Alert alerte = new Alert(Alert.AlertType.INFORMATION, texte, ButtonType.CLOSE);
        if (owner != null) alerte.initOwner(owner);
        alerte.setTitle("Accessibilité et clavier");
        alerte.setHeaderText("Utiliser la plateforme sans souris");
        Dialogue.appliquerTheme(alerte.getDialogPane(), owner);
        alerte.getDialogPane().setMinWidth(550);
        alerte.showAndWait();
    }
}
