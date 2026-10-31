import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Sonde d'animations — instrument de mesure, pas une vue de l'application.
 *
 * <p>Une capture d'écran ne montre pas un mouvement. Cette sonde relève donc, à intervalles
 * réguliers, l'opacité de la racine pendant une bascule d'apparence : la suite de valeurs prouve
 * que l'animation s'exécute réellement, et à quelle vitesse.</p>
 *
 * <pre>
 * ./mvnw -B -o -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 * xvfb-run -a java -Dihm.animations.echelle=10 -cp "target/classes:$(cat /tmp/cp.txt)" \
 *     outils/sonde-animations.java
 * </pre>
 *
 * Sortie observée le 27/10/2026 (échelle ×10, boucle de rendu sans écran ≈ 200 ms) :
 * {@code 0,848 → 0,772 → 0,847 → 0,920}, soit la voile qui descend vers 0,75 puis remonte à 1.
 */
public class SondeAnimations extends Application {
    public static void main(String[] a) { Application.launch(SondeAnimations.class, a); }
    @Override public void start(Stage stage) throws Exception {
        new fr.ihm2026.App().start(stage);
        Platform.runLater(() -> {
            for (Node n : stage.getScene().getRoot().lookupAll(".btn-primary")) {
                if (n instanceof Button b) { b.fire(); break; }
            }
            new Timeline(new KeyFrame(Duration.millis(1000), e -> {
                Node racine = stage.getScene().getRoot();
                Button apparence = null;
                for (Node n : racine.lookupAll(".bouton-apparence")) {
                    if (n instanceof Button b && !b.getStyleClass().contains("bouton-laterale")) apparence = b;
                }
                System.out.println("échelle=" + System.getProperty("ihm.animations.echelle", "1")
                        + " animations actives=" + fr.ihm2026.core.Animations.actives());
                apparence.fire();
                int[] t = {0, 80, 160, 240, 320, 400, 500, 700, 900, 1200, 1600, 2000};
                for (int ms : t) {
                    new Timeline(new KeyFrame(Duration.millis(ms), e2 ->
                        System.out.println("t=" + ms + "ms opacité=" + String.format("%.3f", racine.getOpacity())
                            + " en cours=" + fr.ihm2026.core.Animations.animationsEnCours())
                    )).play();
                }
                new Timeline(new KeyFrame(Duration.millis(2300), e3 -> Platform.exit())).play();
            })).play();
        });
    }
}
