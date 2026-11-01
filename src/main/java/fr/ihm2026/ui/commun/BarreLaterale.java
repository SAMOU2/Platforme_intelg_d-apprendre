package fr.ihm2026.ui.commun;

import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/**
 * Barre latérale de navigation, à la manière du Finder (v0.10.0).
 *
 * <p>C'est le changement de structure de cette version : la navigation quitte la barre du haut —
 * où elle ressemblait à un site web — et prend place dans une <b>barre latérale translucide</b>,
 * avec des sections (« Apprendre », « Parcours », « Compte »), des icônes et une ligne active
 * pleine en couleur d'accent, comme dans le Finder ou Réglages Système.</p>
 *
 * <p>Les entrées restent limitées au rôle courant (IHM-04) ; le parcours (Java / Python / C++) est
 * un choix explicite, pas un menu déroulant perdu dans un coin ; et le compte est en bas, avec
 * l'action « Quitter », comme un pied de barre latérale macOS.</p>
 */
public class BarreLaterale extends VBox {

    private static final String ICONE_PARCOURS = "M1 3.2 L5.5 1.6 L10.5 3.4 L15 1.9 L15 12.8 "
            + "L10.5 14.4 L5.5 12.6 L1 14.1 Z M5.5 1.6 L5.5 12.6 M10.5 3.4 L10.5 14.4";
    private static final String ICONE_COURS = "M2 2.4 C4.2 1.4 6.2 1.6 8 2.8 L8 14 "
            + "C6.2 12.8 4.2 12.6 2 13.6 Z M14 2.4 C11.8 1.4 9.8 1.6 8 2.8 L8 14 "
            + "C9.8 12.8 11.8 12.6 14 13.6 Z";
    private static final String ICONE_EXERCICE = "M3.2 4.2 L5.2 4.2 L8.8 8 L5.2 11.8 L3.2 11.8 "
            + "L6.8 8 Z M9.4 10.6 L14 10.6 L14 12.4 L9.4 12.4 Z";
    private static final String ICONE_BORD = "M2 12.6 L14 12.6 L14 14.2 L2 14.2 Z "
            + "M3.2 10.4 L5.2 10.4 L5.2 5.6 L3.2 5.6 Z M7 10.4 L9 10.4 L9 2.6 L7 2.6 Z "
            + "M10.8 10.4 L12.8 10.4 L12.8 7.4 L10.8 7.4 Z";
    private static final String ICONE_ENSEIGNER = "M2 2.6 L14 2.6 L14 10.4 L8.6 10.4 L8.6 13.6 "
            + "L6.8 13.6 L6.8 10.4 L2 10.4 Z M4.2 4.6 L4.2 6 L11.8 6 L11.8 4.6 Z";
    private static final String ICONE_RESULTATS = "M2 2.4 L6.4 2.4 L6.4 7.2 L2 7.2 Z "
            + "M9.6 2.4 L14 2.4 L14 7.2 L9.6 7.2 Z M2 9.6 L6.4 9.6 L6.4 14.2 L2 14.2 Z "
            + "M9.6 9.6 L14 9.6 L14 14.2 L9.6 14.2 Z";
    private static final String ICONE_COMPTES = "M6 2.2 A2.6 2.6 0 1 1 6 7.4 A2.6 2.6 0 1 1 6 2.2 Z "
            + "M1.4 13.8 C1.4 11 3.6 9.4 6 9.4 C8.4 9.4 10.6 11 10.6 13.8 Z "
            + "M11.6 2.6 A2.2 2.2 0 1 1 11.6 7 A2.2 2.2 0 1 1 11.6 2.6 Z "
            + "M11.9 8.8 C13.9 9 15 10.6 15 13.8 L12.4 13.8 C12.4 11.9 12.2 10.2 11.2 8.9 Z";

    private final Router router;
    private final Session.Role role;
    private final Map<String, HBox> lignes = new LinkedHashMap<>();
    private final VBox sectionParcours = new VBox(1);

    public BarreLaterale(Router router) {
        this.router = router;
        this.role = Session.role();
        getStyleClass().add("barre-laterale");
        setSpacing(2);

        construireEntrees();

        sectionParcours.getStyleClass().add("section");
        Region ressort = new Region();
        VBox.setVgrow(ressort, Priority.ALWAYS);

        getChildren().addAll(ressort, compte());
    }

    // ------------------------------------------------------------------ sections
    private void construireEntrees() {
        switch (role) {
            case APPRENANT -> {
                getChildren().add(entete("Apprendre"));
                getChildren().add(entree("Parcours", Router.PARCOURS, ICONE_PARCOURS));
                getChildren().add(entree("Cours", Router.COURS, ICONE_COURS));
                getChildren().add(entree("Exercice", Router.EXERCICE, ICONE_EXERCICE));
                getChildren().add(entree("Mes copies et décisions", Router.RESULTAT, ICONE_RESULTATS));
                getChildren().add(entree("Tableau de bord", Router.TABLEAU_BORD, ICONE_BORD));
                getChildren().addAll(entete("Parcours"), sectionParcours);
                for (Session.Langage langage : Session.Langage.values()) {
                    sectionParcours.getChildren().add(ligneParcours(langage));
                }
            }
            case ENSEIGNANT -> {
                getChildren().add(entete("Enseigner"));
                getChildren().add(entree(fr.ihm2026.reseau.Reseau.sessionCompteInstallee() ? "Mes niveaux et contenus" : "Créer un niveau", Router.CONDITIONS, ICONE_ENSEIGNER));
                getChildren().add(entree("Mes apprenants et copies", Router.RESULTATS, ICONE_RESULTATS));
                getChildren().addAll(entete("Parcours"), sectionParcours);
                for (Session.Langage langage : Session.Langage.values()) {
                    sectionParcours.getChildren().add(ligneParcours(langage));
                }
            }
            case ADMINISTRATEUR -> {
                getChildren().add(entete("Administration"));
                getChildren().add(entree("Comptes et rôles", Router.COMPTES, ICONE_COMPTES));
            }
        }
    }

    private Label entete(String texte) {
        Label label = new Label(texte);
        label.getStyleClass().add("entete-section");
        return label;
    }

    private HBox entree(String libelle, String route, String icone) {
        HBox ligne = new HBox(8);
        ligne.getStyleClass().add("ligne-laterale");
        ligne.setAlignment(Pos.CENTER_LEFT);
        SVGPath svg = new SVGPath();
        svg.setContent(icone);
        svg.getStyleClass().add("icone-laterale");
        Label texte = new Label(libelle);
        texte.getStyleClass().add("libelle-lateral");
        ligne.getChildren().addAll(svg, texte);
        ligne.setOnMouseClicked(e -> router.go(route));
        lignes.put(route, ligne);
        return ligne;
    }

    /** Une ligne par parcours : le parcours courant porte une coche, comme un favori du Finder. */
    private HBox ligneParcours(Session.Langage langage) {
        HBox ligne = new HBox(8);
        ligne.getStyleClass().addAll("ligne-laterale", "ligne-parcours");
        ligne.setAlignment(Pos.CENTER_LEFT);

        Region pastille = new Region();
        pastille.getStyleClass().addAll("pastille-parcours",
                "parcours-" + langage.code());
        Label texte = new Label(langage.label());
        texte.getStyleClass().add("libelle-lateral");
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        Label coche = new Label("✓");
        coche.getStyleClass().add("coche-parcours");
        coche.setVisible(langage == Session.langage());

        ligne.getChildren().addAll(pastille, texte, espace, coche);
        ligne.setOnMouseClicked(e -> {
            if (Session.langage() != langage) {
                Session.setLangage(langage);
                String route = router.current();
                router.go(route == null ? Router.PARCOURS : route);
            }
        });
        lignes.put("parcours:" + langage.code(), ligne);
        return ligne;
    }

    /** Pied de barre latérale : le compte, son rôle, et la sortie. */
    private HBox compte() {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("avatar");
        Label initiale = new Label(initiales(Session.utilisateur()));
        initiale.getStyleClass().add("avatar-initiale");
        avatar.getChildren().add(initiale);

        Label nom = new Label(Session.utilisateur());
        nom.getStyleClass().add("compte-nom");
        Label roleTexte = new Label(libelleRole(role));
        roleTexte.getStyleClass().add("compte-role");

        VBox textes = new VBox(1, nom, roleTexte);
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);

        Button sortie = new Button("⏻");
        sortie.getStyleClass().add("bouton-quitter");
        sortie.setTooltip(new javafx.scene.control.Tooltip("Quitter la session"));
        sortie.setOnAction(e -> {
            fr.ihm2026.reseau.Reseau.deconnecter();
            router.go(Router.RESEAU);
        });

        HBox pied = new HBox(9, avatar, textes, espace, sortie);
        pied.getStyleClass().add("compte-lateral");
        pied.setAlignment(Pos.CENTER_LEFT);
        return pied;
    }

    private static String initiales(String nom) {
        if (nom == null || nom.isBlank()) {
            return "?";
        }
        String[] morceaux = nom.trim().split("\\s+");
        String premiere = morceaux[0].substring(0, 1);
        String seconde = morceaux.length > 1 ? morceaux[morceaux.length - 1].substring(0, 1) : "";
        return (premiere + seconde).toUpperCase(java.util.Locale.FRANCE);
    }

    private static String libelleRole(Session.Role role) {
        return switch (role) {
            case APPRENANT -> "Apprenant";
            case ENSEIGNANT -> "Enseignant";
            case ADMINISTRATEUR -> "Administrateur";
        };
    }

    // ------------------------------------------------------------------ état
    public Session.Role role() {
        return role;
    }

    /** Met en évidence l'écran courant, et le parcours courant. */
    public void setActive(String route) {
        lignes.forEach((cle, ligne) -> ligne.getStyleClass().remove("active"));
        HBox actif = lignes.get(route);
        if (actif != null) {
            actif.getStyleClass().add("active");
        }
        for (Session.Langage langage : Session.Langage.values()) {
            HBox ligne = lignes.get("parcours:" + langage.code());
            if (ligne == null) {
                continue;
            }
            boolean courant = langage == Session.langage();
            ((Label) ligne.getChildren().get(3)).setVisible(courant);
            if (courant) {
                if (!ligne.getStyleClass().contains("courant")) {
                    ligne.getStyleClass().add("courant");
                }
            } else {
                ligne.getStyleClass().remove("courant");
            }
        }
    }
}
