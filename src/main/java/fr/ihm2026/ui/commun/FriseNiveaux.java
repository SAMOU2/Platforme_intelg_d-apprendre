package fr.ihm2026.ui.commun;

import fr.ihm2026.model.NiveauVM;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Frise verticale des niveaux — l'élément identitaire de l'IHM (IHM-05, IHM-28). */
public class FriseNiveaux extends VBox {

    public FriseNiveaux(List<NiveauVM> niveaux, java.util.function.Consumer<NiveauVM> onSelect) {
        getStyleClass().add("frise");
        setSpacing(0);
        for (int i = 0; i < niveaux.size(); i++) {
            NiveauVM niveau = niveaux.get(i);
            Region pastille = new Region();
            pastille.getStyleClass().addAll("pastille", niveau.getEtat().styleClass());

            VBox rail = new VBox(pastille);
            rail.setAlignment(Pos.TOP_CENTER);
            rail.getStyleClass().add("rail");
            rail.setMinWidth(34);

            if (i < niveaux.size() - 1) {
                Region trait = new Region();
                trait.getStyleClass().add("rail-trait");
                trait.setPrefHeight(26);
                rail.getChildren().add(trait);
            }

            CarteNiveau carte = new CarteNiveau(niveau,
                    niveau.getEtat() == fr.ihm2026.model.EtatNiveau.VERROUILLE ? null : () -> onSelect.accept(niveau));
            VBox.setMargin(carte, new javafx.geometry.Insets(0, 0, 14, 0));

            HBox ligne = new HBox(12, rail, carte);
            HBox.setHgrow(carte, javafx.scene.layout.Priority.ALWAYS);
            getChildren().add(ligne);
        }
    }
}
