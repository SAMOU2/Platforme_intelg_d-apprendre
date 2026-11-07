package fr.ihm2026.ui.commun;

import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Le PasswordField reste la source unique ; aucune persistance du secret. */
public final class ChampSecret {
    private ChampSecret() { }
    public static HBox envelopper(PasswordField secret) {
        return envelopper(secret, false);
    }

    /** Variante compacte utilisée uniquement par l'écran de connexion. */
    public static HBox envelopper(PasswordField secret, boolean icone) {
        TextField clair = new TextField();
        clair.textProperty().bindBidirectional(secret.textProperty());
        clair.promptTextProperty().bind(secret.promptTextProperty());
        clair.disableProperty().bind(secret.disableProperty());
        clair.getStyleClass().addAll(secret.getStyleClass());
        clair.accessibleTextProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> (secret.getAccessibleText() == null ? "Mot de passe" : secret.getAccessibleText())
                        + " — affiché en clair", secret.accessibleTextProperty()));
        secret.setAccessibleHelp("Champ confidentiel masqué. Le bouton Afficher révèle le secret : attention aux personnes présentes.");
        ToggleButton voir = new ToggleButton(icone ? "" : "Afficher");
        if (icone) {
            javafx.scene.shape.SVGPath oeil = new javafx.scene.shape.SVGPath();
            oeil.setContent("M2 12 C5 6 9 4 12 4 C15 4 19 6 22 12 C19 18 15 20 12 20 C9 20 5 18 2 12 Z M12 9 A3 3 0 1 0 12 15 A3 3 0 1 0 12 9");
            oeil.getStyleClass().add("connexion-icone-oeil");
            voir.setGraphic(oeil);
            voir.getStyleClass().add("connexion-secret-bouton");
        }
        voir.setAccessibleText("Afficher : " + nom(secret));
        voir.disableProperty().bind(secret.disableProperty());
        voir.getStyleClass().add("secret-visibilite");
        clair.visibleProperty().bind(voir.selectedProperty());
        clair.managedProperty().bind(clair.visibleProperty());
        secret.opacityProperty().bind(javafx.beans.binding.Bindings.when(voir.selectedProperty()).then(0.0).otherwise(1.0));
        secret.mouseTransparentProperty().bind(voir.selectedProperty());
        StackPane champs = new StackPane(secret, clair);
        HBox.setHgrow(champs, Priority.ALWAYS);
        HBox boite = new HBox(8, champs, voir);
        boite.getStyleClass().add("champ-secret");
        boite.visibleProperty().bind(secret.visibleProperty());
        boite.managedProperty().bind(secret.managedProperty());
        voir.selectedProperty().addListener((o, ancien, affiche) -> {
            if (!icone) voir.setText(affiche ? "Masquer" : "Afficher");
            voir.setAccessibleText((affiche ? "Masquer : " : "Afficher : ") + nom(secret));
            secret.setFocusTraversable(!affiche);
            if (affiche) { clair.requestFocus(); clair.positionCaret(clair.getLength()); }
            else { secret.requestFocus(); secret.positionCaret(secret.getLength()); }
        });
        secret.disabledProperty().addListener((o,a,b) -> { if (b) voir.setSelected(false); });
        secret.visibleProperty().addListener((o,a,b) -> { if (!b) voir.setSelected(false); });
        boite.sceneProperty().addListener((o,a,b) -> { if (b == null) voir.setSelected(false); });
        return boite;
    }
    private static String nom(PasswordField secret) {
        return secret.getAccessibleText() == null ? "mot de passe" : secret.getAccessibleText();
    }

}
