package fr.ihm2026.ui.commun;

import fr.ihm2026.core.Router;
import fr.ihm2026.core.StyleVisuel;
import fr.ihm2026.core.ThemeManager;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

/**
 * Barre de titre unifiée, à la manière de macOS (Big Sur et suivants).
 *
 * <p>De gauche à droite : les trois <b>pastilles</b> — fermer, réduire, agrandir, réellement
 * actives — puis le bouton qui <b>réduit ou agrandit la barre latérale</b>, aligné avec elle,
 * les chevrons <b>retour / avance</b>, le <b>titre de l'écran</b> courant, et à droite les
 * réglages d'apparence. Tout ce qui n'agit sur rien n'est pas là : macOS réserve la décoration
 * au strict nécessaire.</p>
 *
 * <p>La barre est translucide : elle laisse transparaître la couleur de la fenêtre, ce qui est le
 * signe distinctif d'une fenêtre macOS par rapport à une barre de titre peinte.</p>
 */
public class BarreTitre extends HBox {

    private static final String CHEVRON_GAUCHE = "M10 2 L4.5 8 L10 14 L8 16 L0.5 8 L8 0 Z";
    private static final String CHEVRON_DROIT = "M6 2 L11.5 8 L6 14 L8 16 L15.5 8 L8 0 Z";
    private static final String ICONE_LATERALE = "M2 3.1 H14 V5 H2 Z "
            + "M2 7.05 H14 V8.95 H2 Z M2 11 H14 V12.9 H2 Z";

    private final Router router;
    private final Stage stage;
    private final Label titre = new Label();
    private final Button retour = new Button();
    private final Button avance = new Button();
    private final Button laterale = new Button();
    private final Button theme = new Button();
    private final Tooltip infoLaterale = new Tooltip();
    /** Action à exécuter quand le style change (installer ou retirer le balayage cathodique). */
    private static Runnable retroActif = () -> { };

    public static void surChangementDeStyle(Runnable action) {
        retroActif = action == null ? () -> { } : action;
    }

    public BarreTitre(Router router, Stage stage) {
        this.router = router;
        this.stage = stage;
        getStyleClass().add("barre-titre");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);

        HBox feux = pastilles();
        // En style rétro, les trois pastilles du cadre macOS n'ont plus de sens : le tube n'a pas
        // de boutons. On les retire de la mise en page (et non de l'écran seulement) pour que la
        // barre de titre se referme sur les chevrons. Fermer reste possible avec Alt+F4.
        feux.setVisible(!StyleVisuel.estRetro());
        feux.setManaged(!StyleVisuel.estRetro());
        titre.getStyleClass().add("titre-fenetre");

        Button style = new Button(StyleVisuel.estRetro() ? "▚" : "▞");
        style.getStyleClass().addAll("bouton-apparence", "bouton-style");
        style.setTooltip(new Tooltip(StyleVisuel.estRetro()
                ? "Style rétro actif — revenir au style macOS"
                : "Passer au style rétro (old school)"));
        style.setAccessibleText("Changer le style visuel : rétro ou moderne");
        style.setOnAction(e -> {
            StyleVisuel.setRetro(!StyleVisuel.estRetro());
            ThemeManager.appliquerStyle(router.scene().getRoot());
            boolean retro = StyleVisuel.estRetro();
            style.setText(retro ? "▚" : "▞");
            feux.setVisible(!retro);
            feux.setManaged(!retro);
            retroActif.run();
        });

        theme.setText(ThemeManager.isDark() ? "☾" : "☀");
        theme.getStyleClass().add("bouton-apparence");
        theme.setAccessibleText("Changer l’apparence claire ou sombre");
        theme.setTooltip(new Tooltip("Apparence : clair / sombre"));
        theme.setOnAction(e -> {
            boolean sombre = !ThemeManager.isDark();
            theme.setText(sombre ? "☾" : "☀");
            ThemeManager.basculer(router.scene(), sombre);
        });

        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);

        Button aide = new Button("Aide clavier");
        aide.getStyleClass().add("bouton-aide-fenetre");
        aide.setAccessibleText("Aide accessibilité et raccourcis clavier, F1");
        aide.setOnAction(e -> Accessibilite.aide(stage));
        // Le bouton de la barre latérale reste en haut à gauche, dans le même axe visuel que
        // la navigation qu'il réduit ou agrandit ; les réglages d'apparence restent à droite.
        getChildren().addAll(feux, boutonLaterale(), retourButton(), avanceButton(), titre,
                espace, style, theme, aide);
    }

    private Button boutonLaterale() {
        laterale.getStyleClass().addAll("bouton-apparence", "bouton-laterale");
        laterale.setGraphic(chevron(ICONE_LATERALE, "icone-bouton-laterale"));
        laterale.setTooltip(infoLaterale);
        laterale.setOnAction(e -> router.basculerLaterale());
        rafraichirLaterale(router.lateraleReduite());
        return laterale;
    }

    /** Aucun panneau latéral n'est disponible sur les écrans publics de connexion. */
    public void afficherBoutonLaterale(boolean afficher) {
        laterale.setVisible(afficher);
        laterale.setManaged(afficher);
    }

    /** Le thème de connexion est fixe ; le réglage reste disponible après connexion. */
    public void afficherBoutonTheme(boolean afficher) {
        theme.setVisible(afficher);
        theme.setManaged(afficher);
    }

    /** Nomme l'action réellement disponible, au lieu d'un libellé générique afficher/masquer. */
    public void rafraichirLaterale(boolean reduite) {
        String action = reduite
                ? "Agrandir la barre latérale avec les libellés"
                : "Réduire la barre latérale en icônes";
        laterale.setAccessibleText(action);
        infoLaterale.setText(action);
        laterale.getStyleClass().remove("bouton-laterale-reduite");
        if (reduite) {
            laterale.getStyleClass().add("bouton-laterale-reduite");
        }
    }

    private Button retourButton() {
        retour.getStyleClass().add("bouton-nav");
        retour.setGraphic(chevron(CHEVRON_GAUCHE, "bouton-nav-icone"));
        retour.setAccessibleText("Revenir à l’écran précédent");
        retour.setTooltip(new Tooltip("Revenir à l'écran précédent"));
        retour.setOnAction(e -> {
            fr.ihm2026.core.Animations.presserChevron(retour, true);
            router.back();
        });
        retour.setDisable(true);
        return retour;
    }

    private Button avanceButton() {
        avance.getStyleClass().add("bouton-nav");
        avance.setGraphic(chevron(CHEVRON_DROIT, "bouton-nav-icone"));
        avance.setAccessibleText("Avancer à l’écran suivant");
        avance.setTooltip(new Tooltip("Avancer (écran quitté par « retour »)"));
        avance.setOnAction(e -> {
            fr.ihm2026.core.Animations.presserChevron(avance, false);
            router.avancer();
        });
        avance.setDisable(true);
        return avance;
    }

    private SVGPath chevron(String chemin, String classe) {
        SVGPath svg = new SVGPath();
        svg.setContent(chemin);
        svg.getStyleClass().add(classe);
        return svg;
    }

    /** Les pastilles macOS : elles ferment, réduisent et agrandissent la vraie fenêtre. */
    private HBox pastilles() {
        Button fermer = pastille("feu-rouge", "Fermer", stage::close);
        Button reduire = pastille("feu-jaune", "Réduire", () -> stage.setIconified(true));
        Button agrandir = pastille("feu-vert", "Agrandir",
                () -> FenetreOutils.basculerAgrandissement(stage));
        // Les symboles au survol sont posés en CSS (.feux:hover .feu) ; ils ne doivent pas peser sur
        // la largeur du bouton, sinon les pastilles rondes deviennent des rectangles.
        for (Button feu : java.util.List.of(fermer, reduire, agrandir)) {
            feu.setMaxSize(22, 22);
            feu.setMinSize(22, 22);
        }
        HBox feux = new HBox(8, fermer, reduire, agrandir);
        feux.getStyleClass().add("feux");
        feux.setAlignment(Pos.CENTER_LEFT);
        return feux;
    }

    private Button pastille(String classe, String info, Runnable action) {
        Button bouton = new Button(switch (classe) {
            case "feu-rouge" -> "×";
            case "feu-jaune" -> "−";
            default -> "□";
        });
        bouton.setAccessibleText(info);
        bouton.getStyleClass().addAll("feu", classe);
        bouton.setTooltip(new Tooltip(info));
        bouton.setFocusTraversable(true);
        bouton.setOnAction(e -> action.run());
        return bouton;
    }

    /** Met à jour le titre de la fenêtre et l'état des chevrons (comme une barre macOS). */
    public void rafraichir(String titreEcran, boolean peutReculer, boolean peutAvancer) {
        titre.setText(titreEcran);
        retour.setDisable(!peutReculer);
        avance.setDisable(!peutAvancer);
        rafraichirLaterale(router.lateraleReduite());
    }

    /** Le titre est aussi celui de la fenêtre : la barre des tâches doit dire la même chose. */
    public String titreCourant() {
        return titre.getText();
    }
}
