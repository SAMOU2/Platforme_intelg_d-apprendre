package fr.ihm2026.api.mock;

import fr.ihm2026.core.Session;
import fr.ihm2026.model.CoursVM;
import fr.ihm2026.model.EtatNiveau;
import fr.ihm2026.model.NiveauVM;
import fr.ihm2026.model.ResultatVM;
import fr.ihm2026.model.UtilisateurVM;
import java.util.ArrayList;
import java.util.List;

/**
 * Données simulées de la v0.1 — remplacent le serveur tant que l'API n'est pas branchée.
 * Le contenu suit le jeu de données de db/02-seed.sql : mêmes titres, mêmes seuils.
 */
public final class MockBackend {

    private static final List<UtilisateurVM> COMPTES = new ArrayList<>(List.of(
            new UtilisateurVM("Amina Bouzid", "amina.bouzid@univ.dz", "Apprenant", true, 16, 2),
            new UtilisateurVM("Karim Haddad", "karim.haddad@univ.dz", "Apprenant", true, 9, 3),
            new UtilisateurVM("Sofia Meziane", "sofia.meziane@univ.dz", "Enseignant", true, 0, 0),
            new UtilisateurVM("Yacine Rami", "yacine.rami@univ.dz", "Apprenant", false, 7, 1),
            new UtilisateurVM("Nadia Cherif", "nadia.cherif@univ.dz", "Administrateur", true, 0, 0)));

    private MockBackend() {
    }

    public static List<NiveauVM> niveaux(Session.Langage langage) {
        String l = langage.label();
        return switch (langage) {
            case PYTHON -> List.of(
                    new NiveauVM(1, "Variables et types", EtatNiveau.VALIDE, 10, 16, 3, l),
                    new NiveauVM(2, "Conditions et boucles", EtatNiveau.VALIDE, 10, 13, 2, l),
                    new NiveauVM(3, "Fonctions et modules", EtatNiveau.EN_COURS, 12, 0, 2, l),
                    new NiveauVM(4, "Structures de données", EtatNiveau.VERROUILLE, 12, 0, 0, l),
                    new NiveauVM(5, "Fichiers et exceptions", EtatNiveau.VERROUILLE, 12, 0, 0, l));
            case JAVA -> List.of(
                    new NiveauVM(1, "Classes et objets", EtatNiveau.VALIDE, 12, 15, 3, l),
                    new NiveauVM(2, "Héritage et interfaces", EtatNiveau.EN_COURS, 12, 0, 2, l),
                    new NiveauVM(3, "Collections et généricité", EtatNiveau.VERROUILLE, 12, 0, 0, l),
                    new NiveauVM(4, "Exceptions et flux", EtatNiveau.VERROUILLE, 12, 0, 0, l),
                    new NiveauVM(5, "Interface graphique et MVC", EtatNiveau.VERROUILLE, 12, 0, 0, l));
            case CPP -> List.of(
                    new NiveauVM(1, "Compilation et types", EtatNiveau.EN_COURS, 10, 0, 3, l),
                    new NiveauVM(2, "Pointeurs et références", EtatNiveau.VERROUILLE, 10, 0, 0, l),
                    new NiveauVM(3, "Classes et surcharge", EtatNiveau.VERROUILLE, 10, 0, 0, l),
                    new NiveauVM(4, "STL et templates", EtatNiveau.VERROUILLE, 10, 0, 0, l),
                    new NiveauVM(5, "Gestion mémoire", EtatNiveau.VERROUILLE, 10, 0, 0, l));
        };
    }

    public static CoursVM cours(Session.Langage langage, int rang) {
        return switch (langage) {
            case PYTHON -> new CoursVM("Niveau 3 — Fonctions et modules", "Python", List.of(
                    CoursVM.Bloc.texte("Une fonction regroupe des instructions réutilisables. "
                            + "En Python, elle se déclare avec le mot-clé def et son corps est indenté : "
                            + "l'indentation fait partie de la syntaxe."),
                    CoursVM.Bloc.code("python", "def moyenne(notes):\n"
                            + "    \"\"\"Retourne la moyenne d'une liste de notes.\"\"\"\n"
                            + "    if not notes:\n"
                            + "        return 0\n"
                            + "    return sum(notes) / len(notes)\n"
                            + "\n"
                            + "print(moyenne([12, 15, 9]))   # 12.0"),
                    CoursVM.Bloc.texte("Les paramètres peuvent avoir une valeur par défaut : "
                            + "def saluer(nom=\"monde\"). Attention, la valeur par défaut est évaluée "
                            + "une seule fois, à la définition de la fonction."),
                    CoursVM.Bloc.code("python", "def saluer(nom: str = \"monde\") -> str:\n"
                            + "    return f\"Bonjour, {nom} !\"\n"
                            + "\n"
                            + "print(saluer())        # Bonjour, monde !\n"
                            + "print(saluer(\"Amina\")) # Bonjour, Amina !")));
            case JAVA -> new CoursVM("Niveau 2 — Héritage et interfaces", "Java", List.of(
                    CoursVM.Bloc.texte("Une classe Java hérite d'une seule classe mère mais peut "
                            + "implémenter plusieurs interfaces. Toute méthode déclarée doit respecter "
                            + "le type de retour annoncé."),
                    CoursVM.Bloc.code("java", "public interface Notable {\n"
                            + "    double moyenne();\n"
                            + "}\n"
                            + "\n"
                            + "public class Etudiant implements Notable {\n"
                            + "    private final double[] notes;\n"
                            + "\n"
                            + "    public Etudiant(double[] notes) { this.notes = notes; }\n"
                            + "\n"
                            + "    @Override public double moyenne() {\n"
                            + "        double somme = 0;\n"
                            + "        for (double n : notes) somme += n;\n"
                            + "        return notes.length == 0 ? 0 : somme / notes.length;\n"
                            + "    }\n"
                            + "}")));
            case CPP -> new CoursVM("Niveau 1 — Compilation et types", "C++", List.of(
                    CoursVM.Bloc.texte("Un programme C++ est compilé en deux temps (translation puis "
                            + "édition de liens). Les types sont connus à la compilation : une conversion "
                            + "implicite peut donc tronquer une valeur sans avertissement."),
                    CoursVM.Bloc.code("cpp", "#include <iostream>\n"
                            + "#include <vector>\n"
                            + "\n"
                            + "double moyenne(const std::vector<int>& notes) {\n"
                            + "    if (notes.empty()) return 0.0;\n"
                            + "    int somme = 0;\n"
                            + "    for (int n : notes) somme += n;\n"
                            + "    return static_cast<double>(somme) / notes.size();\n"
                            + "}\n"
                            + "\n"
                            + "int main() {\n"
                            + "    std::cout << moyenne({12, 15, 9}) << std::endl;\n"
                            + "    return 0;\n"
                            + "}")));
        };
    }

    public static List<QuestionDto> questions() {
        return List.of(
                new QuestionDto("Quel mot-clé déclare une fonction en Python ?",
                        List.of("func", "def", "function", "void"), 1),
                new QuestionDto("Que vaut 7 // 2 en Python ?",
                        List.of("3.5", "3", "4", "Erreur"), 1),
                new QuestionDto("Quelle est la portée d'une variable déclarée dans un bloc en Java ?",
                        List.of("Tout le programme", "La classe", "Le bloc où elle est déclarée", "Le fichier"), 2));
    }

    /** Petit DTO local pour éviter une dépendance de la couche mock vers api.dto. */
    public record QuestionDto(String enonce, List<String> propositions, int bonneReponse) {
    }

    public static synchronized List<UtilisateurVM> comptes() {
        return List.copyOf(COMPTES);
    }

    public static synchronized UtilisateurVM creerCompte(String nom, String email,
            Session.Role role, boolean actif) {
        if (nom == null || nom.isBlank() || email == null || email.isBlank() || role == null) {
            throw new IllegalArgumentException("Le nom, l'e-mail et le rôle sont obligatoires.");
        }
        String adresse = email.strip();
        boolean existe = COMPTES.stream().anyMatch(c -> c.getEmail().equalsIgnoreCase(adresse));
        if (existe) {
            throw new IllegalArgumentException("Un compte utilise déjà cette adresse e-mail.");
        }
        UtilisateurVM compte = new UtilisateurVM(nom.strip(), adresse, role.label(), actif, 0, 0);
        COMPTES.add(compte);
        return compte;
    }

    public static synchronized UtilisateurVM definirCompteActif(String email, boolean actif) {
        int index = indexCompte(email);
        UtilisateurVM compte = COMPTES.get(index);
        if (!actif && compte.isActif() && "Administrateur".equalsIgnoreCase(compte.getRole())) {
            long administrateursActifs = COMPTES.stream()
                    .filter(UtilisateurVM::isActif)
                    .filter(c -> "Administrateur".equalsIgnoreCase(c.getRole()))
                    .count();
            if (administrateursActifs <= 1) {
                throw new IllegalStateException(
                        "Impossible de désactiver le dernier compte administrateur actif.");
            }
        }
        UtilisateurVM actualise = new UtilisateurVM(compte.getNom(), compte.getEmail(),
                compte.getRole(), actif, compte.getNote(), compte.getTentatives());
        COMPTES.set(index, actualise);
        return actualise;
    }

    private static int indexCompte(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("L'adresse e-mail du compte est obligatoire.");
        }
        for (int i = 0; i < COMPTES.size(); i++) {
            if (COMPTES.get(i).getEmail().equalsIgnoreCase(email.strip())) {
                return i;
            }
        }
        throw new IllegalArgumentException("Compte introuvable : " + email);
    }

    public static List<UtilisateurVM> resultatsDuNiveau() {
        return List.of(
                new UtilisateurVM("Amina Bouzid", "Niveau 3 — Python", "Validé", true, 16, 1),
                new UtilisateurVM("Karim Haddad", "Niveau 3 — Python", "En cours", true, 9, 3),
                new UtilisateurVM("Yacine Rami", "Niveau 3 — Python", "En cours", true, 7, 2),
                new UtilisateurVM("Lina Saadi", "Niveau 3 — Python", "Validé", true, 13, 1),
                new UtilisateurVM("Omar Belkacem", "Niveau 3 — Python", "En cours", true, 11, 2));
    }

    /** Résultat d'une correction simulée (le vrai calcul sera fait par le moteur de validation). */
    public static ResultatVM corriger(int bonnesReponses, int total, int seuil) {
        int note = total == 0 ? 0 : Math.round(20f * bonnesReponses / total);
        List<String> manquants = new ArrayList<>();
        List<String> aRevoir = new ArrayList<>();
        if (note < seuil) {
            manquants.add("Il manque " + (seuil - note) + " point(s) pour atteindre le seuil de " + seuil + "/20.");
            if (bonnesReponses < total) {
                manquants.add((total - bonnesReponses) + " question(s) sans réponse correcte.");
            }
            aRevoir.add("Niveau 3 — Fonctions et modules (section « paramètres par défaut »)");
            aRevoir.add("Exercice 3.2 — retour multiple d'une fonction");
        }
        return new ResultatVM(note, seuil, manquants, aRevoir, 1);
    }

    /** Exécution simulée : détecte les oublis de point-virgule pour montrer l'erreur pédagogique. */
    public static ExecutionSimulee executer(String code, Session.Langage langage) {
        String[] lignes = code.split("\n", -1);
        for (int i = 0; i < lignes.length; i++) {
            String l = lignes[i].trim();
            boolean instruction = !l.isEmpty() && !l.startsWith("#") && !l.startsWith("//")
                    && !l.endsWith(":") && !l.endsWith("{") && !l.endsWith(";")
                    && !l.endsWith(",") && !l.endsWith(")") && !l.endsWith("(")
                    && !l.startsWith("def ") && !l.startsWith("class ") && !l.startsWith("if ")
                    && !l.startsWith("for ") && !l.startsWith("while ") && !l.startsWith("else")
                    && !l.startsWith("public ") && !l.startsWith("}")
                    && !(langage == Session.Langage.PYTHON && !l.contains("="));
            if ((langage == Session.Langage.JAVA || langage == Session.Langage.CPP) && instruction) {
                return new ExecutionSimulee(false, "",
                        "ligne " + (i + 1) + " : « ; » manquant en fin d'instruction", i + 1);
            }
        }
        return new ExecutionSimulee(true, "Programme exécuté — aucune erreur détectée.", "", 0);
    }

    public record ExecutionSimulee(boolean ok, String sortie, String erreur, int ligneErreur) {
    }
}
