package fr.ihm2026.ui.apprenant;

import fr.ihm2026.api.service.TestApi;
import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.core.TaskRunner;
import fr.ihm2026.ui.commun.ConsoleSortie;
import fr.ihm2026.ui.commun.EnteteVue;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** A3 — test de validation : questions (UNORD) puis soumission. */
public class TestView extends BorderPane {

    private final List<ToggleGroup> groupes = new ArrayList<>();
    private final List<TestApi.Question> questions = TestApi.questions();
    private final ConsoleSortie console = new ConsoleSortie();
    private final Button soumettre = new Button("Soumettre le test");

    public TestView(Router router) {
        getStyleClass().add("vue");
        Session.demarrerEvaluation();   // l'assistance est fermée tant que ce test dure
        setTop(new EnteteVue("Test de validation — niveau 3",
                "Répondez aux questions dans l'ordre que vous voulez, puis soumettez. "
                        + "L'assistant est masqué pendant l'évaluation."));

        VBox liste = new VBox(14);
        liste.getStyleClass().add("contenu-test");
        int numero = 1;
        for (TestApi.Question question : questions) {
            liste.getChildren().add(carteQuestion(numero++, question));
        }

        Label titreCode = new Label("Partie code (facultative)");
        titreCode.getStyleClass().add("titre-bloc");
        TextArea code = new TextArea("def somme(a, b):\n    return a + b");
        code.getStyleClass().addAll("editor", "code-" + Session.langage().code());
        code.setPrefRowCount(6);
        liste.getChildren().addAll(titreCode, code, console);

        ScrollPane scroll = new ScrollPane(liste);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-vue");

        soumettre.getStyleClass().add("btn-primary");
        soumettre.setOnAction(e -> soumettre(router));

        Button abandonner = new Button("Revenir au parcours");
        abandonner.setOnAction(e -> router.go(Router.PARCOURS));

        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox bas = new HBox(10, abandonner, espace, soumettre);
        bas.getStyleClass().add("barre-actions");

        setCenter(scroll);
        setBottom(bas);
        BorderPane.setMargin(scroll, new Insets(4, 0, 0, 0));
    }

    private VBox carteQuestion(int numero, TestApi.Question question) {
        Label enonce = new Label(numero + ". " + question.enonce());
        enonce.getStyleClass().add("enonce-question");
        enonce.setWrapText(true);

        ToggleGroup groupe = new ToggleGroup();
        groupes.add(groupe);
        VBox propositions = new VBox(4);
        for (int i = 0; i < question.propositions().size(); i++) {
            RadioButton choix = new RadioButton(question.propositions().get(i));
            choix.setToggleGroup(groupe);
            choix.setUserData(i);
            choix.getStyleClass().add("proposition");
            propositions.getChildren().add(choix);
        }

        VBox carte = new VBox(6, enonce, propositions);
        carte.getStyleClass().addAll("card", "card-question");
        return carte;
    }

    private void soumettre(Router router) {
        int bonnes = 0;
        for (int i = 0; i < questions.size(); i++) {
            ToggleGroup groupe = groupes.get(i);
            if (groupe.getSelectedToggle() != null
                    && Integer.valueOf(questions.get(i).bonneReponse()).equals(groupe.getSelectedToggle().getUserData())) {
                bonnes++;
            }
        }
        final int total = questions.size();
        final int seuil = 12;
        final int obtenues = bonnes;
        soumettre.setDisable(true);
        console.attente();
        TaskRunner.runSlow(() -> TestApi.soumettre(obtenues, total, seuil), 800, resultat -> {
            soumettre.setDisable(false);
            Session.setDernierResultat(resultat);
            // Si ce poste est relié au serveur de la salle, le résultat y est publié : c'est ce
            // qui permet au poste enseignant de le voir. Hors ligne, l'appel ne fait rien.
            fr.ihm2026.reseau.Reseau.publier(resultat, Session.langage(),
                    Session.rangNiveauCourant());
            router.go(Router.RESULTAT);
        });
    }
}
