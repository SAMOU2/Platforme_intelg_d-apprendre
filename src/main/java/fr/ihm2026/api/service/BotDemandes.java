package fr.ihm2026.api.service;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Bot d'acceptation des demandes d'inscription à une offre de formation.
 *
 * <p>Comme {@link BotInscription}, il s'agit d'un moteur de règles explicables (IA
 * symbolique) : rien n'est envoyé à un modèle de langage et aucune décision
 * pédagogique (note, validation de niveau) n'est prise ici. Le bot répond seulement à
 * une question : <b>une demande d'accès aux cours peut-elle être acceptée sans
 * attendre l'enseignant ?</b></p>
 *
 * <p>Politique lue sur PC1 avec {@code IHM_DEMANDE_BOT_MODE} :</p>
 * <ul>
 *   <li>{@code MANUEL} — le bot n'accepte jamais ; chaque demande attend l'enseignant ;</li>
 *   <li>{@code OFFRES} (défaut) — le bot accepte seulement si l'enseignant a activé
 *       l'acceptation automatique sur son offre ;</li>
 *   <li>{@code AUTO} — le bot accepte toute demande dont le nom saisi est lisible.</li>
 * </ul>
 *
 * <p>Dans tous les cas, une offre fermée et un nom illisible restent à traiter par
 * l'enseignant, et l'accès accordé est tracé ({@code decide_par = 'BOT'}) dans la
 * demande et dans le journal d'audit.</p>
 */
public final class BotDemandes {

    public enum Mode {
        MANUEL,
        OFFRES,
        AUTO
    }

    /** Décision du bot : acceptation immédiate ou attente de l'enseignant, avec son motif. */
    public record Decision(boolean autoAccepter, String motif) {
    }

    /** Valeur enregistrée dans {@code demandes_inscription.decide_par}. */
    public static final String DECIDEUR_BOT = "BOT";

    public static final int NOM_MIN = 2;
    public static final int NOM_MAX = 120;

    private final Mode mode;

    public BotDemandes(Map<String, String> environnement) {
        Objects.requireNonNull(environnement, "environnement");
        String brut = environnement.getOrDefault("IHM_DEMANDE_BOT_MODE", "OFFRES");
        try {
            mode = Mode.valueOf(brut == null || brut.isBlank()
                    ? "OFFRES"
                    : brut.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "IHM_DEMANDE_BOT_MODE doit valoir MANUEL, OFFRES ou AUTO.", ex);
        }
    }

    public static BotDemandes depuisEnvironnement() {
        return new BotDemandes(System.getenv());
    }

    public Mode mode() {
        return mode;
    }

    /**
     * Analyse une demande d'inscription.
     *
     * @param nomDemandeur          nom saisi par l'apprenant dans le formulaire d'inscription
     * @param offreOuverte          l'enseignant accepte-t-il encore de nouvelles demandes
     * @param offreAutoAcceptation  l'enseignant a-t-il activé le bot sur cette offre
     */
    public Decision analyser(String nomDemandeur, boolean offreOuverte,
            boolean offreAutoAcceptation) {
        if (!offreOuverte) {
            return new Decision(false,
                    "Offre fermée : l'enseignant n'accepte plus de nouvelles demandes.");
        }
        String nom = nomDemandeur == null ? "" : nomDemandeur.strip();
        if (nom.length() < NOM_MIN || nom.length() > NOM_MAX) {
            return new Decision(false,
                    "Nom saisi illisible : décision laissée à l'enseignant.");
        }
        if (nom.chars().noneMatch(Character::isLetter)) {
            return new Decision(false,
                    "Nom saisi sans aucune lettre : décision laissée à l'enseignant.");
        }
        return switch (mode) {
            case MANUEL -> new Decision(false,
                    "Bot en mode manuel : l'enseignant décide de chaque demande.");
            case OFFRES -> offreAutoAcceptation
                    ? new Decision(true,
                            "Acceptation automatique activée sur l'offre : accès aux cours accordé par le bot.")
                    : new Decision(false,
                            "Offre sans acceptation automatique : l'enseignant décide.");
            case AUTO -> new Decision(true,
                    "Politique du serveur en acceptation automatique : accès aux cours accordé par le bot.");
        };
    }
}
