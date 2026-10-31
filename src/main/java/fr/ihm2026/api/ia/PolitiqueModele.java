package fr.ihm2026.api.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Politique de repli du modèle — étape 4.
 *
 * <p>Elle porte trois décisions écrites dans {@code strategie-modele.json} : le délai maximal
 * d'un appel, le nombre d'échecs consécutifs avant ouverture du disjoncteur, et la durée de
 * refroidissement. Le fichier est la source unique : le code le lit, les tests le comparent au
 * code, le document de l'étape est généré depuis lui.</p>
 *
 * <p><b>Ce qui ouvre le disjoncteur</b> : les échecs <em>techniques</em> signalés par
 * {@link ModeleDistant} (délai dépassé, erreur HTTP, réponse illisible). Une réponse
 * {@link MotifRefus#INDISPONIBLE} due à l'absence de fragment pertinent n'est pas une panne du
 * fournisseur : elle ne doit pas désactiver l'assistance pour tout le monde.</p>
 */
public final class PolitiqueModele {

    private static final String RESSOURCE = "/fr/ihm2026/ia/strategie-modele.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int DEFAUT_DELAI_MS = 3000;
    private static final int DEFAUT_ECHECS = 3;
    private static final int DEFAUT_REFROIDISSEMENT_S = 120;

    private static int delaiMs = DEFAUT_DELAI_MS;
    private static int echecsAvantOuverture = DEFAUT_ECHECS;
    private static int refroidissementS = DEFAUT_REFROIDISSEMENT_S;
    private static String version = "strategie-v1";

    private static int echecs = 0;
    private static long ouvertJusqua = 0L;
    private static LongSupplier horloge = System::currentTimeMillis;

    static {
        charger();
    }

    private PolitiqueModele() {
    }

    /** Lit les paramètres dans le fichier de stratégie ; garde les valeurs par défaut s'il manque. */
    private static void charger() {
        try (InputStream in = PolitiqueModele.class.getResourceAsStream(RESSOURCE)) {
            if (in == null) {
                return;
            }
            JsonNode racine = MAPPER.readTree(in);
            JsonNode repli = racine.path("politiqueRepli");
            if (repli.hasNonNull("delaiMs")) {
                delaiMs = repli.get("delaiMs").asInt(DEFAUT_DELAI_MS);
            }
            JsonNode disjoncteur = repli.path("disjoncteur");
            if (disjoncteur.hasNonNull("echecsAvantOuverture")) {
                echecsAvantOuverture = disjoncteur.get("echecsAvantOuverture").asInt(DEFAUT_ECHECS);
            }
            if (disjoncteur.hasNonNull("refroidissementS")) {
                refroidissementS = disjoncteur.get("refroidissementS").asInt(DEFAUT_REFROIDISSEMENT_S);
            }
            if (racine.hasNonNull("version")) {
                version = racine.get("version").asText();
            }
        } catch (Exception ignore) {
            // Fichier illisible : les valeurs par défaut permettent de démarrer, l'aide reste bornée.
        }
    }

    public static String version() {
        return version;
    }

    public static Duration delai() {
        return Duration.ofMillis(delaiMs);
    }

    public static int delaiMs() {
        return delaiMs;
    }

    public static int echecsAvantOuverture() {
        return echecsAvantOuverture;
    }

    public static int refroidissementS() {
        return refroidissementS;
    }

    /** État courant, tel que l'IHM doit l'afficher. */
    public static synchronized EtatAgent etat() {
        long maintenant = horloge.getAsLong();
        if (echecs >= echecsAvantOuverture && maintenant < ouvertJusqua) {
            long restant = Math.max(1, (ouvertJusqua - maintenant) / 1000);
            return EtatAgent.desactive(MotifRefus.INDISPONIBLE,
                    "Assistance désactivée après " + echecs + " échecs consécutifs. "
                            + "Nouvelle tentative dans " + restant + " s. Le cours et l'exercice "
                            + "restent utilisables.");
        }
        return EtatAgent.actif();
    }

    public static boolean aideDisponible() {
        return etat().disponible();
    }

    /** Appel réussi : le compteur repart de zéro, le disjoncteur se referme. */
    public static synchronized void signalerSucces() {
        echecs = 0;
        ouvertJusqua = 0L;
    }

    /** Échec technique du fournisseur : au seuil, le disjoncteur s'ouvre pour un temps borné. */
    public static synchronized void signalerEchecTechnique() {
        echecs++;
        if (echecs >= echecsAvantOuverture) {
            ouvertJusqua = horloge.getAsLong() + refroidissementS * 1000L;
        }
    }

    public static synchronized int echecsConsecutifs() {
        return echecs;
    }

    /** Remise à zéro (changement de modèle, tests). */
    public static synchronized void reinitialiser() {
        echecs = 0;
        ouvertJusqua = 0L;
    }

    /** Horloge injectable : c'est ce qui rend le refroidissement testable sans attendre 2 minutes. */
    public static synchronized void reglerHorloge(LongSupplier nouvelle) {
        horloge = nouvelle == null ? System::currentTimeMillis : nouvelle;
    }
}
