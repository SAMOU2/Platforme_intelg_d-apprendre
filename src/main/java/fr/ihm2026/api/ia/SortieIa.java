package fr.ihm2026.api.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Format de sortie de l'agent — étape 5.
 *
 * <p>La sortie du modèle n'est pas un texte libre : c'est un objet de cinq champs, décrit par
 * {@code ia/prompts/sortie-schema.json} et vérifié ici, dans le code. Un modèle distant peut
 * renvoyer n'importe quoi ; {@link #lire(String)} ne laisse passer que ce qui est conforme.</p>
 *
 * <p>Deux choix de conception, tous deux vérifiés par les tests :</p>
 * <ul>
 *   <li>{@code refus} <b>est</b> le motif, pas un booléen : avec un booléen et un motif séparés, un
 *       modèle peut écrire {@code refus: true} et {@code motif: AUCUN} ; avec un seul champ, la
 *       contradiction est impossible.</li>
 *   <li>{@code citations} est vide <b>si et seulement si</b> c'est un refus. Une réponse sans
 *       citation n'est pas affichable : elle est rejetée, pas rafistolée.</li>
 * </ul>
 */
public record SortieIa(
        String version,
        String reponse,
        List<ReponseIa.Citation> citations,
        String notion,
        MotifRefus refus) {

    public static final List<String> NOTIONS =
            List.of("VAR", "COND", "BOUCLE", "FONC", "COLL", "ERREUR", "DIVERS");
    public static final List<String> LANGAGES = List.of("python", "java", "cpp");
    public static final int LONGUEUR_MAX_REPONSE = 600;
    public static final int CITATIONS_MAX = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SortieIa {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    // ------------------------------------------------------------------ lecture
    /**
     * Lit et valide une sortie. Vide = sortie rejetée (JSON illisible, champ manquant, refus et
     * citations incohérents, langage ou notion inconnus). Aucune exception ne sort d'ici.
     */
    public static Optional<SortieIa> lire(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode racine = MAPPER.readTree(json);
            // Une réponse de fournisseur enveloppe parfois le JSON dans du texte : on l'accepte,
            // mais le contenu doit rester conforme.
            if (racine.has("choices")) {
                JsonNode contenu = racine.path("choices").path(0).path("message").path("content");
                if (!contenu.isTextual()) {
                    return Optional.empty();
                }
                racine = MAPPER.readTree(contenu.asText());
            }
            if (!racine.isObject()) {
                return Optional.empty();
            }
            for (String champ : List.of("version", "reponse", "citations", "notion", "refus")) {
                if (!racine.has(champ)) {
                    return Optional.empty();
                }
            }

            MotifRefus motif;
            try {
                motif = MotifRefus.valueOf(racine.get("refus").asText());
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }

            String reponse = racine.get("reponse").asText("");
            if (reponse.isBlank() || reponse.length() > LONGUEUR_MAX_REPONSE) {
                return Optional.empty();
            }

            String notion = racine.get("notion").asText("");
            if (!NOTIONS.contains(notion)) {
                return Optional.empty();
            }

            JsonNode noeudCitations = racine.get("citations");
            if (!noeudCitations.isArray() || noeudCitations.size() > CITATIONS_MAX) {
                return Optional.empty();
            }
            List<ReponseIa.Citation> citations = new ArrayList<>();
            for (JsonNode c : noeudCitations) {
                String section = c.path("section").asText("");
                String langage = c.path("langage").asText("");
                if (section.length() < 3 || !LANGAGES.contains(langage)) {
                    return Optional.empty();
                }
                citations.add(new ReponseIa.Citation(section, langage));
            }

            // La règle qui rend le format utile : réponse citée, ou refus sans citation.
            boolean refus = motif.estRefus();
            if (refus == !citations.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new SortieIa(racine.get("version").asText(""), reponse,
                    citations, notion, motif));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ écriture
    /** Sérialise une réponse du service au format du contrat (ce que verrait l'API REST). */
    public static String enJson(ReponseIa reponse) {
        SortieIa sortie = new SortieIa(
                reponse.versionPrompt(),
                reponse.reponse(),
                reponse.citations(),
                notionDe(reponse),
                reponse.motif());
        ObjectNode racine = MAPPER.createObjectNode();
        racine.put("version", sortie.version());
        racine.put("reponse", sortie.reponse());
        ArrayNode citations = racine.putArray("citations");
        for (ReponseIa.Citation c : sortie.citations()) {
            citations.addObject().put("section", c.section()).put("langage", c.langage());
        }
        racine.put("notion", sortie.notion());
        racine.put("refus", sortie.refus().name());
        return racine.toString();
    }

    /** Notion déduite du titre de section ; {@code DIVERS} quand rien ne correspond. */
    static String notionDe(ReponseIa reponse) {
        for (ReponseIa.Citation c : reponse.citations()) {
            String s = c.section().toLowerCase();
            if (s.startsWith("variables et types")) {
                return "VAR";
            }
            if (s.startsWith("conditions")) {
                return "COND";
            }
            if (s.startsWith("boucles")) {
                return "BOUCLE";
            }
            if (s.startsWith("fonctions") || s.startsWith("méthodes")) {
                return "FONC";
            }
            if (s.startsWith("collections") || s.startsWith("listes")
                    || s.startsWith("conteneurs")) {
                return "COLL";
            }
            if (s.startsWith("erreurs") || s.startsWith("exceptions")) {
                return "ERREUR";
            }
        }
        return "DIVERS";
    }

    public ReponseIa versReponse() {
        return new ReponseIa(reponse, citations, refus, version);
    }
}
