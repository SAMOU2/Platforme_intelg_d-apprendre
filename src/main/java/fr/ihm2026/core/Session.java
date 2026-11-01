package fr.ihm2026.core;

import fr.ihm2026.model.ResultatVM;

/** Session courante : rôle, utilisateur, parcours (langage) et dernier résultat. */
public final class Session {
    private static long niveauServeurId;
    public static long niveauServeurId() { return niveauServeurId; }
    public static void setNiveauServeurId(long id) { niveauServeurId = id; }


    private Session() {
    }

    public enum Role {
        APPRENANT("Apprenant"), ENSEIGNANT("Enseignant"), ADMINISTRATEUR("Administrateur");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Langage {
        JAVA("Java", "java"), PYTHON("Python", "python"), CPP("C++", "cpp");

        private final String label;
        private final String code;

        Langage(String label, String code) {
            this.label = label;
            this.code = code;
        }

        public String label() {
            return label;
        }

        public String code() {
            return code;
        }
    }

    private static Role role = Role.APPRENANT;
    private static Langage langage = Langage.PYTHON;
    private static String utilisateur = "Poste non configuré";
    private static ResultatVM dernierResultat;
    /**
     * Vrai pendant une évaluation. L'assistance est alors fermée <b>côté service</b>, pas
     * seulement masquée à l'écran : une règle d'interface se contourne (raccourci, appel direct),
     * une règle serveur non.
     */
    private static boolean evaluationEnCours = false;

    /** Rang du niveau en cours de consultation (1 à 5) : contexte minimal transmis à l'assistant. */
    private static int rangNiveauCourant = 1;

    public static Role role() {
        return role;
    }

    public static void setRole(Role r) {
        role = r;
    }

    public static Langage langage() {
        return langage;
    }

    public static void setLangage(Langage l) {
        if (langage != l) niveauServeurId = 0;
        langage = l;
    }

    public static String utilisateur() {
        return utilisateur;
    }

    public static void setUtilisateur(String u) {
        utilisateur = u;
    }

    public static boolean evaluationEnCours() {
        return evaluationEnCours;
    }

    public static void demarrerEvaluation() {
        evaluationEnCours = true;
    }

    public static void terminerEvaluation() {
        evaluationEnCours = false;
    }

    public static int rangNiveauCourant() {
        return rangNiveauCourant;
    }

    public static void setRangNiveauCourant(int rang) {
        rangNiveauCourant = Math.max(1, rang);
    }

    public static ResultatVM dernierResultat() {
        return dernierResultat;
    }

    public static void setDernierResultat(ResultatVM r) {
        dernierResultat = r;
    }
}
