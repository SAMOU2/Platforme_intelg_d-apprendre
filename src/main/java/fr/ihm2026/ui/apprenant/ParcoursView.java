package fr.ihm2026.ui.apprenant;

import fr.ihm2026.api.service.CoursApi;
import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.core.i18n.UiText;
import fr.ihm2026.model.NiveauVM;
import fr.ihm2026.ui.commun.EnteteVue;
import fr.ihm2026.ui.commun.FriseNiveaux;
import fr.ihm2026.ui.commun.KpiCard;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** A4 — parcours de l'apprenant : la frise des niveaux et l'action suivante. */
public class ParcoursView extends BorderPane {

    public ParcoursView(Router router) {
        getStyleClass().add("vue");
        List<NiveauVM> niveaux = CoursApi.niveaux(Session.langage());

        long valides = niveaux.stream().filter(n -> n.getEtat() == fr.ihm2026.model.EtatNiveau.VALIDE).count();
        NiveauVM courant = niveaux.stream()
                .filter(n -> n.getEtat() == fr.ihm2026.model.EtatNiveau.EN_COURS)
                .findFirst().orElse(niveaux.get(0));

        setTop(new EnteteVue(UiText.get("parcours.titre", Session.langage().label()),
                UiText.get("parcours.sousTitre")));

        FriseNiveaux frise = new FriseNiveaux(niveaux, niveau -> {
            // Contexte transmis à l'assistant : rang du niveau consulté, rien de personnel.
            Session.setRangNiveauCourant(niveau.getRang());
            router.go(Router.COURS);
        });
        VBox colonneGauche = new VBox(frise);
        colonneGauche.getStyleClass().add("colonne");

        ScrollPane scroll = new ScrollPane(colonneGauche);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-vue");

        HBox kpis = new HBox(12,
                new KpiCard(String.valueOf(valides), "niveaux validés", "kpi-succes"),
                new KpiCard(String.valueOf(niveaux.size()), "niveaux du parcours", null),
                new KpiCard(courant.getSeuil() + "/20", "seuil du niveau en cours", "kpi-accent"));

        Label titreSuite = new Label(UiText.get("parcours.prochainTest"));
        titreSuite.getStyleClass().add("titre-bloc");
        Label detail = new Label("Niveau " + courant.getRang() + " — " + courant.getTitre()
                + "\nSeuil " + courant.getSeuil() + "/20 · "
                + courant.getTentativesRestantes() + " tentative(s) restante(s)");
        detail.getStyleClass().add("texte-secondaire");
        detail.setWrapText(true);

        Button test = new Button(UiText.get("action.passerTest"));
        test.getStyleClass().addAll("btn-primary", "wide");
        test.setOnAction(e -> router.go(Router.TEST));

        Button cours = new Button(UiText.get("action.revoirCours"));
        cours.getStyleClass().addAll("wide");
        cours.setOnAction(e -> router.go(Router.COURS));

        VBox droite = new VBox(10, kpis, titreSuite, detail, test, cours);
        droite.getStyleClass().addAll("card", "panneau-lateral");
        droite.setPrefWidth(330);
        droite.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        droite.setMaxHeight(Region.USE_PREF_SIZE);   // pas d'immense carte vide sous le contenu
        droite.setPadding(new Insets(6, 4, 6, 10));

        HBox centre = new HBox(16, scroll, droite);
        HBox.setHgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        setCenter(centre);
        BorderPane.setMargin(centre, new Insets(4, 0, 0, 0));
    }
}
