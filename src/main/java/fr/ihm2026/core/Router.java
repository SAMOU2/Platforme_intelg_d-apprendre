package fr.ihm2026.core;

import fr.ihm2026.ui.commun.BarreLaterale;
import fr.ihm2026.ui.commun.BarreTitre;
import fr.ihm2026.reseau.IdentitePoste;
import fr.ihm2026.reseau.Reseau;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Navigation de l'application : une route = une fabrique de vue.
 *
 * <p>Depuis la v0.10.0, la fenêtre est composée comme une application macOS : une <b>barre de
 * titre unifiée</b> en haut ({@link BarreTitre}), une <b>barre latérale</b> de navigation à gauche
 * ({@link BarreLaterale}), et la vue au centre. L'écran de connexion échappe à cette composition :
 * il occupe toute la fenêtre, comme une feuille d'ouverture.</p>
 *
 * <p>La navigation garde une histoire dans les deux sens : les chevrons de la barre de titre
 * reviennent en arrière et avancent, avec la même sémantique qu'un navigateur macOS.</p>
 */
public final class Router {

    public static final String LOGIN = "login";
    public static final String RESEAU = "reseau";
    public static final String PARCOURS = "parcours";
    public static final String COURS = "cours";
    public static final String EXERCICE = "exercice";
    public static final String TEST = "test";
    public static final String RESULTAT = "resultat";
    public static final String TABLEAU_BORD = "tableau-bord";
    public static final String CONDITIONS = "conditions";
    public static final String RESULTATS = "resultats";
    public static final String COMPTES = "comptes";

    /** Titre affiché dans la barre de la fenêtre, écran par écran (comme un document macOS). */
    private static final Map<String, String> TITRES = Map.ofEntries(
            Map.entry(LOGIN, "Connexion"),
            Map.entry(RESEAU, "Connexion au serveur"),
            Map.entry(PARCOURS, "Parcours"),
            Map.entry(COURS, "Cours"),
            Map.entry(EXERCICE, "Exercice"),
            Map.entry(TEST, "Test de validation"),
            Map.entry(RESULTAT, "Résultat du test"),
            Map.entry(TABLEAU_BORD, "Tableau de bord"),
            Map.entry(CONDITIONS, "Créer un niveau"),
            Map.entry(RESULTATS, "Résultats du niveau"),
            Map.entry(COMPTES, "Comptes et rôles"));

    private final Map<String, Supplier<Parent>> routes = new LinkedHashMap<>();
    private final Deque<String> precedent = new ArrayDeque<>();
    private final Deque<String> suivant = new ArrayDeque<>();

    private Scene scene;
    private Stage stage;
    private BorderPane root;
    private BarreTitre barreTitre;
    private BarreLaterale barreLaterale;
    private StackPane contenu;
    private String current;
    private boolean retour = false;
    private boolean lateraleVisible = true;

    public void init(Stage stage, Scene scene, BorderPane root) {
        this.stage = stage;
        this.scene = scene;
        this.root = root;
        this.contenu = new StackPane();
        this.contenu.getStyleClass().add("contenu-fenetre");
        this.barreTitre = new BarreTitre(this, stage);
    }

    public void register(String route, Supplier<Parent> view) {
        routes.put(route, view);
    }

    public String current() {
        return current;
    }

    public Scene scene() {
        return scene;
    }

    public Stage stage() {
        return stage;
    }

    /** La barre de titre sert aussi de poignée de déplacement de la fenêtre. */
    public BarreTitre barreTitre() {
        return barreTitre;
    }

    public boolean peutReculer() {
        return !precedent.isEmpty();
    }

    public boolean peutAvancer() {
        return !suivant.isEmpty();
    }

    public void go(String route) {
        String destination = routeSecurisee(route);
        if (current != null && !current.equals(destination)) {
            precedent.push(current);
            suivant.clear();   // une nouvelle destination efface l'avenir, comme un navigateur
        }
        if (!route.equals(destination) && Session.role() != null && Reseau.connecte()
                && !autorise(Session.role(), route)) {
            javafx.scene.control.Alert alerte = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alerte.setTitle("Accès refusé");
            alerte.setHeaderText("Cette fonction n'est pas autorisée pour ce rôle.");
            alerte.setContentText("Le rôle « " + Session.role().label()
                    + " » ne peut pas ouvrir cet écran.");
            if (stage != null) {
                alerte.initOwner(stage);
            }
            alerte.showAndWait();
        }
        afficher(destination);
    }

    /** Chevron « retour » : recule d'un écran et empile l'écran quitté dans l'avenir. */
    public void back() {
        if (precedent.isEmpty()) {
            return;
        }
        String cible = precedent.pop();
        if (current != null) {
            suivant.push(current);
        }
        retour = true;
        afficher(cible);
    }

    /** Chevron « avance » : revient sur l'écran quitté par un retour. */
    public void avancer() {
        if (suivant.isEmpty()) {
            return;
        }
        String cible = suivant.pop();
        if (current != null) {
            precedent.push(current);
        }
        afficher(cible);
    }

    /** Affiche une route sans toucher à l'histoire (utilisé par back/avancer/go). */
    private void afficher(String route) {
        String destination = routeSecurisee(route);
        if (!route.equals(destination)) {
            precedent.clear();
            suivant.clear();
            retour = false;
            route = destination;
        }
        Supplier<Parent> fabrique = routes.get(route);
        if (fabrique == null) {
            throw new IllegalArgumentException("Route inconnue : " + route);
        }
        current = route;
        // Quitter l'écran de test referme la fenêtre d'évaluation, quel que soit le chemin
        // (résultat, parcours, déconnexion) : la règle ne dépend pas des boutons de la vue.
        if (!TEST.equals(route)) {
            Session.terminerEvaluation();
        }

        // Ces deux écrans précèdent l'accès métier : configuration du poste et connexion serveur.
        boolean public_ = LOGIN.equals(route) || RESEAU.equals(route);
        root.getStyleClass().remove("racine--login");
        if (public_) {
            // Écran public : pas de barre latérale, la fenêtre est peinte par le dégradé.
            root.getStyleClass().add("racine--login");
            root.setLeft(null);
        } else {
            boolean nouvelle = barreLaterale == null || barreLaterale.role() != Session.role();
            if (nouvelle) {
                barreLaterale = new BarreLaterale(this);
                barreLaterale.prefWidthProperty().set(lateraleVisible ? Animations.LATERALE_LARGEUR : 0);
                barreLaterale.setVisible(true);
                barreLaterale.setManaged(true);
            }
            barreLaterale.setActive(route);
            root.setLeft(barreLaterale);
        }
        root.setTop(barreTitre);

        // Changement de contenu animé : l'ancien s'efface, le nouveau arrive du bon côté.
        javafx.scene.Node ancien = contenu.getChildren().isEmpty() ? null
                : contenu.getChildren().get(contenu.getChildren().size() - 1);
        javafx.scene.Parent vue = fabrique.get();
        contenu.getChildren().add(vue);
        if (StyleVisuel.estRetro()) {
            // Rétro : changement de chaîne (secousse + scintillements), pas de fondu.
            Animations.balayer(ancien, vue);
        } else {
            Animations.changerEcran(ancien, vue, !retour);
        }
        if (ancien != null) {
            contenu.getChildren().remove(ancien);
        }
        retour = false;
        root.setCenter(contenu);

        barreTitre.rafraichir(TITRES.getOrDefault(route, ""), peutReculer(), peutAvancer());
        if (stage != null) {
            stage.setTitle(TITRES.getOrDefault(route, Config.APP_NAME) + " — " + Config.APP_NAME);
        }
    }

    /**
     * Replie ou déploie la barre latérale, comme le bouton de barre d'outils de macOS (⌃⌘S).
     * L'état est conservé d'un écran à l'autre : c'est un réglage, pas un effet.
     */
    public void basculerLaterale() {
        lateraleVisible = !lateraleVisible;
        if (barreLaterale != null) {
            Animations.basculerLaterale(barreLaterale, lateraleVisible);
        }
    }

    public boolean lateraleVisible() {
        return lateraleVisible;
    }

    /** Retour à l'écran d'accueil du rôle courant. */
    public void home() {
        if (IdentitePoste.role() == null) {
            go(LOGIN);
            return;
        }
        switch (Session.role()) {
            case ENSEIGNANT -> go(CONDITIONS);
            case ADMINISTRATEUR -> go(COMPTES);
            default -> go(PARCOURS);
        }
    }

    /** Vérifie les droits d'écran indépendamment de la barre latérale. */
    public static boolean autorise(Session.Role role, String route) {
        if (LOGIN.equals(route) || RESEAU.equals(route)) {
            return true;
        }
        if (role == null) {
            return false;
        }
        return switch (role) {
            case APPRENANT -> switch (route) {
                case PARCOURS, COURS, EXERCICE, TEST, RESULTAT, TABLEAU_BORD -> true;
                default -> false;
            };
            case ENSEIGNANT -> CONDITIONS.equals(route) || RESULTATS.equals(route);
            case ADMINISTRATEUR -> COMPTES.equals(route);
        };
    }

    private String routeSecurisee(String route) {
        if (!routes.containsKey(route)) {
            throw new IllegalArgumentException("Route inconnue : " + route);
        }
        if (LOGIN.equals(route)) {
            return LOGIN;
        }
        Session.Role rolePoste = IdentitePoste.role();
        if (rolePoste == null) {
            return LOGIN;
        }
        if (Session.role() != rolePoste) {
            Session.setRole(rolePoste);
        }
        if (RESEAU.equals(route)) {
            return RESEAU;
        }
        if (!Reseau.connecte()
                || (!Reseau.authentifie() && !SmokeTest.actif())) {
            if (Reseau.connecte() && !SmokeTest.actif()) {
                Reseau.deconnecter();
            }
            return RESEAU;
        }
        if (!autorise(rolePoste, route)) {
            return switch (rolePoste) {
                case ENSEIGNANT -> CONDITIONS;
                case ADMINISTRATEUR -> COMPTES;
                case APPRENANT -> PARCOURS;
            };
        }
        return route;
    }
}
