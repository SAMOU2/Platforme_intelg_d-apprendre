package fr.ihm2026.ui.apprenant;

import fr.ihm2026.api.service.CoursApi;
import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.model.NiveauVM;
import fr.ihm2026.ui.commun.EnteteVue;
import fr.ihm2026.ui.commun.KpiCard;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** A4 — tableau de bord : deux vues (par langage / par niveau) et remédiation. */
public class TableauBordView extends BorderPane {

    public TableauBordView(Router router) {
        getStyleClass().add("vue");
        setTop(new EnteteVue("Mon tableau de bord", "Suivez votre progression dans les trois parcours."));

        TabPane onglets = new TabPane();
        onglets.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (Session.Langage langage : Session.Langage.values()) {
            onglets.getTabs().add(new Tab(langage.label(), vueLangage(langage, router)));
        }
        onglets.getSelectionModel().select(Session.langage().ordinal());

        setCenter(onglets);
        BorderPane.setMargin(onglets, new Insets(4, 0, 0, 0));
    }

    private VBox vueLangage(Session.Langage langage, Router router) {
        List<NiveauVM> niveaux = CoursApi.niveaux(langage);
        long valides = niveaux.stream().filter(n -> n.getEtat() == fr.ihm2026.model.EtatNiveau.VALIDE).count();
        long enCours = niveaux.stream().filter(n -> n.getEtat() == fr.ihm2026.model.EtatNiveau.EN_COURS).count();
        int noteMax = niveaux.stream().mapToInt(NiveauVM::getNote).max().orElse(0);

        HBox kpis = new HBox(12,
                new KpiCard(String.valueOf(valides), "niveaux validés", "kpi-succes"),
                new KpiCard(String.valueOf(enCours), "niveau en cours", "kpi-accent"),
                new KpiCard(noteMax + "/20", "meilleure note", null));

        Label titre = new Label("Ce qui vous bloque");
        titre.getStyleClass().add("titre-bloc");
        VBox blocages = new VBox(4);
        niveaux.stream().filter(n -> n.getEtat() != fr.ihm2026.model.EtatNiveau.VALIDE)
                .forEach(n -> {
                    Label l = new Label("• Niveau " + n.getRang() + " — " + n.getTitre() + " : " + n.getExplication());
                    l.setWrapText(true);
                    l.getStyleClass().add("texte-secondaire");
                    blocages.getChildren().add(l);
                });

        Button revoir = new Button("Revoir le cours du niveau en cours");
        revoir.setOnAction(e -> router.go(Router.COURS));
        Button refaire = new Button("Repasser le test");
        refaire.getStyleClass().add("btn-primary");
        refaire.setOnAction(e -> router.go(Router.TEST));

        VBox contenu = new VBox(12, kpis, titre, blocages, new HBox(10, revoir, refaire));
        contenu.getStyleClass().add("colonne");
        contenu.setPadding(new Insets(14));
        return contenu;
    }
}
