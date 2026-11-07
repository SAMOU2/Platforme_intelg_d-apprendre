package fr.ihm2026.ui.commun;

import fr.ihm2026.core.Animations;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Conseils décoratifs dans les marges du login, sans capturer les clics ni le focus. */
final class ConseilsFlottantsConnexion extends Pane {
    private static final double LARGEUR_MIN = 1100;
    private static final double HAUTEUR_MIN = 540;
    // La colonne centrale fait au plus 556 px ; cette réserve protège aussi son ombre.
    private static final double RESERVE_CENTRALE = 640;
    private static final double MARGE_COTE = 26;
    private static final double LARGEUR_MAX_TEXTE = 260;
    private static final double[] HAUTEURS = {0.22, 0.45, 0.68};
    private static final List<String> CONSEILS_GAUCHE = List.of(
            "Un petit pas chaque jour fait avancer tes projets.",
            "Commence par une solution simple, puis améliore-la.",
            "Chaque erreur est une occasion d'apprendre.");
    private static final List<String> CONSEILS_DROITE = List.of(
            "Teste ton code avec de petits exemples.",
            "Lis les messages d'erreur : ils peuvent te guider.",
            "Prends une pause et reviens avec un regard neuf.");

    private final Pane gauche = creerCote();
    private final Pane droite = creerCote();
    private final List<Label> labelsGauche = creerLabels(gauche, CONSEILS_GAUCHE);
    private final List<Label> labelsDroite = creerLabels(droite, CONSEILS_DROITE);
    private final DoubleProperty progression = new SimpleDoubleProperty();
    private Timeline mouvement;

    ConseilsFlottantsConnexion() {
        setId("connexion-conseils-flottants");
        setMouseTransparent(true);
        setPickOnBounds(false);
        setFocusTraversable(false);
        setMinSize(0, 0);
        setPrefSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        getChildren().addAll(gauche, droite);
        progression.addListener((o, avant, apres) -> positionnerMouvement());
        widthProperty().addListener((o, avant, apres) -> actualiser());
        heightProperty().addListener((o, avant, apres) -> actualiser());
        visibleProperty().addListener((o, avant, apres) -> actualiser());
        sceneProperty().addListener((o, avant, apres) -> actualiser());
    }

    private static Pane creerCote() {
        Pane cote = new Pane();
        cote.setManaged(false);
        cote.setMouseTransparent(true);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(cote.widthProperty());
        clip.heightProperty().bind(cote.heightProperty());
        cote.setClip(clip);
        return cote;
    }

    private static List<Label> creerLabels(Pane cote, List<String> textes) {
        return textes.stream().map(texte -> {
            Label label = new Label(texte);
            label.getStyleClass().add("connexion-conseil-volant");
            label.setManaged(false);
            label.setMouseTransparent(true);
            label.setFocusTraversable(false);
            label.setAccessibleText(""); // décoratif : ne s'ajoute pas au parcours clavier
            label.setWrapText(true);
            cote.getChildren().add(label);
            return label;
        }).toList();
    }

    static boolean espaceDisponible(double largeur, double hauteur) {
        return Double.isFinite(largeur) && Double.isFinite(hauteur)
                && largeur >= LARGEUR_MIN && hauteur >= HAUTEUR_MIN;
    }

    static double largeurMarge(double largeur) {
        return Double.isFinite(largeur) ? Math.max(0, (largeur - RESERVE_CENTRALE) / 2) : 0;
    }

    static List<String> conseils() {
        return java.util.stream.Stream.concat(CONSEILS_GAUCHE.stream(), CONSEILS_DROITE.stream()).toList();
    }

    @Override protected void layoutChildren() {
        double largeur = getWidth(), hauteur = getHeight();
        boolean disponible = espaceDisponible(largeur, hauteur);
        gauche.setVisible(disponible);
        droite.setVisible(disponible);
        if (!disponible) return;
        double marge = largeurMarge(largeur);
        gauche.resizeRelocate(0, 0, marge, hauteur);
        droite.resizeRelocate(largeur - marge, 0, marge, hauteur);
        placerLabels(labelsGauche, marge, hauteur, false);
        placerLabels(labelsDroite, marge, hauteur, true);
    }

    private static void placerLabels(List<Label> labels, double largeur, double hauteur, boolean alignerDroite) {
        double largeurTexte = Math.min(LARGEUR_MAX_TEXTE, Math.max(0, largeur - 2 * MARGE_COTE));
        for (int i = 0; i < labels.size(); i++) {
            Label label = labels.get(i);
            double x = alignerDroite ? largeur - MARGE_COTE - largeurTexte : MARGE_COTE;
            label.resizeRelocate(x, hauteur * HAUTEURS[i], largeurTexte, label.prefHeight(largeurTexte));
        }
    }

    private void actualiser() {
        boolean actif = getScene() != null && isVisible()
                && espaceDisponible(getWidth(), getHeight());
        if (!actif || !Animations.actives()) {
            arreter();
        } else if (mouvement == null) {
            mouvement = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(progression, 0, Interpolator.LINEAR)),
                    new KeyFrame(Animations.AMPLE.multiply(50),
                            new KeyValue(progression, 1, Interpolator.LINEAR)));
            mouvement.setCycleCount(Animation.INDEFINITE);
            Animations.suivre(mouvement);
            mouvement.play();
        }
        requestLayout();
    }

    private void positionnerMouvement() {
        for (int i = 0; i < HAUTEURS.length; i++) {
            double phase = 2 * Math.PI * (progression.get() + i / 3.0);
            labelsGauche.get(i).setTranslateY(10 * Math.sin(phase));
            labelsDroite.get(i).setTranslateY(10 * Math.sin(phase + Math.PI));
        }
    }

    private void arreter() {
        Animations.arreter(mouvement);
        mouvement = null;
        progression.set(0);
        positionnerMouvement();
    }
}
