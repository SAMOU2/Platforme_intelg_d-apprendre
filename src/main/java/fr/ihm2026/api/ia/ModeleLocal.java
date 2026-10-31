package fr.ihm2026.api.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ihm2026.core.Session;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modèle local — entraîné hors ligne, exécuté sans réseau ni clé d'API.
 *
 * <p>C'est le modèle « codé » de l'étape 6. Il ne rédige pas : il <b>décide</b> et il <b>classe</b>,
 * les deux endroits où une règle écrite ne suffit plus. Trois têtes, apprises par
 * {@code ia/modele/entrainer.py} et relues ici :</p>
 * <ol>
 *   <li><b>décision</b> — la question demande-t-elle une réponse, une solution, une note, ou
 *       sort-elle du parcours ? (4 classes : AUCUN, SOLUTION_DEMANDEE, DECISION_DEMANDEE,
 *       HORS_PERIMETRE) ;</li>
 *   <li><b>notion</b> — de quoi parle-t-elle ? (6 classes : VAR, COND, BOUCLE, FONC, COLL,
 *       ERREUR) ;</li>
 *   <li><b>couverture</b> — la question contient-elle des mots du domaine ? (seuil appris du
 *       fichier, pas écrit ici).</li>
 * </ol>
 *
 * <p>Les deux premières sont deux Naive Bayes multinomiaux ; la troisième est un simple recouvrement
 * pondéré par la rareté des mots. Aucune dépendance d'exécution : le fichier de poids est du JSON,
 * lu avec le même Jackson que le reste du projet. Le modèle ne fait aucun appel réseau — c'est
 * vérifié par un test qui lit les sources.</p>
 */
public final class ModeleLocal implements ModeleIa {

    // Le v2 reste opt-in tant que sa généralisation n'est pas validée sur des données réelles.
    public static final String VERSION = "local-v2".equalsIgnoreCase(
            System.getProperty("ihm.modele", "")) ? "modele-local-v2" : "modele-local-v1";
    private static final String RESSOURCE = "/fr/ihm2026/ia/" + VERSION + ".json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Longueur maximale du texte produit, alignée sur le format de sortie (600 caractères). */
    private static final int LONGUEUR_MAX = 560;

    private static final JsonNode RACINE = charger();

    // ------------------------------------------------------------------ structures apprises
    /** Une tête Naive Bayes : classes, a priori, probabilités conditionnelles et valeur par défaut. */
    private static final class Tete {
        private final List<String> classes;
        private final Map<String, Double> logPrior = new HashMap<>();
        private final Map<String, Double> defaut = new HashMap<>();
        private final Map<String, Map<String, Double>> logP = new HashMap<>();

        Tete(JsonNode noeud) {
            List<String> noms = new ArrayList<>();
            noeud.path("classes").forEach(n -> noms.add(n.asText()));
            this.classes = List.copyOf(noms);
            noeud.path("logPrior").fields().forEachRemaining(e ->
                    logPrior.put(e.getKey(), e.getValue().asDouble()));
            noeud.path("defaut").fields().forEachRemaining(e ->
                    defaut.put(e.getKey(), e.getValue().asDouble()));
            noeud.path("logP").fields().forEachRemaining(e -> {
                Map<String, Double> table = new HashMap<>();
                e.getValue().fields().forEachRemaining(
                        p -> table.put(p.getKey(), p.getValue().asDouble()));
                logP.put(e.getKey(), table);
            });
        }

        /** score(c) = log P(c) + Σ_t log P(t | c) — identique au calcul d'entraînement. */
        Map<String, Double> scores(Set<String> jetons) {
            Map<String, Double> resultat = new LinkedHashMap<>();
            for (String classe : classes) {
                double s = logPrior.getOrDefault(classe, 0.0);
                Map<String, Double> table = logP.getOrDefault(classe, Map.of());
                double repli = defaut.getOrDefault(classe, -20.0);
                for (String jeton : jetons) {
                    s += table.getOrDefault(jeton, repli);
                }
                resultat.put(classe, s);
            }
            return resultat;
        }

        /** Classe la plus probable, et la marge avec la suivante (sert de mesure de confiance). */
        Map.Entry<String, Double> predire(Set<String> jetons) {
            Map<String, Double> scores = scores(jetons);
            List<Map.Entry<String, Double>> tri = new ArrayList<>(scores.entrySet());
            tri.sort(Comparator.comparingDouble((Map.Entry<String, Double> e) -> e.getValue()).reversed());
            if (tri.isEmpty()) {
                return null;
            }
            return tri.get(0);
        }
    }

    private static final Map<String, Double> IDF = new HashMap<>();
    private static final double IDF_MAX;
    private static final Map<String, Map<String, String>> SECTIONS = new HashMap<>();
    private static final Tete TETE_NOTION;
    private static final Tete TETE_DECISION;
    private static final double COUVERTURE_MIN;

    static {
        Map<String, Double> idf = new HashMap<>();
        double max = 1.0;
        Map<String, Map<String, String>> sections = new HashMap<>();
        Tete notion = null;
        Tete decision = null;
        double seuil = 0.10;
        if (RACINE != null) {
            RACINE.path("idf").fields().forEachRemaining(e -> idf.put(e.getKey(), e.getValue().asDouble()));
            for (double v : idf.values()) {
                max = Math.max(max, v);
            }
            RACINE.path("sections").fields().forEachRemaining(langue -> {
                Map<String, String> table = new HashMap<>();
                langue.getValue().fields().forEachRemaining(
                        n -> table.put(n.getKey(), n.getValue().asText()));
                sections.put(langue.getKey(), table);
            });
            notion = new Tete(RACINE.path("notion"));
            decision = new Tete(RACINE.path("decision"));
            seuil = RACINE.path("seuil").path("couvertureMin").asDouble(seuil);
        }
        IDF.putAll(idf);
        IDF_MAX = max;
        SECTIONS.putAll(sections);
        TETE_NOTION = notion;
        TETE_DECISION = decision;
        COUVERTURE_MIN = seuil;
    }

    private static JsonNode charger() {
        try (InputStream in = ModeleLocal.class.getResourceAsStream(RESSOURCE)) {
            if (in == null) {
                return null;
            }
            return MAPPER.readTree(in);
        } catch (Exception e) {
            System.err.println("[ia] modèle local illisible : " + e);
            return null;
        }
    }

    /** Le fichier de poids a-t-il pu être chargé ? (sans lui, ce modèle refuse tout.) */
    public static boolean disponible() {
        return RACINE != null && TETE_NOTION != null && TETE_DECISION != null;
    }

    public static int tailleVocabulaire() {
        return IDF.size();
    }

    public static double couvertureMin() {
        return COUVERTURE_MIN;
    }

    public static List<String> classesNotion() {
        return TETE_NOTION == null ? List.of() : TETE_NOTION.classes;
    }

    public static List<String> classesDecision() {
        return TETE_DECISION == null ? List.of() : TETE_DECISION.classes;
    }

    public static Map<String, Map<String, String>> sections() {
        return SECTIONS;
    }

    /** Empreintes des fichiers qui ont produit le modèle : sert au test de cohérence. */
    public static String empreinte(String nom) {
        return RACINE == null ? "" : RACINE.path("sources").path(nom).asText("");
    }

    // ------------------------------------------------------------------ fragments du cours
    private record Fragment(String langage, String section, String contenu) {
    }

    private static final List<Fragment> CORPUS = chargerCorpus();

    private static List<Fragment> chargerCorpus() {
        List<Fragment> fragments = new ArrayList<>();
        try (InputStream in = ModeleLocal.class
                .getResourceAsStream("/fr/ihm2026/ia/corpus-fragments.tsv")) {
            if (in == null) {
                return fragments;
            }
            BufferedReader lecteur =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                String[] colonnes = ligne.split("\t", 3);
                if (colonnes.length == 3) {
                    fragments.add(new Fragment(colonnes[0], colonnes[1],
                            colonnes[2].replace("\\n", "\n")));
                }
            }
        } catch (Exception e) {
            System.err.println("[ia] corpus illisible pour le modèle local : " + e);
        }
        return fragments;
    }

    public static int tailleCorpus() {
        return CORPUS.size();
    }

    // ------------------------------------------------------------------ inference
    @Override
    public String identifiant() {
        return "modele-local@" + VERSION;
    }

    /** Part pondérée des mots de la question que le modèle connaît (cf. entrainer.py). */
    public static double couverture(Set<String> jetons) {
        if (jetons.isEmpty()) {
            return 0.0;
        }
        double connu = 0;
        double total = 0;
        for (String jeton : jetons) {
            double poids = IDF.getOrDefault(jeton, 0.0);
            connu += poids;
            total += poids > 0 ? poids : IDF_MAX;
        }
        return total == 0 ? 0.0 : connu / total;
    }

    /** Notion prédite pour une question (« DIVERS » si le modèle n'est pas chargé). */
    public static String notionDe(String question) {
        if (TETE_NOTION == null) {
            return "DIVERS";
        }
        Map.Entry<String, Double> choix = TETE_NOTION.predire(Tokeniseur.mots(question));
        return choix == null ? "DIVERS" : choix.getKey();
    }

    @Override
    public ReponseIa repondre(ContexteIa contexte) {
        if (!disponible()) {
            return ReponseIa.refus(MotifRefus.INDISPONIBLE, VERSION);
        }
        String question = contexte.question();
        Set<String> jetons = Tokeniseur.mots(question);
        if (jetons.isEmpty()) {
            return ReponseIa.refus(MotifRefus.HORS_PERIMETRE, VERSION);
        }

        // 1. La question parle-t-elle du domaine ? (porte indépendante de l'apprentissage)
        if (couverture(jetons) < COUVERTURE_MIN) {
            return ReponseIa.refus(MotifRefus.HORS_PERIMETRE, VERSION);
        }

        // 2. La demande est-elle acceptable ? (tête de décision apprise)
        Map.Entry<String, Double> decision = TETE_DECISION.predire(jetons);
        if (decision != null && !"AUCUN".equals(decision.getKey())) {
            try {
                return ReponseIa.refus(MotifRefus.valueOf(decision.getKey()), VERSION);
            } catch (IllegalArgumentException ignore) {
                return ReponseIa.refus(MotifRefus.HORS_PERIMETRE, VERSION);
            }
        }

        // 3. De quoi parle-t-elle ? (tête de notion apprise)
        Map.Entry<String, Double> notion = TETE_NOTION.predire(jetons);
        if (notion == null) {
            return ReponseIa.refus(MotifRefus.HORS_PERIMETRE, VERSION);
        }
        String section = SECTIONS.getOrDefault(contexte.langage().code(), Map.of())
                .get(notion.getKey());
        if (section == null) {
            // Parcours sans cours pour cette notion : mieux vaut le dire que citer à côté.
            return ReponseIa.refus(MotifRefus.HORS_PERIMETRE, VERSION);
        }

        // 4. La réponse est extractive : une phrase du cours retrouvé, jamais une rédaction.
        List<Fragment> duSection = new ArrayList<>();
        for (Fragment f : CORPUS) {
            if (f.section().equals(section)) {
                duSection.add(f);
            }
        }
        if (duSection.isEmpty()) {
            return ReponseIa.refus(MotifRefus.HORS_PERIMETRE, VERSION);
        }
        Fragment retenu = choisirFragment(duSection, jetons);
        String texte = composer(section, extrait(retenu.contenu(), jetons));

        return new ReponseIa(texte,
                List.of(new ReponseIa.Citation(section, contexte.langage().code())),
                MotifRefus.AUCUN, VERSION);
    }

    private static String composer(String section, String extrait) {
        String debut = "D'après le cours « " + section + " » : ";
        String fin = " Cette réponse n'est pas une correction : relis la section citée, puis reviens "
                + "à l'exercice. Je ne note pas et je ne valide pas.";
        int place = Math.max(0, LONGUEUR_MAX - debut.length() - fin.length());
        String corps = extrait.length() > place ? extrait.substring(0, Math.max(0, place - 1)) + "…"
                : extrait;
        return debut + corps + fin;
    }

    /**
     * Choix du fragment : d'abord celui qui se lit comme un cours, puis, à égalité, celui qui
     * partage le plus de mots rares avec la question. L'ordre inverse a été mesuré : il fait gagner
     * un témoin sur 60 et dégrade la lisibilité de l'extrait — écarté.
     */
    private static Fragment choisirFragment(List<Fragment> candidats, Set<String> jetons) {
        return candidats.stream()
                .max(Comparator.comparingInt((Fragment f) -> prose(f.contenu()))
                        .thenComparingDouble(f -> recouvrement(f.contenu(), jetons))
                        .thenComparing(Fragment::contenu))
                .orElse(candidats.get(0));
    }

    private static int prose(String contenu) {
        int n = 0;
        for (String phrase : contenu.split("(?<=[.!?])\\s+")) {
            if (phrase.strip().length() >= 25) {
                n++;
            }
        }
        return n;
    }

    private static double recouvrement(String contenu, Set<String> jetons) {
        Set<String> propres = Tokeniseur.mots(contenu);
        double score = 0;
        for (String jeton : jetons) {
            if (propres.contains(jeton)) {
                score += IDF.getOrDefault(jeton, 1.0);
            }
        }
        return score;
    }

    /** Phrase du fragment la plus proche de la question (pondération par la rareté des mots). */
    private static String extrait(String contenu, Set<String> jetons) {
        String retenu = contenu.length() > 240 ? contenu.substring(0, 240) + "…" : contenu;
        double meilleur = -1;
        for (String phrase : contenu.split("(?<=[.!?])\\s+|\\n\\s*\\n")) {
            String propre = phrase.replaceAll("\\s+", " ").strip();
            if (propre.length() < 25) {
                continue;
            }
            double score = recouvrement(propre, jetons);
            if (score > meilleur) {
                meilleur = score;
                retenu = propre;
            }
        }
        return retenu;
    }

    /** Utilisé par l'écran pour afficher la notion travaillée avant même la réponse. */
    public static String notionDe(ContexteIa contexte) {
        return notionDe(contexte.question());
    }

    /** Commodité de test : le langage par défaut d'un contexte (Python) reste sans effet ici. */
    public static String sectionPour(Session.Langage langage, String notion) {
        return SECTIONS.getOrDefault(langage.code(), Map.of()).get(notion);
    }
}
