package fr.ihm2026.ui.apprenant;

import fr.ihm2026.api.service.CoursApi;
import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.model.CoursVM;
import fr.ihm2026.ui.commun.EnteteVue;
import fr.ihm2026.ui.commun.EtatVide;
import fr.ihm2026.ui.ia.PanneauAssistant;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** A2 — lecture du cours, avec vue comparative des trois langages (IHM-23). */
public class CoursView extends BorderPane {

    public CoursView(Router router) {
        getStyleClass().add("vue");
        CoursVM cours = CoursApi.cours(Session.langage(), Session.rangNiveauCourant());

        setTop(new EnteteVue(cours.getTitre(), "Lisez la section, exécutez l'exemple, puis passez à l'exercice."));

        TabPane onglets = new TabPane();
        onglets.getStyleClass().add("onglets-langages");
        for (Session.Langage langage : Session.Langage.values()) {
            CoursVM contenu = CoursApi.cours(langage, 3);
            Tab onglet = new Tab(langage.label(), contenu(contenu));
            onglet.setClosable(false);
            onglets.getTabs().add(onglet);
        }
        onglets.getSelectionModel().select(Session.langage().ordinal());

        Button exercice = new Button("Faire l'exercice");
        exercice.getStyleClass().add("btn-primary");
        exercice.setOnAction(e -> router.go(Router.EXERCICE));
        Button test = new Button("Passer le test du niveau");
        test.setOnAction(e -> router.go(Router.TEST));

        // L'assistant est disponible pendant la lecture du cours : c'est là que naissent
        // les questions. Il disparaît pendant le test (voir TestView et AideIaApi).
        PanneauAssistant assistant = new PanneauAssistant();
        Button aide = new Button("Aide IA");
        aide.getStyleClass().add("btn-secondaire");
        aide.setOnAction(e -> setRight(getRight() == null ? assistant : null));
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox bas = new HBox(10, aide, espace, exercice, test);
        bas.getStyleClass().add("barre-actions");

        setCenter(onglets);
        setBottom(bas);
        BorderPane.setMargin(onglets, new Insets(4, 0, 0, 0));
    }

    private ScrollPane contenu(CoursVM cours) {
        VBox boite = new VBox(14);
        boite.getStyleClass().add("contenu-cours");
        for (CoursVM.Bloc bloc : cours.getBlocs()) {
            if ("code".equals(bloc.type())) {
                boite.getChildren().add(blocCode(bloc.langage(), bloc.contenu()));
            } else {
                Label texte = new Label(bloc.contenu());
                texte.setWrapText(true);
                texte.getStyleClass().add("paragraphe-cours");
                boite.getChildren().add(texte);
            }
        }
        if (cours.getBlocs().isEmpty()) {
            boite.getChildren().add(new EtatVide("Aucun contenu publié à ce niveau."));
        }
        ScrollPane scroll = new ScrollPane(boite);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-vue");
        return scroll;
    }

    /** Bloc de code coloré : la coloration vient de la feuille CSS du langage (IHM-22). */
    private VBox blocCode(String langage, String code) {
        Label entete = new Label("exemple · " + langage);
        entete.getStyleClass().add("entete-code");
        TextFlow flux = new TextFlow();
        flux.getStyleClass().addAll("bloc-code", "code-" + langage);
        for (String ligne : code.split("\n", -1)) {
            Text t = new Text(ligne + "\n");
            t.getStyleClass().add(classeLigne(ligne));
            flux.getChildren().add(t);
        }
        VBox boite = new VBox(entete, flux);
        boite.getStyleClass().add("carte-code");
        return boite;
    }

    private String classeLigne(String ligne) {
        String l = ligne.trim();
        if (l.startsWith("#") || l.startsWith("//")) {
            return "code-com";
        }
        if (l.startsWith("def ") || l.startsWith("class ") || l.startsWith("public ")
                || l.startsWith("return ") || l.startsWith("#include") || l.startsWith("int ")) {
            return "code-kw";
        }
        if (l.contains("\"")) {
            return "code-str";
        }
        return "code-defaut";
    }
}
