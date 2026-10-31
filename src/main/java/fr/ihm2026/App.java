package fr.ihm2026;

import fr.ihm2026.core.Config;
import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.core.ThemeManager;
import fr.ihm2026.ui.admin.ComptesView;
import fr.ihm2026.ui.apprenant.CoursView;
import fr.ihm2026.ui.apprenant.ExerciceView;
import fr.ihm2026.ui.apprenant.LoginView;
import fr.ihm2026.ui.apprenant.ParcoursView;
import fr.ihm2026.ui.apprenant.ResultatView;
import fr.ihm2026.ui.apprenant.TableauBordView;
import fr.ihm2026.ui.apprenant.TestView;
import fr.ihm2026.ui.enseignant.ConditionsView;
import fr.ihm2026.ui.enseignant.ResultatsView;
import fr.ihm2026.ui.commun.FenetreOutils;
import java.util.Locale;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Point d'entrée du client JavaFX.
 *
 * <p>Depuis la v0.10.0, la fenêtre n'utilise plus le cadre du système : elle est
 * <b>sans décoration</b>, avec ses propres pastilles, une barre de titre unifiée translucide, une
 * barre latérale de navigation et des coins arrondis avec ombre portée — la composition d'une
 * application macOS. Les gestes que le cadre natif offrait (déplacer, redimensionner, agrandir)
 * sont rendus par {@link FenetreOutils}, sinon l'interface serait plus jolie et moins utilisable.</p>
 */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        Locale.setDefault(Locale.FRANCE);   // aussi pour un lancement direct de App (IDE)

        // Polices embarquées : Inter (proche de SF Pro) et JetBrains Mono pour le code.
        fr.ihm2026.core.Fonts.charger();

        FenetreOutils.installer(stage);

        BorderPane fenetre = new BorderPane();
        fenetre.getStyleClass().addAll("racine", "fenetre");

        StackPane hote = new StackPane(fenetre);
        hote.getStyleClass().add("hote-fenetre");
        hote.setPadding(new Insets(FenetreOutils.MARGE));

        // Les coins arrondis ne sont pas décoratifs : ils demandent un vrai découpage, sinon les
        // enfants (barre latérale, contenu) repeignent des angles droits par-dessus le rayon.
        javafx.scene.shape.Rectangle arrondi = new javafx.scene.shape.Rectangle();
        arrondi.setArcWidth(24);
        arrondi.setArcHeight(24);
        arrondi.widthProperty().bind(hote.widthProperty());
        arrondi.heightProperty().bind(hote.heightProperty());
        hote.setClip(arrondi);

        StackPane calque = new StackPane(hote);
        calque.getStyleClass().add("calque-fenetre");
        // La fenêtre s'adapte à l'écran du poste (v0.14.1) : plus grande que l'écran, elle cache
        // sa barre latérale et le bouton de validation.
        javafx.geometry.Rectangle2D ecran = javafx.stage.Screen.getPrimary().getVisualBounds();
        int[] taille = Config.tailleFenetre(ecran.getWidth(), ecran.getHeight());
        int[] tailleMin = Config.tailleMinFenetre(ecran.getWidth(), ecran.getHeight());
        Scene scene = new Scene(calque, taille[0] + 2 * FenetreOutils.MARGE,
                taille[1] + 2 * FenetreOutils.MARGE, Color.TRANSPARENT);

        Router router = new Router();
        router.init(stage, scene, fenetre);
        FenetreOutils.installerRedimensionnement(stage, scene, calque);

        // Trace de diagnostic (-Dihm.trace.souris=1) : dit quel nœud reçoit la souris et avec quel
        // curseur. C'est ainsi qu'a été établi le défaut de la v0.14.3 — une zone de bord étirée
        // sur toute la fenêtre interdisait tout clic.
        if (Boolean.getBoolean("ihm.trace.souris")) {
            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
                javafx.scene.Node cible = e.getPickResult() == null
                        ? null : e.getPickResult().getIntersectedNode();
                System.out.println("[souris] " + (int) e.getSceneX() + "," + (int) e.getSceneY()
                        + " → " + FenetreOutils.decrire(cible));
            });
        }
        FenetreOutils.rendreDeplacable(router.barreTitre(), stage);

        router.register(Router.LOGIN, LoginView::new);
        router.register(Router.RESEAU, () -> new fr.ihm2026.ui.commun.VueConnexionServeur(
                router, fr.ihm2026.reseau.IdentitePoste.role()));
        router.register(Router.PARCOURS, () -> new ParcoursView(router));
        router.register(Router.COURS, () -> new CoursView(router));
        router.register(Router.EXERCICE, () -> new ExerciceView(router));
        router.register(Router.TEST, () -> new TestView(router));
        router.register(Router.RESULTAT, () -> new ResultatView(router));
        router.register(Router.TABLEAU_BORD, () -> new TableauBordView(router));
        router.register(Router.CONDITIONS, () -> new ConditionsView(router));
        router.register(Router.RESULTATS, () -> new ResultatsView());
        router.register(Router.COMPTES, () -> new ComptesView());

        ThemeManager.apply(scene);
        ThemeManager.appliquerStyle(calque);
        fr.ihm2026.core.Animations.installerEffetPression(scene);
        if (fr.ihm2026.core.StyleVisuel.estRetro()) {
            // Style old school : le tube s'allume, puis le balayage cathodique reste posé.
            fr.ihm2026.ui.commun.Retro.installerBalayage(calque);
            fr.ihm2026.ui.commun.Retro.allumage(fenetre);
        } else {
            fr.ihm2026.core.Animations.ouvrirFenetre(hote);
        }

        stage.setTitle(Config.APP_NAME + " — " + Config.APP_SUB);
        stage.setScene(scene);
        stage.setMinWidth(tailleMin[0]);
        stage.setMinHeight(tailleMin[1]);
        stage.setOnCloseRequest(e -> fr.ihm2026.reseau.Reseau.deconnecter());
        stage.show();
        stage.setX(ecran.getMinX() + (ecran.getWidth() - stage.getWidth()) / 2);
        stage.setY(ecran.getMinY() + (ecran.getHeight() - stage.getHeight()) / 2);

        // Lancée depuis un .cmd, la fenêtre s'ouvre derrière la console : le clavier irait à la
        // console et l'on croirait que l'on ne peut pas écrire dans la fenêtre (v0.14.2).
        stage.toFront();
        stage.requestFocus();

        // Filet : si le contenu impose une fenêtre plus grande que l'écran, on la ramène dedans.
        if (stage.getWidth() > ecran.getWidth() + 2 || stage.getHeight() > ecran.getHeight() + 2) {
            stage.setWidth(Math.min(stage.getWidth(), ecran.getWidth()));
            stage.setHeight(Math.min(stage.getHeight(), ecran.getHeight()));
            stage.setX(ecran.getMinX());
            stage.setY(ecran.getMinY());
        }

        // Chaque poste du réseau local arrive avec son rôle fixé (--role=...). Sans rôle configuré,
        // l'écran de démarrage explique comment lancer un poste ; aucun rôle de démonstration n'est
        // proposé.
        Session.Role roleDuPoste = fr.ihm2026.reseau.IdentitePoste.role();
        if (roleDuPoste != null) {
            Session.setRole(roleDuPoste);
            stage.setTitle(Config.APP_NAME + " — " + fr.ihm2026.reseau.IdentitePoste.libelle());
            router.go(Router.RESEAU);
        } else {
            router.go(Router.LOGIN);
        }

        // Vérification automatique de tous les écrans : -Dihm.smoketest=1
        if (fr.ihm2026.core.SmokeTest.actif()) {
            fr.ihm2026.core.SmokeTest.executer(router, scene);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
