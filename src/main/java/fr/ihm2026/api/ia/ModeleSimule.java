package fr.ihm2026.api.ia;

import fr.ihm2026.core.Session;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Modèle hors ligne, déterministe, sans réseau — utilisé tant que le service réel (étape 7)
 * n'est pas déployé.
 *
 * <p>Il ne « rédige » pas : il <b>retrouve</b> le fragment de cours le plus proche de la question
 * par recouvrement de mots, puis répond en citant ce fragment. C'est volontairement pauvre — et
 * c'est ce qui rend le reste de la chaîne testable : garde-fou, quotas, refus, citations.</p>
 *
 * <p>Le corpus embarqué ({@code corpus-fragments.tsv}) est un export de la table
 * {@code ia_fragments} de PostgreSQL : le contenu affiché est donc exactement celui du cours
 * publié. En v0.3, {@link ModeleDistant} remplace cette classe sans toucher au reste.</p>
 */
public final class ModeleSimule implements ModeleIa {

    /**
     * Version du prompt système, journalisée à chaque appel. Étape 5 : le prompt a gagné un format
     * de sortie imposé, le vocabulaire du contrat de refus et cinq exemples (dont deux refus), donc
     * il change de version — un journal qui annoncerait encore {@code system-v1} mentirait.
     */
    public static final String VERSION_PROMPT = "system-v2";
    private static final double SEUIL_RECOUVREMENT = 0.34;   // ≈ deux mots fréquents ou un mot rare
    private static final int MAX_CITATIONS = 2;

    /**
     * Le découpage en mots n'est plus défini ici : il vient de {@link Tokeniseur}, qui lit la table
     * du fichier du modèle local. Deux copies de la même table finissaient par diverger.
     */
    private static Set<String> mots(String texte) {
        return Tokeniseur.mots(texte);
    }

    private static final List<Fragment> CORPUS = charger();

    private record Fragment(String langage, String section, String contenu, Set<String> mots) {
    }

    private static List<Fragment> charger() {
        List<Fragment> fragments = new ArrayList<>();
        try (InputStream in = ModeleSimule.class.getResourceAsStream("/fr/ihm2026/ia/corpus-fragments.tsv")) {
            if (in == null) {
                return fragments;
            }
            BufferedReader lecteur = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                String[] colonnes = ligne.split("\t", 3);
                if (colonnes.length == 3) {
                    String contenu = colonnes[2].replace("\\n", "\n");
                    fragments.add(new Fragment(colonnes[0], colonnes[1], contenu, mots(contenu)));
                }
            }
        } catch (Exception e) {
            // Corpus illisible : le modèle refusera toutes les demandes sans faire tomber l'IHM,
            // mais l'incident doit rester visible dans la console (il l'a été trop discrètement).
            System.err.println("[ia] corpus illisible : " + e);
        }
        return fragments;
    }

    /** Nombre de fragments chargés (vérifié par les tests). */
    public static int tailleCorpus() {
        return CORPUS.size();
    }

    /** Nombre de fragments contenant chaque mot : sert de pondération par rareté. */
    private static final java.util.Map<String, Integer> OCCURRENCES = occurrences();

    private static java.util.Map<String, Integer> occurrences() {
        java.util.Map<String, Integer> table = new java.util.HashMap<>();
        for (Fragment f : CORPUS) {
            for (String mot : f.mots()) {
                table.merge(mot, 1, Integer::sum);
            }
        }
        return table;
    }

    @Override
    public String identifiant() {
        return "modele-simule@" + VERSION_PROMPT;
    }

    @Override
    public ReponseIa repondre(ContexteIa contexte) {
        String question = contexte.question();
        if (question.isBlank()) {
            return ReponseIa.refus(MotifRefus.HORS_PERIMETRE, VERSION_PROMPT);
        }

        Set<String> motsQuestion = mots(question);
        java.util.Map<String, Integer> occurrences = OCCURRENCES;
        List<Score> scores = new ArrayList<>();
        for (Fragment f : CORPUS) {
            if (!f.langage().equals(contexte.langage().code())) {
                continue;                       // un parcours = un langage (indépendance des parcours)
            }
            // Pondération par rareté : un mot présent dans un seul fragment pèse plus lourd
            // qu'un mot présent partout (« fonction »). Sans cela, les fragments se valaient
            // et le premier arrivé gagnait, quel qu'il soit.
            double communs = 0;
            for (String mot : motsQuestion) {
                if (f.mots().contains(mot)) {
                    communs += 1.0 / Math.max(1, occurrences.getOrDefault(mot, 1));
                }
            }
            if (communs > 0) {
                scores.add(new Score(f, communs));
            }
        }
        scores.sort(Comparator.comparingDouble(Score::poids).reversed());

        if (scores.isEmpty() || scores.get(0).poids() < SEUIL_RECOUVREMENT) {
            // Aucun fragment assez proche : mieux vaut refuser que d'inventer (RG-13).
            return ReponseIa.refus(MotifRefus.HORS_PERIMETRE, VERSION_PROMPT);
        }

        Fragment meilleur = scores.get(0).fragment();
        StringBuilder texte = new StringBuilder();
        texte.append("D'après le cours « ").append(meilleur.section()).append(" » : ");
        texte.append(extrait(meilleur.contenu(), motsQuestion));
        texte.append(" Cette réponse n'est pas une correction : relis la section citée, puis ")
                .append("reviens à l'exercice. Je ne note pas et je ne valide pas.");

        List<ReponseIa.Citation> citations = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_CITATIONS, scores.size()); i++) {
            Fragment f = scores.get(i).fragment();
            if (i > 0 && f.section().equals(meilleur.section())) {
                continue;
            }
            citations.add(new ReponseIa.Citation(f.section(), f.langage()));
        }
        return new ReponseIa(texte.toString(), citations, MotifRefus.AUCUN, VERSION_PROMPT);
    }

    /** Extrait la phrase du fragment qui partage le plus de mots avec la question. */
    private static String extrait(String contenu, Set<String> motsQuestion) {
        String[] phrases = contenu.split("(?<=[.!?])\\s+|\\n\\s*\\n");
        String retenu = contenu.length() > 240 ? contenu.substring(0, 240) + "…" : contenu;
        int meilleurScore = -1;
        for (String phrase : phrases) {
            String propre = phrase.replaceAll("\\s+", " ").strip();
            if (propre.length() < 25) {
                continue;
            }
            int score = 0;
            for (String mot : mots(propre)) {
                if (motsQuestion.contains(mot)) {
                    score++;
                }
            }
            if (score > meilleurScore) {
                meilleurScore = score;
                retenu = propre;
            }
        }
        return retenu;
    }

    private record Score(Fragment fragment, double poids) {
    }
}
