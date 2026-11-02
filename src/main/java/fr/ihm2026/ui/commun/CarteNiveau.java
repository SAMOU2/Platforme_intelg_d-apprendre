package fr.ihm2026.ui.commun;

import fr.ihm2026.model.NiveauVM;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Carte d'un niveau : rang, titre, état et raison chiffrée (IHM-01, IHM-02).
 * L'état est porté par une pastille colorée + un libellé + la classe CSS de l'état.
 */
public class CarteNiveau extends VBox {
    private final Runnable action;

    public CarteNiveau(NiveauVM niveau, Runnable action) {
        this.action = action;
        setAccessibleText("Niveau " + niveau.getRang() + ", " + niveau.getTitre() + ", "
                + niveau.getEtat().label() + ". " + niveau.getExplication() + ". Seuil : "
                + niveau.getSeuil() + " sur 20. " + niveau.getTentativesRestantes() + " tentative(s) restante(s).");
        getStyleClass().addAll("card", "card-niveau", niveau.getEtat().styleClass());

        Label rang = new Label(String.valueOf(niveau.getRang()));
        rang.getStyleClass().add("rang-niveau");

        Label titre = new Label(niveau.getTitre());
        titre.getStyleClass().add("titre-niveau");

        Label etat = new Label(niveau.getEtat().label());
        etat.getStyleClass().addAll("chip-etat", niveau.getEtat().styleClass());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox ligne = new HBox(10, rang, titre, spacer, etat);
        ligne.getStyleClass().add("ligne-niveau");

        Label explication = new Label(niveau.getExplication());
        explication.getStyleClass().add("explication-niveau");
        explication.setWrapText(true);

        getChildren().addAll(ligne, explication);
        setSpacing(4);

        Tooltip.install(this, new Tooltip("Seuil : " + niveau.getSeuil() + "/20 · "
                + niveau.getTentativesRestantes() + " tentative(s) restante(s)"));

        if (action != null) {
            setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
            setFocusTraversable(true);
            setOnMouseClicked(e -> activer());
            setOnKeyPressed(e -> {
                if (!e.isAltDown() && !e.isControlDown() && !e.isMetaDown()
                        && (e.getCode() == javafx.scene.input.KeyCode.ENTER
                        || e.getCode() == javafx.scene.input.KeyCode.SPACE)) {
                    activer();
                    e.consume();
                }
            });
        }
    }
    private void activer() {
        if (action != null && !isDisabled() && isVisible()) action.run();
    }

    @Override public void executeAccessibleAction(javafx.scene.AccessibleAction action, Object... parametres) {
        if (action == javafx.scene.AccessibleAction.FIRE) activer();
        else super.executeAccessibleAction(action, parametres);
    }

}
