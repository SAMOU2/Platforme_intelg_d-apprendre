package fr.ihm2026.ui.commun;

import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Le PasswordField reste la source unique ; aucune persistance du secret. */
public final class ChampSecret {
    private ChampSecret() { }
    public static HBox envelopper(PasswordField secret) {
        TextField clair = new TextField();
        clair.textProperty().bindBidirectional(secret.textProperty());
        clair.promptTextProperty().bind(secret.promptTextProperty());
        clair.disableProperty().bind(secret.disableProperty());
        clair.getStyleClass().addAll(secret.getStyleClass());
        clair.setAccessibleText("Mot de passe affiché en clair");
        ToggleButton voir = new ToggleButton("Afficher");
        voir.setAccessibleText("Afficher le mot de passe");
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
            voir.setText(affiche ? "Masquer" : "Afficher");
            voir.setAccessibleText(affiche ? "Masquer le mot de passe" : "Afficher le mot de passe");
            secret.setFocusTraversable(!affiche);
            if (affiche) { clair.requestFocus(); clair.positionCaret(clair.getLength()); }
            else { secret.requestFocus(); secret.positionCaret(secret.getLength()); }
        });
        secret.disabledProperty().addListener((o,a,b) -> { if (b) voir.setSelected(false); });
        secret.visibleProperty().addListener((o,a,b) -> { if (!b) voir.setSelected(false); });
        boite.sceneProperty().addListener((o,a,b) -> { if (b == null) voir.setSelected(false); });
        return boite;
    }
}
