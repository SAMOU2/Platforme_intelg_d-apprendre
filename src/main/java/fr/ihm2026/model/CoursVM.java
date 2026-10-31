package fr.ihm2026.model;

import java.util.List;

/** Un cours (ou une section de cours) dans un langage donné. */
public class CoursVM {

    /** Un bloc de contenu : paragraphe ou exemple de code. */
    public record Bloc(String type, String langage, String contenu) {
        public static Bloc texte(String t) {
            return new Bloc("texte", null, t);
        }

        public static Bloc code(String langage, String c) {
            return new Bloc("code", langage, c);
        }
    }

    private final String titre;
    private final String langage;
    private final List<Bloc> blocs;

    public CoursVM(String titre, String langage, List<Bloc> blocs) {
        this.titre = titre;
        this.langage = langage;
        this.blocs = blocs;
    }

    public String getTitre() {
        return titre;
    }

    public String getLangage() {
        return langage;
    }

    public List<Bloc> getBlocs() {
        return blocs;
    }
}
