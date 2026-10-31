package fr.ihm2026.api.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Contrat de refus — source unique des libellés affichés (étape 1, version {@code contrat-v2}).
 *
 * <p>Les messages ne sont plus écrits dans le code Java : ils sont lus dans
 * {@code fr/ihm2026/ia/cas-de-refus.json}, le même fichier que celui qui produit la note de
 * cadrage et que {@code ContratUsageTest} rejoue. Trois conséquences :</p>
 * <ul>
 *   <li>le document, l'écran et le code ne peuvent pas diverger ;</li>
 *   <li>un message se relit sans ouvrir de source Java (utile pour un relecteur pédagogique) ;</li>
 *   <li>si le fichier est absent, un libellé de repli subsiste : l'application démarre quand même.</li>
 * </ul>
 */
public final class ContratRefus {

    /** Un cas du contrat, tel que documenté et testé. */
    public record Cas(String id, String position, String famille, String situation, String demande,
                      MotifRefus motif, String messageAffiche, String garantie, String test) {
    }

    private static final String RESSOURCE = "/fr/ihm2026/ia/cas-de-refus.json";
    private static final JsonNode RACINE = charger();

    private static final Map<MotifRefus, String> MESSAGES = new EnumMap<>(MotifRefus.class);
    private static final List<Cas> CAS = new ArrayList<>();
    private static String version = "indisponible";

    static {
        if (RACINE != null) {
            version = RACINE.path("version").asText(version);
            JsonNode messages = RACINE.path("vocabulaire").path("messages");
            messages.fieldNames().forEachRemaining(nom -> {
                try {
                    MESSAGES.put(MotifRefus.valueOf(nom), messages.get(nom).asText());
                } catch (IllegalArgumentException ignore) {
                    // Motif inconnu du code : signalé par les tests, jamais bloquant à l'exécution.
                }
            });
            for (JsonNode c : RACINE.path("cas")) {
                try {
                    CAS.add(new Cas(c.path("id").asText(), c.path("position").asText("A"),
                            c.path("famille").asText(), c.path("situation").asText(),
                            c.path("demande").asText(), MotifRefus.valueOf(c.path("motif").asText()),
                            c.path("messageAffiche").asText(), c.path("garantie").asText(),
                            c.path("test").asText()));
                } catch (IllegalArgumentException ignore) {
                    // Cas dont le motif n'existe pas : ignoré ici, refusé par les tests.
                }
            }
        }
    }

    private ContratRefus() {
    }

    private static JsonNode charger() {
        try (InputStream in = ContratRefus.class.getResourceAsStream(RESSOURCE)) {
            return in == null ? null : new ObjectMapper().readTree(in);
        } catch (Exception e) {
            System.err.println("[ia] contrat de refus illisible : " + e);
            return null;
        }
    }

    /** Version du contrat chargé (« contrat-v2 »), affichée dans le journal d'assistance. */
    public static String version() {
        return version;
    }

    /** Libellé exact d'un motif, ou le libellé de repli de l'énumération. */
    public static String message(MotifRefus motif) {
        return MESSAGES.getOrDefault(motif, motif.messageParDefaut());
    }

    /** Les cas documentés, dans l'ordre du fichier. */
    public static List<Cas> cas() {
        return List.copyOf(CAS);
    }

    public static List<Cas> casPour(String position) {
        return CAS.stream().filter(c -> c.position().equals(position)).toList();
    }
}
