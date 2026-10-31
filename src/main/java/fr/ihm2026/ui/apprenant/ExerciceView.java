package fr.ihm2026.ui.apprenant;

import fr.ihm2026.api.mock.MockBackend;
import fr.ihm2026.api.service.TestApi;
import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.core.TaskRunner;
import fr.ihm2026.ui.commun.ConsoleSortie;
import fr.ihm2026.ui.commun.EnteteVue;
import fr.ihm2026.ui.ia.PanneauAssistant;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** A2 — exercice : écrire, exécuter, lire l'erreur, réessayer (boucle du modèle de tâches). */
public class ExerciceView extends BorderPane {

    private static final String CODE_DEPART = switch (Session.langage()) {
        case PYTHON -> "def moyenne(notes):\n    if not notes:\n        return 0\n    return sum(notes) / len(notes)\n\n"
                + "print(moyenne([12, 15, 9]))";
        case JAVA -> "public double moyenne(int[] notes) {\n    double somme = 0;\n    for (int n : notes) {\n"
                + "        somme += n;\n    }\n    return notes.length == 0 ? 0 : somme / notes.length;\n}";
        case CPP -> "#include <iostream>\n\ndouble moyenne(int notes[], int taille) {\n    double somme = 0;\n"
                + "    for (int i = 0; i < taille; i++) {\n        somme += notes[i];\n    }\n"
                + "    return taille == 0 ? 0 : somme / taille;\n}";
    };

    private final TextArea editeur = new TextArea(CODE_DEPART);
    private final ConsoleSortie console = new ConsoleSortie();
    private final PanneauAssistant assistant = new PanneauAssistant();

    public ExerciceView(Router router) {
        getStyleClass().add("vue");

        setTop(new EnteteVue("Exercice 3.2 — calculer une moyenne",
                "Écrivez la fonction, exécutez-la, corrigez jusqu'à ce que la sortie soit correcte."));

        Label enonce = new Label("Écrire une fonction qui renvoie la moyenne d'une liste de notes. "
                + "Une liste vide doit renvoyer 0. La sortie attendue est 12.0.");
        enonce.setWrapText(true);
        enonce.getStyleClass().add("enonce");

        editeur.getStyleClass().addAll("editor", "code-" + Session.langage().code());
        editeur.setPrefRowCount(16);

        Button executer = new Button("Exécuter");
        executer.getStyleClass().add("btn-primary");
        executer.setOnAction(e -> executer());

        Button reinitialiser = new Button("Réinitialiser");
        reinitialiser.setOnAction(e -> {
            editeur.setText(CODE_DEPART);
            console.attente();
            console.setText("Éditeur réinitialisé.\n");
        });

        Button aide = new Button("Aide IA");
        aide.getStyleClass().add("btn-secondaire");
        aide.setOnAction(e -> basculerAssistant());

        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);

        HBox barre = new HBox(10, executer, reinitialiser, espace, aide);
        barre.getStyleClass().add("barre-actions");

        VBox zoneEdition = new VBox(8, enonce, editeur, console, barre);
        VBox.setVgrow(editeur, Priority.ALWAYS);
        zoneEdition.getStyleClass().add("colonne");

        HBox centre = new HBox(14, zoneEdition);
        HBox.setHgrow(zoneEdition, Priority.ALWAYS);
        setCenter(centre);
        BorderPane.setMargin(centre, new Insets(4, 0, 0, 0));
    }

    private void basculerAssistant() {
        if (getRight() == null) {
            setRight(assistant);
        } else {
            setRight(null);
        }
    }

    /** L'exécution passe par le service : jamais dans le client (BNF-03). */
    private void executer() {
        console.attente();
        String code = editeur.getText();
        Session.Langage langage = Session.langage();
        TaskRunner.runSlow(() -> TestApi.executer(code, langage), 700, resultat -> {
            if (resultat == null) {
                console.erreur("Serveur indisponible : réessayez dans un instant.", 0);
                return;
            }
            MockBackend.ExecutionSimulee execution = resultat;
            if (execution.ok()) {
                console.succes("✓ " + execution.sortie());
            } else {
                console.erreur(execution.erreur(), execution.ligneErreur());
            }
        });
    }
}
