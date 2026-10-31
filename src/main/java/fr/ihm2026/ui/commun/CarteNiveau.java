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

    public CarteNiveau(NiveauVM niveau, Runnable action) {
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
            setOnMouseClicked(e -> action.run());
        }
    }
}
