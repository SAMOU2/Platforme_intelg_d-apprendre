package fr.ihm2026.core;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Animations de l'interface, dans le langage de macOS (v0.11.0).
 *
 * <p>macOS n'anime pas pour décorer : il anime pour <b>expliquer un changement d'état</b>. Une
 * fenêtre qui s'ouvre grossit de 1,5 % en 220 ms ; un changement de contenu se fond en 170 ms ;
 * un appui sur un bouton réduit la cible de 4 % en 90 ms. Rien ne dure plus d'un quart de seconde,
 * rien ne rebondit, et le mouvement indique toujours la provenance : un écran « retour » arrive
 * par la gauche, un écran « suivant » par la droite.</p>
 *
 * <p>Deux garde-fous, parce qu'une animation peut aussi être une nuisance :</p>
 * <ul>
 *   <li>{@code -Dihm.animations=off} désactive tout (accessibilité : mouvement réduit) ; dans ce
 *       cas les transitions ne sont même pas créées, les écrans s'affichent directement ;</li>
 *   <li>{@link #terminerTout()} amène toutes les animations en cours à leur état final — c'est ce
 *       que fait le smoketest avant chaque capture, pour que les images ne soient jamais prises au
 *       milieu d'un fondu.</li>
 * </ul>
 */
public final class Animations {

    /**
     * Échelle de temps. Elle vaut 1 en usage normal ; {@code -Dihm.animations.echelle=10} ralentit
     * toutes les animations d'un facteur 10, ce qui permet de les <b>enregistrer image par image</b>
     * pour la démonstration, et de les relire au ralenti pour les vérifier. Ce n'est pas un réglage
     * utilisateur : c'est un instrument de mesure, et il est annoncé comme tel.
     */
    private static final double ECHELLE = echelle();

    private static double echelle() {
        try {
            double v = Double.parseDouble(System.getProperty("ihm.animations.echelle", "1"));
            return v > 0 && v <= 50 ? v : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static Duration duree(double millis) {
        return Duration.millis(millis * ECHELLE);
    }

    /** Appui sur un bouton : la cible se réduit puis revient. */
    public static final Duration RAPIDE = duree(90);
    /** Changement de contenu : fondu + léger déplacement. */
    public static final Duration STANDARD = duree(170);
    /** Ouverture de fenêtre, repli de la barre latérale. */
    public static final Duration AMPLE = duree(220);

    /** Largeur dépliée de la barre latérale : une seule valeur, lue par le routeur et l'animation. */
    public static final double LATERALE_LARGEUR = 244;

    private static boolean actives =
            !"off".equalsIgnoreCase(System.getProperty("ihm.animations", "on"));

    private static final List<Animation> EN_COURS = new ArrayList<>();
    /** Actions à exécuter pour amener un état animé à son état final (texte à moitié écrit…). */
    private static final List<Runnable> FINALISATIONS = new ArrayList<>();

    private Animations() {
    }

    public static boolean actives() {
        return actives;
    }

    public static void desactiver() {
        actives = false;
    }

    public static void activer() {
        actives = true;
    }

    // ------------------------------------------------------------------ primitives
    private static void lancer(Animation animation) {
        if (!actives || animation == null) {
            return;
        }
        EN_COURS.add(animation);
        animation.setOnFinished(e -> EN_COURS.remove(animation));
        animation.play();
    }

    /** Lance une animation en garantissant le nettoyage de la liste, quel que soit son auteur. */
    private static void lancer(Animation animation, Runnable aLaFin) {
        if (!actives || animation == null) {
            return;
        }
        EN_COURS.add(animation);
        animation.setOnFinished(e -> {
            EN_COURS.remove(animation);
            if (aLaFin != null) {
                aLaFin.run();
            }
        });
        animation.play();
    }

    /** Amène immédiatement toutes les animations à leur état final (captures, tests). */
    public static void terminerTout() {
        for (Runnable action : List.copyOf(FINALISATIONS)) {
            try {
                action.run();
            } catch (RuntimeException ignore) {
                // Une finalisation déjà appliquée n'est pas une erreur.
            }
        }
        FINALISATIONS.clear();
        for (Animation animation : List.copyOf(EN_COURS)) {
            try {
                animation.jumpTo(animation.getTotalDuration());
                animation.stop();
            } catch (RuntimeException ignore) {
                // Une animation déjà terminée n'a rien à finir : ce n'est pas une erreur.
            }
        }
        EN_COURS.clear();
    }

    public static int animationsEnCours() {
        return EN_COURS.size();
    }

    /**
     * Déclare une animation construite ailleurs (calque de balayage, rouet) pour qu'elle soit
     * comptée, arrêtée et terminée comme les autres. Sans cela, {@link #terminerTout()} laisserait
     * des animations infinies tourner pendant les captures.
     */
    public static void suivre(Animation animation) {
        if (animation != null && actives && !EN_COURS.contains(animation)) {
            EN_COURS.add(animation);
            javafx.event.EventHandler<javafx.event.ActionEvent> precedente = animation.getOnFinished();
            animation.setOnFinished(e -> {
                EN_COURS.remove(animation);
                if (precedente != null) {
                    precedente.handle(e);
                }
            });
        }
    }

    /** Enregistre une action d'état final, exécutée par {@link #terminerTout()}. */
    public static void aTerminer(Runnable action) {
        if (action != null) {
            FINALISATIONS.add(action);
        }
    }

    // ------------------------------------------------------------------ compositions
    /**
     * Ouverture de fenêtre : fondu de 0,98 à 1 en 220 ms. Assez court pour ne pas retarder le
     * travail, assez pour que l'œil comprenne que la fenêtre vient d'apparaître.
     */
    public static void ouvrirFenetre(Node fenetre) {
        if (!actives) {
            return;
        }
        fenetre.setOpacity(0);
        fenetre.setScaleX(0.985);
        fenetre.setScaleY(0.985);
        FadeTransition fondu = new FadeTransition(AMPLE, fenetre);
        fondu.setFromValue(0);
        fondu.setToValue(1);
        ScaleTransition echelle = new ScaleTransition(AMPLE, fenetre);
        echelle.setFromX(0.985);
        echelle.setFromY(0.985);
        echelle.setToX(1);
        echelle.setToY(1);
        lancer(new ParallelTransition(fondu, echelle));
    }

    /**
     * Changement d'écran : l'ancien s'efface, le nouveau arrive du côté d'où l'on vient.
     *
     * @param avant true pour un écran « suivant » (arrivée par la droite), false pour un retour
     */
    public static void changerEcran(Node ancien, Node nouveau, boolean avant) {
        if (!actives || nouveau == null) {
            return;
        }
        double decalage = avant ? 14 : -14;
        nouveau.setOpacity(0);
        nouveau.setTranslateX(decalage);
        FadeTransition entree = new FadeTransition(STANDARD, nouveau);
        entree.setFromValue(0);
        entree.setToValue(1);
        TranslateTransition glisse = new TranslateTransition(STANDARD, nouveau);
        glisse.setFromX(decalage);
        glisse.setToX(0);
        glisse.setInterpolator(Interpolator.EASE_OUT);
        if (ancien != null && ancien != nouveau) {
            FadeTransition sortie = new FadeTransition(RAPIDE, ancien);
            sortie.setFromValue(ancien.getOpacity());
            sortie.setToValue(0);
            lancer(sortie);
            entree.setOnFinished(e -> ancien.setOpacity(1));
        }
        lancer(new ParallelTransition(entree, glisse));
    }

    /**
     * Transition rétro : l'écran précédent disparaît d'un coup, le nouveau arrive par la droite en
     * une seule secousse, avec trois clignotements de 40 ms. Pas d'accélération, pas de fondu
     * progressif : c'est un changement de chaîne, pas un morphing.
     */
    public static void balayer(javafx.scene.Node ancien, javafx.scene.Node nouveau) {
        if (!actives || nouveau == null) {
            return;
        }
        nouveau.setOpacity(1);
        nouveau.setTranslateX(24);
        Timeline secousse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(nouveau.translateXProperty(), 24),
                        new KeyValue(nouveau.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(40), new KeyValue(nouveau.translateXProperty(), 0),
                        new KeyValue(nouveau.opacityProperty(), 0.35)),
                new KeyFrame(Duration.millis(80), new KeyValue(nouveau.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(120), new KeyValue(nouveau.opacityProperty(), 0.6)),
                new KeyFrame(Duration.millis(160), new KeyValue(nouveau.opacityProperty(), 1)));
        lancer(secousse);
        if (ancien != null && ancien != nouveau) {
            ancien.setOpacity(0);
            aTerminer(() -> ancien.setOpacity(1));
        }
    }

    /** Apparition d'une bulle : fondu + montée de 8 px + grossissement de 2 %. */
    public static void apparaitreBulle(Node bulle) {
        if (!actives) {
            return;
        }
        bulle.setOpacity(0);
        bulle.setTranslateY(8);
        bulle.setScaleX(0.98);
        bulle.setScaleY(0.98);
        FadeTransition fondu = new FadeTransition(STANDARD, bulle);
        fondu.setFromValue(0);
        fondu.setToValue(1);
        TranslateTransition monte = new TranslateTransition(STANDARD, bulle);
        monte.setFromY(8);
        monte.setToY(0);
        monte.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition grossit = new ScaleTransition(STANDARD, bulle);
        grossit.setFromX(0.98);
        grossit.setFromY(0.98);
        grossit.setToX(1);
        grossit.setToY(1);
        lancer(new ParallelTransition(fondu, monte, grossit));
    }

    /**
     * Bascule d'apparence : la racine se voile (opacité 1 → 0,75), le thème est appliqué au point
     * le plus sombre, puis la scène se découvre. Sans cette voile, la fenêtre clignote le temps que
     * le système recharge ses feuilles de style.
     */
    public static void basculerApparence(Node racine, Runnable auMilieu) {
        if (!actives || racine == null) {
            auMilieu.run();
            return;
        }
        Timeline voile = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(racine.opacityProperty(), 1)),
                new KeyFrame(RAPIDE, new KeyValue(racine.opacityProperty(), 0.75,
                        Interpolator.EASE_BOTH)),
                new KeyFrame(RAPIDE.add(STANDARD), new KeyValue(racine.opacityProperty(), 1,
                        Interpolator.EASE_BOTH)));
        // Le thème change exactement quand la voile est la plus sombre : aucune image intermédiaire
        // ne montre un mélange des deux palettes.
        Timeline application = new Timeline(new KeyFrame(RAPIDE, e -> auMilieu.run()));
        lancer(application);
        lancer(voile);
    }

    /**
     * Repli ou déploiement de la barre latérale : la largeur glisse, l'opacité suit, et le contenu
     * reprend la place progressivement (comme ⌃⌘S dans le Finder).
     *
     * <p>La largeur est animée par {@code prefWidth}, mais aussi par {@code minWidth} : la feuille
     * de style impose une largeur minimale de 244 px, et une animation qui ne touche que la
     * préférence ne produit <b>aucun</b> mouvement visible. Constaté à la première exécution : la
     * barre ne bougeait pas alors que l'animation « tournait ».</p>
     */
    public static void basculerLaterale(Region laterale, boolean visible) {
        final double ouverte = LATERALE_LARGEUR;
        if (!actives) {
            laterale.setVisible(visible);
            laterale.setManaged(visible);
            laterale.setMinWidth(visible ? ouverte : 0);
            laterale.setPrefWidth(visible ? ouverte : 0);
            return;
        }
        double depart = visible ? 0 : Math.max(0, laterale.getWidth());
        laterale.setVisible(true);
        laterale.setManaged(true);
        laterale.setMinWidth(0);
        laterale.setPrefWidth(depart);
        laterale.setOpacity(visible ? 0.4 : 1);

        Timeline glisse = new Timeline(new KeyFrame(AMPLE,
                new KeyValue(laterale.prefWidthProperty(), visible ? ouverte : 0, Interpolator.EASE_BOTH),
                new KeyValue(laterale.opacityProperty(), visible ? 1 : 0.4, Interpolator.EASE_BOTH)));
        lancer(glisse, () -> {
            if (!visible) {
                laterale.setVisible(false);
                laterale.setManaged(false);
            } else {
                laterale.setMinWidth(ouverte);
            }
            laterale.setOpacity(1);
            laterale.requestLayout();
        });
    }

    /** Chevron retour / avance : petit déplacement latéral à l'appui. */
    public static void presserChevron(Node chevron, boolean versLaGauche) {
        if (!actives) {
            return;
        }
        double depart = chevron.getTranslateX();
        Timeline appui = new Timeline(
                new KeyFrame(RAPIDE, new KeyValue(chevron.translateXProperty(),
                        versLaGauche ? -2.5 : 2.5)),
                new KeyFrame(RAPIDE.multiply(2), new KeyValue(chevron.translateXProperty(), depart)));
        lancer(appui);
    }

    /**
     * Effet d'appui sur tous les boutons de la scène : la cible se réduit de 4 % puis revient.
     * Installé une fois, par filtre d'événement — aucune vue n'a à s'en occuper.
     */
    public static void installerEffetPression(javafx.scene.Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() instanceof Button bouton && actives) {
                reduire(bouton, 0.955);
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getTarget() instanceof Button bouton) {
                reduire(bouton, 1);
            }
        });
    }

    private static void reduire(Node noeud, double facteur) {
        ScaleTransition echelle = new ScaleTransition(RAPIDE, noeud);
        echelle.setToX(facteur);
        echelle.setToY(facteur);
        echelle.setInterpolator(Interpolator.EASE_OUT);
        lancer(echelle);
    }

    /** Remet un nœud dans son état neutre : à appeler après une capture ou un test d'animation. */
    public static void reinitialiser(Node noeud) {
        noeud.setOpacity(1);
        noeud.setTranslateX(0);
        noeud.setTranslateY(0);
        noeud.setScaleX(1);
        noeud.setScaleY(1);
    }
}
