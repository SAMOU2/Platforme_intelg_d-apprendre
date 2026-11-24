package fr.ihm2026.ui.apprenant;

import com.fasterxml.jackson.databind.JsonNode;
import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.reseau.Reseau;
import fr.ihm2026.ui.commun.ActualisationVue;
import fr.ihm2026.ui.commun.EnteteVue;
import fr.ihm2026.ui.commun.EtatVide;
import fr.ihm2026.ui.commun.SuiviPedagogique;
import fr.ihm2026.ui.ia.PanneauAssistant;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A2 → A3 — l'atelier d'un niveau : <b>cours, exercices puis test final dans la même fenêtre</b>.
 *
 * <p>Avant cet écran, chaque étape ouvrait une route différente : l'apprenant quittait le cours
 * pour l'exercice, puis l'exercice pour le test, et perdait le fil. Ici les trois étapes sont
 * trois panneaux d'une seule fenêtre, reliés par la barre d'étapes et le bouton « Continuer » :
 * la forme du cours, la forme des exercices, puis le test — sans changer d'écran.</p>
 *
 * <p>Les règles restent celles du serveur : rien n'est affiché qui ne soit publié, le test final
 * ne démarre que si le niveau est complet, dans sa période, et si l'enseignant a accepté la
 * demande d'inscription ; et l'assistant IA est retiré de la fenêtre dès que l'étape « Test » est
 * ouverte ({@link Session#demarrerEvaluation()} est posé par le panneau de test lui-même).</p>
 */
public class AtelierNiveauView extends BorderPane {

    private final Router router;
    private final VBox entete = new VBox(8);
    private final Button etapeCours = new Button("1 · Cours et explications");
    private final Button etapeExercices = new Button("2 · Entraînement avec IA");
    private final Button etapeTest = new Button("3 · Test final sans IA");
    private final Label fil = new Label("Cours → exercices → test : tout se suit dans cette fenêtre.");
    private final StackPane corps = new StackPane();
    private final VBox panneauCours = new VBox(12);
    private final VBox panneauExercices = new VBox(12);
    private final ScrollPane defilementCours = new ScrollPane(panneauCours);
    private final ScrollPane defilementExercices = new ScrollPane(panneauExercices);
    private final TextArea brouillon = new TextArea();
    private final Label etat = new Label("Chargement du niveau depuis PC1…");
    private final Button revenir = new Button("Revenir au parcours");
    private final Button continuer = new Button("Continuer");
    private PanneauAssistant assistant;
    private TestServeurView test;
    private JsonNode niveau;
    private Session.EtapeAtelier etape = Session.EtapeAtelier.COURS;
    /** Étape demandée à l'ouverture : appliquée dès que le niveau est lu sur PC1. */
    private Session.EtapeAtelier etapeDemandee = Session.EtapeAtelier.COURS;
    private boolean brouillonModifie;
    private boolean remplissage;

    public AtelierNiveauView(Router router) {
        this.router = router;
        getStyleClass().add("vue");
        long niveauDemande = Session.niveauServeurId();

        if (!Reseau.sessionCompteInstallee()) {
            setTop(new EnteteVue("Atelier d'un niveau",
                    "Cours, exercices et test final dans la même fenêtre."));
            setCenter(new EtatVide("L'atelier lit les contenus publiés par votre enseignant sur le "
                    + "serveur central PC1. Connectez ce poste apprenant au serveur, inscrivez-vous "
                    + "auprès d'un enseignant, puis ouvrez un niveau depuis votre parcours."));
            return;
        }
        if (niveauDemande < 1) {
            setTop(new EnteteVue("Atelier d'un niveau",
                    "Cours, exercices et test final dans la même fenêtre."));
            Button choisir = new Button("Choisir un niveau dans mon parcours");
            choisir.getStyleClass().add("btn-primary");
            choisir.setOnAction(e -> router.go(Router.PARCOURS));
            VBox vide = new VBox(12, new EtatVide("Aucun niveau sélectionné : ouvrez un niveau "
                    + "depuis votre parcours pour travailler son cours, ses exercices et son test "
                    + "sans quitter la fenêtre."), choisir);
            vide.getStyleClass().add("colonne");
            setCenter(vide);
            return;
        }

        etape = Session.etapeAtelier();
        etapeDemandee = etape;
        fil.getStyleClass().add("texte-secondaire");
        fil.setWrapText(true);
        HBox barreEtapes = new HBox(8, etapeCours, etapeExercices, etapeTest, fil);
        barreEtapes.getStyleClass().add("barre-actions");
        entete.getChildren().addAll(new EnteteVue("Niveau en cours de chargement",
                "Cours publiés, entraînement avec aide IA, puis test final sans IA."), barreEtapes);
        setTop(entete);

        panneauCours.getStyleClass().add("contenu-cours");
        panneauExercices.getStyleClass().add("contenu-cours");
        defilementCours.setFitToWidth(true);
        defilementExercices.setFitToWidth(true);
        defilementCours.getStyleClass().add("scroll-vue");
        defilementExercices.getStyleClass().add("scroll-vue");
        corps.getChildren().add(defilementCours);
        setCenter(corps);

        brouillon.setAccessibleText("Brouillon de code de l’entraînement, non sauvegardé");
        brouillon.setPromptText("Votre brouillon d'entraînement (non sauvegardé)");
        brouillon.setPrefRowCount(12);
        brouillon.textProperty().addListener((o, avant, apres) -> {
            if (!remplissage) {
                brouillonModifie = true;
            }
        });

        etat.setWrapText(true);
        etat.setFocusTraversable(true);
        etat.getStyleClass().add("texte-secondaire");
        revenir.setOnAction(e -> router.go(Router.PARCOURS));
        continuer.getStyleClass().add("btn-primary");
        continuer.setOnAction(e -> afficherEtape(etape.suivante()));
        Region espace = new Region();
        HBox.setHgrow(espace, javafx.scene.layout.Priority.ALWAYS);
        HBox barreBas = new HBox(10, revenir, espace, continuer);
        barreBas.getStyleClass().add("barre-actions");
        setBottom(new VBox(8, etat, barreBas));

        etapeCours.setOnAction(e -> afficherEtape(Session.EtapeAtelier.COURS));
        etapeExercices.setOnAction(e -> afficherEtape(Session.EtapeAtelier.EXERCICES));
        etapeTest.setOnAction(e -> afficherEtape(Session.EtapeAtelier.TEST));

        String langage = Session.langage().code();
        new ActualisationVue<JsonNode>(this, () -> {
            JsonNode niveaux = SuiviPedagogique.appeler(
                    SuiviPedagogique.demande("niveaux").put("langage", langage));
            boolean accessible = false;
            for (JsonNode n : niveaux) {
                if (n.path("id").asLong() == niveauDemande
                        && !"VERROUILLE".equals(n.path("etat").asText())) {
                    accessible = true;
                }
            }
            if (!accessible) {
                return SuiviPedagogique.demande("niveau-obsolete");
            }
            return SuiviPedagogique.appeler(SuiviPedagogique.demande("contenus")
                    .put("niveau", niveauDemande).put("langage", langage));
        }, this::afficherContenu, erreur -> {
            etat.setText("Connexion interrompue. Nouvelle tentative automatique… " + erreur.getMessage());
        });

        afficherEtape(etape);
    }

    // ------------------------------------------------------------------ contenu
    private void afficherContenu(JsonNode contenu) {
        if ("niveau-obsolete".equals(contenu.path("action").asText(""))) {
            if (brouillonModifie) {
                etat.setText("Votre parcours a changé : ce niveau n'est plus accessible. Votre "
                        + "brouillon reste affiché, copiez-le avant de revenir au parcours à jour.");
            } else {
                Session.setNiveauServeurId(0);
                router.go(Router.PARCOURS);
            }
            return;
        }
        niveau = contenu.path("niveau");
        entete.getChildren().set(0, new EnteteVue(
                "Niveau " + niveau.path("rang").asInt() + " — " + niveau.path("titre").asText(),
                "Cours, exercices et test final de ce niveau, dans cette seule fenêtre."));

        panneauCours.getChildren().clear();
        if (contenu.path("blocs").isEmpty()) {
            panneauCours.getChildren().add(new EtatVide("Cours en préparation : l'enseignant n'a "
                    + "rien publié pour ce niveau."));
        }
        for (JsonNode bloc : contenu.path("blocs")) {
            if ("CODE".equals(bloc.path("type").asText(""))) {
                TextArea exemple = new TextArea(bloc.path("contenu").asText(""));
                exemple.setAccessibleText("Exemple de code du cours, en lecture seule");
                exemple.setEditable(false);
                exemple.setPrefRowCount(5);
                panneauCours.getChildren().add(exemple);
            } else {
                Label texte = new Label(bloc.path("contenu").asText(""));
                texte.setWrapText(true);
                texte.getStyleClass().add("paragraphe-cours");
                panneauCours.getChildren().add(texte);
            }
        }

        panneauExercices.getChildren().clear();
        if (contenu.path("exercices").isEmpty()) {
            panneauExercices.getChildren().add(new EtatVide("Entraînement en préparation."));
        }
        for (JsonNode exercice : contenu.path("exercices")) {
            Label enonce = new Label(exercice.path("enonce").asText(""));
            enonce.setWrapText(true);
            enonce.getStyleClass().add("enonce");
            panneauExercices.getChildren().add(enonce);
        }
        if (!contenu.path("exercices").isEmpty() || brouillonModifie) {
            if (!brouillonModifie) {
                remplissage = true;
                brouillon.setText(contenu.path("exercices").path(0).path("code_depart").asText(""));
                remplissage = false;
            }
            panneauExercices.getChildren().addAll(brouillon, new Label("Brouillon local : aucune "
                    + "exécution automatique ni note. L'aide IA est disponible à cette étape."));
        }

        rafraichirEtapes();
        // L'étape demandée (par exemple « 3 · Test final » depuis le parcours) ne peut être
        // ouverte qu'après la lecture du niveau : c'est elle qui dit si l'étape est prête.
        if (etape != etapeDemandee) {
            afficherEtape(etapeDemandee);
        }
    }

    /** Disponibilités lues sur le serveur : une étape non prête ne s'ouvre pas. */
    private void rafraichirEtapes() {
        boolean coursPret = niveau != null && niveau.path("cours_pret").asBoolean();
        boolean exercicePret = niveau != null && niveau.path("exercice_pret").asBoolean();
        boolean testPret = niveau != null && niveau.path("test_pret").asBoolean();
        boolean periode = niveau != null && niveau.path("periode_ouverte").asBoolean();
        boolean inscrit = niveau != null && niveau.path("affecte").asBoolean();

        etapeCours.setDisable(!coursPret);
        etapeExercices.setDisable(!exercicePret);
        boolean testOuvert = coursPret && exercicePret && testPret && periode && inscrit;
        etapeTest.setDisable(!testOuvert);

        if (etape == Session.EtapeAtelier.TEST) {
            continuer.setVisible(false);
            continuer.setManaged(false);
            etat.setText("Test final : l'assistance IA est retirée de cette fenêtre. La note et la "
                    + "décision seront publiées par votre enseignant après soumission.");
            return;
        }
        continuer.setVisible(true);
        continuer.setManaged(true);
        if (etape == Session.EtapeAtelier.COURS) {
            continuer.setText("Continuer vers les exercices");
            continuer.setDisable(!exercicePret);
            etat.setText(exercicePret
                    ? "Lisez le cours, puis continuez vers les exercices dans cette même fenêtre."
                    : "Entraînement en préparation : l'enseignant n'a pas encore publié d'exercice.");
        } else {
            continuer.setText("Passer au test final — sans IA");
            continuer.setDisable(!testOuvert);
            etat.setText(Session.evaluationEnCours()
                    ? "Une copie est ouverte : l'assistance IA reste suspendue jusqu'à sa soumission. "
                            + "Le test final se poursuit dans cette fenêtre."
                    : testOuvert
                            ? "Entraînement libre avec aide IA. Le test final s'ouvre ensuite dans cette fenêtre."
                            : manqueTest(coursPret, exercicePret, testPret, periode, inscrit));
        }
    }

    private static String manqueTest(boolean coursPret, boolean exercicePret, boolean testPret,
            boolean periode, boolean inscrit) {
        if (!inscrit) {
            return "Test indisponible : votre demande d'inscription n'est pas encore acceptée par "
                    + "un enseignant (écran « Enseignants et inscription »).";
        }
        if (!coursPret || !exercicePret) {
            return "Test indisponible : cours ou entraînement encore en préparation.";
        }
        if (!testPret) {
            return "Test indisponible : aucune question publiée pour ce niveau.";
        }
        return "Test indisponible : hors de sa période d'ouverture.";
    }

    /** Change d'étape sans changer de fenêtre ; le test n'est créé qu'au moment de l'ouvrir. */
    private void afficherEtape(Session.EtapeAtelier nouvelle) {
        if (nouvelle == null) {
            return;
        }
        if (nouvelle == Session.EtapeAtelier.TEST && (niveau == null
                || !niveau.path("test_pret").asBoolean() || !niveau.path("affecte").asBoolean())) {
            rafraichirEtapes();
            return;
        }
        etape = nouvelle;
        etapeDemandee = nouvelle;
        Session.setEtapeAtelier(nouvelle);
        for (Button bouton : new Button[] {etapeCours, etapeExercices, etapeTest}) {
            bouton.getStyleClass().remove("btn-primary");
        }
        switch (nouvelle) {
            case COURS -> {
                corps.getChildren().setAll(defilementCours);
                etapeCours.getStyleClass().add("btn-primary");
                ouvrirAssistant();
            }
            case EXERCICES -> {
                corps.getChildren().setAll(defilementExercices);
                etapeExercices.getStyleClass().add("btn-primary");
                ouvrirAssistant();
            }
            case TEST -> {
                // Créé une seule fois : les réponses saisies restent dans cette fenêtre tant que
                // la copie n'est ni soumise ni abandonnée.
                if (test == null) {
                    test = new TestServeurView(router);
                }
                corps.getChildren().setAll(test);
                etapeTest.getStyleClass().add("btn-primary");
                setRight(null);
            }
        }
        rafraichirEtapes();
    }

    /**
     * L'assistance accompagne les étapes de cours et d'entraînement. Pendant une copie ouverte,
     * le serveur refuse l'IA (RG-13) : le panneau reste donc <b>visible mais désactivé</b>, avec
     * la raison écrite. Auparavant il disparaissait sans explication, ce qui laissait croire que
     * le bouton « Donner un indice » ou le champ de question étaient cassés.
     */
    /**
     * Revient a la copie ouverte. Si l'etape « Test » ne peut pas etre rouverte (question ou
     * acces retires entre-temps), on passe par « Mes copies » : la bas, l'apprenant peut
     * reprendre, soumettre ou abandonner, et le drapeau local d'evaluation est leve par le
     * routeur. Sans cette porte de sortie, le panneau restait verrouille indefiniment.
     */
    private void reprendreEvaluation() {
        afficherEtape(Session.EtapeAtelier.TEST);
        if (Session.evaluationEnCours() && etape != Session.EtapeAtelier.TEST) {
            router.go(Router.RESULTAT);
        }
    }

    private void ouvrirAssistant() {
        if (assistant == null) {
            assistant = new PanneauAssistant(brouillon::getText);
            // Sortie explicite du blocage : la copie ouverte est dans cet atelier, on y retourne.
            assistant.surReprise(this::reprendreEvaluation);
        }
        assistant.verrouiller(Session.evaluationEnCours());
        setRight(assistant);
    }
}
