package fr.ihm2026.api.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Découpage en mots — une seule définition, partagée par le modèle simulé et le modèle local.
 *
 * <p>Jusqu'ici chaque classe avait sa table de synonymes et sa liste de mots vides. Deux copies,
 * deux dérives possibles : le modèle simulé trouvait un fragment que le modèle local ne trouvait
 * plus, avec les mêmes mots. Les tables viennent désormais <b>du fichier du modèle</b>
 * ({@code ia/modele/modele-local-*.json}, section {@code tokeniseur}), lui-même généré par
 * {@code ia/modele/entrainer.py}. Le Python et le Java lisent donc la même table, et un test compare
 * les découpages mot pour mot ({@code ModeleLocalTest.tokeniseurIdentiqueAuPython}).</p>
 */
public final class Tokeniseur {

    /** Version du découpage, écrite dans le fichier du modèle : la changer invalide les poids. */
    public static final String VERSION = "tok-v1";

    static final String RESSOURCE_MODELE = "/fr/ihm2026/ia/" + ModeleLocal.VERSION + ".json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Table de repli : si le fichier du modèle manque, l'application doit quand même démarrer. */
    private static final Map<String, String> FAMILLES_REPLI = Map.ofEntries(
            Map.entry("return", "retour"), Map.entry("returns", "retour"),
            Map.entry("retourne", "retour"), Map.entry("retourner", "retour"),
            Map.entry("retournee", "retour"), Map.entry("retournes", "retour"),
            Map.entry("renvoie", "retour"), Map.entry("renvoyer", "retour"),
            Map.entry("renvoient", "retour"), Map.entry("dividende", "division"),
            Map.entry("diviser", "division"), Map.entry("divise", "division"),
            Map.entry("divisant", "division"), Map.entry("moyenne", "moyenne"),
            Map.entry("moyennes", "moyenne"), Map.entry("indentation", "indentation"),
            Map.entry("indente", "indentation"), Map.entry("vide", "vide"),
            Map.entry("vides", "vide"), Map.entry("empty", "vide"));

    private static final Set<String> MOTS_VIDES_REPLI = Set.of(
            "le", "la", "les", "un", "une", "des", "du", "de", "d", "et", "ou", "a", "au", "aux",
            "en", "dans", "sur", "par", "pour", "avec", "sans", "que", "qui", "quoi", "est", "sont",
            "ce", "cet", "cette", "il", "elle", "on", "nous", "vous", "ils", "ne", "pas", "plus",
            "se", "sa", "son", "ses", "leur", "y", "si", "comme", "comment", "pourquoi", "quel",
            "quelle", "quels", "quelles", "quand", "fait", "faire", "peut", "peux", "mon", "ma",
            "mes", "ton", "ta", "tes", "je", "tu", "moi", "toi", "c", "l", "s", "n", "j");

    private static final Map<String, String> FAMILLES;
    private static final Set<String> MOTS_VIDES;
    /** true si les tables viennent du fichier du modèle, false si c'est la table de repli. */
    private static final boolean DEPUIS_LE_MODELE;

    static {
        Map<String, String> familles = FAMILLES_REPLI;
        Set<String> vides = MOTS_VIDES_REPLI;
        boolean depuis = false;
        try (InputStream in = Tokeniseur.class.getResourceAsStream(RESSOURCE_MODELE)) {
            if (in != null) {
                JsonNode noeud = MAPPER.readTree(in).path("tokeniseur");
                if (noeud.has("familles") && noeud.has("motsVides")) {
                    var f = new java.util.HashMap<String, String>();
                    noeud.get("familles").fields().forEachRemaining(
                            e -> f.put(e.getKey(), e.getValue().asText()));
                    var v = new java.util.HashSet<String>();
                    noeud.get("motsVides").forEach(n -> v.add(n.asText()));
                    familles = Map.copyOf(f);
                    vides = Set.copyOf(v);
                    depuis = true;
                }
            }
        } catch (Exception e) {
            System.err.println("[ia] tokeniseur : fichier du modèle illisible (" + e + ")");
        }
        FAMILLES = familles;
        MOTS_VIDES = vides;
        DEPUIS_LE_MODELE = depuis;
    }

    private Tokeniseur() {
    }

    /** true si les tables de synonymes et de mots vides ont été lues dans le fichier du modèle. */
    public static boolean depuisLeModele() {
        return DEPUIS_LE_MODELE;
    }

    public static Map<String, String> familles() {
        return FAMILLES;
    }

    /**
     * Découpe un texte en jetons porteurs de sens : accents retirés, minuscules, synonymes
     * regroupés, mots vides écartés, <b>chiffres conservés</b> (une question de code parle de 7 et
     * de 2 — la version qui les jetait refusait « Que vaut 7 // 2 ? »).
     */
    public static Set<String> mots(String texte) {
        Set<String> jetons = new LinkedHashSet<>();
        if (texte == null) {
            return jetons;
        }
        String normalise = Normalizer.normalize(texte.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        for (String mot : normalise.split("[^a-z0-9_]+")) {
            boolean porteur = mot.length() > 2 || (!mot.isEmpty() && mot.chars().allMatch(Character::isDigit));
            if (porteur && !MOTS_VIDES.contains(mot)) {
                jetons.add(FAMILLES.getOrDefault(mot, mot));
            }
        }
        return jetons;
    }

    /** Les mêmes jetons, dans l'ordre : utilisé par le test de comparaison avec le Python. */
    public static List<String> liste(String texte) {
        return List.copyOf(mots(texte));
    }
}
