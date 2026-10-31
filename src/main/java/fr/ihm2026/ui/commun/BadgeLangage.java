package fr.ihm2026.ui.commun;

import fr.ihm2026.core.Session;
import javafx.scene.control.Label;

/** Badge de langage : couleur + libellé, jamais la couleur seule (IHM-24, IHM-15). */
public class BadgeLangage extends Label {

    public BadgeLangage(Session.Langage langage) {
        super(langage.label());
        getStyleClass().addAll("badge-langage", "langage-" + langage.code());
    }

    public static BadgeLangage pour(String libelle) {
        Session.Langage langage = switch (libelle == null ? "" : libelle.toLowerCase()) {
            case "java" -> Session.Langage.JAVA;
            case "c++", "cpp" -> Session.Langage.CPP;
            default -> Session.Langage.PYTHON;
        };
        return new BadgeLangage(langage);
    }
}
