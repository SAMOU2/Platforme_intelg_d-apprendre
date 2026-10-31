package fr.ihm2026.api.ia;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Garde-fous de l'agent — étape 6.
 *
 * <p><b>Principe :</b> ce n'est pas le modèle qui garantit les règles du projet, c'est ce filtre.
 * Un modèle peut toujours produire une note ou une solution complète ; le code, lui, refuse de
 * l'afficher. Les interdits du cadrage (RG-13) deviennent ainsi des propriétés vérifiables :</p>
 * <ul>
 *   <li>ne note pas — aucune sortie contenant une note chiffrée n'est transmise ;</li>
 *   <li>ne valide pas, ne débloque pas — aucune sortie annonçant une validation n'est transmise ;</li>
 *   <li>ne donne pas la solution — une demande de code complet est refusée en entrée.</li>
 * </ul>
 *
 * <p>Le filtre agit deux fois : <b>en entrée</b> (requête de l'apprenant) et <b>en sortie</b>
 * (réponse du modèle).</p>
 */
public final class GardeFou {

    /** Demandes de décision réservées à l'enseignant. */
    private static final List<Pattern> DEMANDES_DECISION = List.of(
            Pattern.compile("\\bnote[- ]?moi\\b"),
            Pattern.compile("\\bcombien (de points|j'ai|vais[- ]je avoir)\\b"),
            Pattern.compile("\\bquelle (est )?ma note\\b"),
            // « donne-moi la note de Karim » : demande portant sur un tiers (cas R08 du contrat).
            Pattern.compile("\\b(la|les|sa|son) notes? (de|d')\\b"),
            Pattern.compile("\\bvalide[rz]? (mon|le|ce) (niveau|test|exercice)\\b"),
            Pattern.compile("\\bvalides?[- ]tu\\b"),
            Pattern.compile("\\btu valides?\\b"),
            Pattern.compile("\\b(d[ée]bloque|d[ée]bloques?[- ]tu)\\b"),
            Pattern.compile("\\bje (suis|serai) (re[çc]u|admis|valid[ée])\\b"),
            Pattern.compile("\\b(est-ce que )?(j'ai|ai[- ]je) r[ée]ussi\\b"),
            Pattern.compile("\\bcorrige[rz]? (mon|ma|le) (exercice|test|code|travail)\\b"),
            // Position B : un enseignant qui demande une notation automatique (cas B03).
            Pattern.compile("\\bnote[rz]? (mes|les|la|le|mon|ma) \\w+"),
            Pattern.compile("\\battribu(e|er|ez) (une|des) notes?\\b"),
            Pattern.compile("\\bcorriger (les|mes) (copies|exercices|tests)\\b"));

    /** Demandes de solution toute faite. */
    private static final List<Pattern> DEMANDES_SOLUTION = List.of(
            Pattern.compile("\\bdonne[- ]moi (le|la|les) (code|solution|r[ée]ponse)s?\\b"),
            Pattern.compile("\\bla solution\\b"),
            Pattern.compile("\\b(code|solution|r[ée]ponse) (complet|enti[èe]re?|finale?)\\b"),
            Pattern.compile("\\b[ée]cris?[- ]moi (le|la|un|une) (code|fonction|programme|classe)\\b"),
            Pattern.compile("\\b(copie|recopie)[- ]?(le|la|moi)\\b"),
            Pattern.compile("\\bsans (r[ée]fl[ée]chir|chercher)\\b"));

    /** Formules de notation détectables dans une sortie de modèle. */
    private static final List<Pattern> SORTIE_NOTE = List.of(
            Pattern.compile("\\b\\d{1,2}\\s*/\\s*20\\b"),
            Pattern.compile("\\b\\d{1,2}\\s+sur\\s+20\\b"),
            Pattern.compile("\\bnote\\s*[:=]\\s*\\d"),
            Pattern.compile("\\bje (te|vous) (mets|attribue|donne)\\s+(une note|\\d)"),
            Pattern.compile("\\b(ta|votre) note (est|sera)\\b"),
            Pattern.compile("\\b[ée]valuation\\s*[:=]\\s*\\d"));

    /** Formules de validation ou de déblocage détectables dans une sortie de modèle. */
    private static final List<Pattern> SORTIE_VALIDATION = List.of(
            Pattern.compile("\\bniveau (est )?valid[ée]\\b"),
            Pattern.compile("\\b(j'ai|j’ai) valid[ée]\\b"),
            Pattern.compile("\\b(niveau|parcours) (d[ée]bloqu[ée]|d[ée]verrouill[ée])\\b"),
            Pattern.compile("\\btu peux passer (au|au niveau|à l'?exercice)\\b"),
            Pattern.compile("\\btu as r[ée]ussi\\b"));

    private GardeFou() {
    }

    /** Normalisation commune : minuscules, sans accents, apostrophes uniformisées. */
    static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(texte.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sansAccents.replace('’', '\'').replaceAll("\\s+", " ").strip();
    }

    /**
     * Contrôle d'entrée : décide s'il faut refuser la demande avant même d'appeler le modèle.
     *
     * @return le motif de refus, ou {@link MotifRefus#AUCUN} si la demande est acceptable
     */
    public static MotifRefus verifierDemande(String question) {
        String texte = normaliser(question);
        if (texte.isEmpty()) {
            return MotifRefus.HORS_PERIMETRE;
        }
        for (Pattern p : DEMANDES_DECISION) {
            if (p.matcher(texte).find()) {
                return MotifRefus.DECISION_DEMANDEE;
            }
        }
        for (Pattern p : DEMANDES_SOLUTION) {
            if (p.matcher(texte).find()) {
                return MotifRefus.SOLUTION_DEMANDEE;
            }
        }
        return MotifRefus.AUCUN;
    }

    /**
     * Contrôle de sortie : filet de sécurité si le modèle produit malgré tout une note,
     * une validation ou une solution complète.
     */
    public static ReponseIa filtrer(ReponseIa reponse) {
        if (reponse.estRefus()) {
            return reponse;
        }
        String texte = normaliser(reponse.reponse());
        for (Pattern p : SORTIE_NOTE) {
            if (p.matcher(texte).find()) {
                return ReponseIa.refus(MotifRefus.DECISION_DEMANDEE, reponse.versionPrompt());
            }
        }
        for (Pattern p : SORTIE_VALIDATION) {
            if (p.matcher(texte).find()) {
                return ReponseIa.refus(MotifRefus.DECISION_DEMANDEE, reponse.versionPrompt());
            }
        }
        return reponse;
    }

    /** Vrai si le texte contient une note chiffrée (utilisé par les tests adversariaux). */
    public static boolean contientUneNote(String texte) {
        String t = normaliser(texte);
        return SORTIE_NOTE.stream().anyMatch(p -> p.matcher(t).find());
    }

    /** Vrai si le texte annonce une validation ou un déblocage. */
    public static boolean annonceUneValidation(String texte) {
        String t = normaliser(texte);
        return SORTIE_VALIDATION.stream().anyMatch(p -> p.matcher(t).find());
    }
}
