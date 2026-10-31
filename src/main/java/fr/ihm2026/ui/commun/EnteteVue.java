package fr.ihm2026.ui.commun;

import fr.ihm2026.core.Session;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** En-tête commun aux écrans : titre, sous-titre et badge du parcours courant. */
public class EnteteVue extends HBox {

    public EnteteVue(String titre, String sousTitre) {
        getStyleClass().add("entete-vue");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        Label t = new Label(titre);
        t.getStyleClass().add("titre-vue");
        Label s = new Label(sousTitre);
        s.getStyleClass().add("sous-titre-vue");
        s.setWrapText(true);
        VBox textes = new VBox(2, t, s);

        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);

        getChildren().addAll(textes, espace, new BadgeLangage(Session.langage()));
    }
}
