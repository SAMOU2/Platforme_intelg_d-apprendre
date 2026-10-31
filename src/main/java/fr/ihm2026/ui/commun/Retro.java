package fr.ihm2026.ui.commun;

import fr.ihm2026.core.Animations;
import fr.ihm2026.core.StyleVisuel;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Couche « old school » (v0.12.0) : balayage cathodique, machine à écrire, rouet de patience.
 *
 * <p>Ce que faisaient les animations d'avant, et que l'on a réintroduit ici, sans pastiche
 * gratuit :</p>
 * <ul>
 *   <li><b>balayage cathodique</b> — des lignes horizontales de 1 px tous les 3 px, plus un
 *       scintillement d'amplitude 6 % : c'est le tube, pas la fenêtre ;</li>
 *   <li><b>machine à écrire</b> — le texte de l'assistant s'écrit caractère par caractère derrière
 *       un curseur plein, comme sur un Minitel ;</li>
 *   <li><b>rouet</b> — quatre traits qui tournent pendant qu'on attend, en monospace, avant
 *       l'animation en pixels (⣾ ⣽ ⣻ ⢿) ;</li>
 *   <li><b>allumage</b> — à l'ouverture, l'image s'écrase verticalement puis s'ouvre, comme un
 *       téléviseur à tube.</li>
 * </ul>
 *
 * <p>Rien de tout cela n'est actif en style macOS : {@link StyleVisuel#estRetro()} commande, et
 * {@link Animations#terminerTout()} ramène tout à son état final pour les captures.</p>
 */
public final class Retro {

    /** Caractère du curseur : un bloc plein, comme un terminal. */
    public static final String CURSEUR = "▌";

    /** Les quatre phases du rouet de patience. */
    public static final String[] ROUET = {"⣾", "⣽", "⣻", "⢿"};

    private Retro() {
    }

    /** Calque de balayage : un dessin, pas une image — il suit la taille de la fenêtre. */
    public static Canvas balayage(double largeur, double hauteur) {
        Canvas toile = new Canvas(largeur, hauteur);
        toile.setMouseTransparent(true);
        toile.setOpacity(0.10);
        dessiner(toile);
        toile.widthProperty().addListener((o, a, b) -> dessiner(toile));
        toile.heightProperty().addListener((o, a, b) -> dessiner(toile));
        if (Animations.actives()) {
            // Scintillement : 6 % d'amplitude, jamais plus — au-delà, la lecture devient pénible.
            Timeline scintille = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(toile.opacityProperty(), 0.08)),
                    new KeyFrame(Duration.millis(90), new KeyValue(toile.opacityProperty(), 0.15)),
                    new KeyFrame(Duration.millis(180), new KeyValue(toile.opacityProperty(), 0.08)));
            scintille.setCycleCount(Timeline.INDEFINITE);
            Animations.suivre(scintille);
            scintille.play();
        }
        return toile;
    }

    private static void dessiner(Canvas toile) {
        GraphicsContext g = toile.getGraphicsContext2D();
        g.clearRect(0, 0, toile.getWidth(), toile.getHeight());
        g.setFill(Color.web("#000000"));
        for (double y = 0; y < toile.getHeight(); y += 3) {
            g.fillRect(0, y, toile.getWidth(), 1);
        }
    }

    /** Enveloppe le contenu de la fenêtre d'un calque de balayage (aucun clic intercepté). */
    public static void installerBalayage(StackPane calque) {
        Canvas toile = balayage(calque.getWidth(), calque.getHeight());
        toile.widthProperty().bind(calque.widthProperty());
        toile.heightProperty().bind(calque.heightProperty());
        toile.setManaged(false);
        calque.getChildren().add(toile);
        StackPane.setMargin(toile, javafx.geometry.Insets.EMPTY);
    }

    /**
     * Machine à écrire : le texte apparaît à raison de {@code msParCaractere} par caractère.
     * Le curseur clignote ; à la fin, le texte complet reste affiché sans curseur.
     */
    public static void ecrire(Label cible, String texte, double msParCaractere) {
        if (!StyleVisuel.estRetro() || !Animations.actives() || texte == null) {
            cible.setText(texte);
            return;
        }
        cible.setText("");
        final int[] position = {0};
        Timeline frappe = new Timeline(new KeyFrame(Duration.millis(Math.max(8, msParCaractere)), e -> {
            position[0] = Math.min(texte.length(), position[0] + 2);
            cible.setText(texte.substring(0, position[0])
                    + (position[0] < texte.length() ? CURSEUR : ""));
        }));
        frappe.setCycleCount(Math.max(1, (texte.length() + 1) / 2));
        Animations.suivre(frappe);
        // Si la capture ou l'utilisateur interrompt l'animation, le texte doit être complet :
        // un écran à moitié écrit serait une régression, pas une image.
        Animations.aTerminer(() -> cible.setText(texte));
        frappe.play();
    }

    /** Rouet de patience : renvoie le contrôle pour pouvoir l'arrêter. */
    public static Timeline demarrerRouet(Label cible, String suffixe) {
        if (!Animations.actives()) {
            return null;
        }
        final int[] phase = {0};
        Timeline rouet = new Timeline(new KeyFrame(Duration.millis(120), e -> {
            phase[0] = (phase[0] + 1) % ROUET.length;
            cible.setText(ROUET[phase[0]] + " " + suffixe);
        }));
        rouet.setCycleCount(Timeline.INDEFINITE);
        Animations.suivre(rouet);
        rouet.play();
        return rouet;
    }

    public static void arreterRouet(Timeline rouet) {
        if (rouet != null) {
            rouet.stop();
        }
    }

    /** Allumage du tube : l'image s'écrase puis s'ouvre, avec un éclair blanc. */
    public static void allumage(Region contenu) {
        if (!StyleVisuel.estRetro() || !Animations.actives()) {
            return;
        }
        contenu.setScaleY(0.02);
        contenu.setOpacity(0.4);
        Timeline ouverture = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(contenu.scaleYProperty(), 0.02),
                        new KeyValue(contenu.opacityProperty(), 0.4)),
                new KeyFrame(Duration.millis(110), new KeyValue(contenu.scaleYProperty(), 1.0,
                        javafx.animation.Interpolator.LINEAR),
                        new KeyValue(contenu.opacityProperty(), 1)));
        Animations.suivre(ouverture);
        ouverture.play();
    }
}
