import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;

/**
 * Sonde de la souris (v0.14.4) : vérifie qu'un clic atteint réellement l'interface.
 *
 * <p>Défaut constaté en salle : le curseur prenait la forme d'une double flèche diagonale au
 * milieu de la fenêtre, et les clics ne faisaient rien. Cause : les zones de redimensionnement de
 * {@code FenetreOutils} étaient des {@code Region} sans taille maximale ; le {@code StackPane} les
 * étirait sur toute la fenêtre, et la dernière — le coin bas-droit, curseur {@code SE_RESIZE} —
 * recouvrait tout. La sonde déplace la souris avec le {@link Robot} (de vrais événements du
 * système, pas des {@code fire()}), puis clique pour de bon.</p>
 *
 * <pre>
 * xvfb-run -a -s "-screen 0 1024x768x24" java -Dihm.trace.souris=1 -cp "target/classes:$(cat /tmp/cp.txt)" \
 *     outils/sonde-souris.java
 * </pre>
 */
public class SondeSouris {

    public static void main(String[] args) throws Exception {
        Thread application = new Thread(() -> fr.ihm2026.Launcher.main(new String[]{
                "--role=administrateur", "--poste=PC2", "--serveur=127.0.0.1", "--port=7788"}), "application");
        application.setDaemon(true);
        application.start();

        Stage fenetre = attendreFenetre();
        for (int i = 0; i < 200 && (!fenetre.isShowing() || fenetre.getWidth() < 200); i++) {
            Thread.sleep(100);
        }
        javafx.geometry.Rectangle2D ecran = javafx.stage.Screen.getPrimary().getVisualBounds();
        System.out.println("[souris] fenêtre " + (int) fenetre.getWidth() + " × "
                + (int) fenetre.getHeight() + " sur un écran de " + (int) ecran.getWidth()
                + " × " + (int) ecran.getHeight());

        List<String> clics = java.util.Collections.synchronizedList(new ArrayList<>());
        Platform.runLater(() -> fenetre.getScene().addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> clics.add(
                        fr.ihm2026.ui.commun.FenetreOutils.decrire(e.getPickResult()
                                .getIntersectedNode()))));

        Thread.sleep(3000);   // connexion automatique + ouverture de l'écran du poste

        // 1) Balayage : neuf points répartis dans la fenêtre, plus le centre du bouton visible.
        Platform.runLater(() -> {
            Robot robot = new Robot();
            double x0 = fenetre.getX();
            double y0 = fenetre.getY();
            double w = fenetre.getWidth();
            double h = fenetre.getHeight();
            for (double fx : new double[]{0.25, 0.5, 0.75}) {
                for (double fy : new double[]{0.25, 0.5, 0.75}) {
                    robot.mouseMove(x0 + w * fx, y0 + h * fy);
                }
            }
        });
        Thread.sleep(1200);

        // 2) Clic réel sur le premier bouton de l'écran, aux coordonnées de son centre.
        AtomicReference<String> cible = new AtomicReference<>("aucun bouton");
        Platform.runLater(() -> {
            Button bouton = premierBouton(fenetre.getScene().getRoot());
            if (bouton == null) {
                return;
            }
            Point2D centre = bouton.localToScreen(bouton.getWidth() / 2, bouton.getHeight() / 2);
            cible.set("« " + bouton.getText() + " » en (" + (int) centre.getX()
                    + "," + (int) centre.getY() + ")");
            Robot robot = new Robot();
            robot.mouseMove(centre.getX(), centre.getY());
        });
        Thread.sleep(700);
        Platform.runLater(() -> {
            Robot robot = new Robot();
            robot.mousePress(javafx.scene.input.MouseButton.PRIMARY);
            robot.mouseRelease(javafx.scene.input.MouseButton.PRIMARY);
        });
        Thread.sleep(900);

        System.out.println("[souris] clic simulé   : " + cible.get());
        System.out.println("[souris] clics reçus   : " + clics.size()
                + (clics.isEmpty() ? "  → ECHEC : aucun clic n'a atteint l'interface" : ""));
        clics.forEach(c -> System.out.println("[souris]   reçu par : " + c));

        Platform.runLater(() -> {
            fr.ihm2026.core.Animations.terminerTout();
            Platform.exit();
        });
        Thread.sleep(400);
        System.exit(clics.isEmpty() ? 1 : 0);
    }

    private static Button premierBouton(Node noeud) {
        if (noeud instanceof Button b && b.isVisible() && b.getWidth() > 30
                && b.getText() != null && b.getText().strip().length() > 3) {
            return b;
        }
        if (noeud instanceof javafx.scene.Parent parent) {
            for (Node enfant : parent.getChildrenUnmodifiable()) {
                Button trouve = premierBouton(enfant);
                if (trouve != null) {
                    return trouve;
                }
            }
        }
        return null;
    }

    private static Stage attendreFenetre() throws InterruptedException {
        for (int i = 0; i < 400; i++) {
            for (javafx.stage.Window fenetre : javafx.stage.Window.getWindows()) {
                if (fenetre instanceof Stage stage && stage.getScene() != null) {
                    return stage;
                }
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("aucune fenêtre ouverte");
    }
}
