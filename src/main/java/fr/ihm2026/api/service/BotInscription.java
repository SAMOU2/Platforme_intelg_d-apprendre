package fr.ihm2026.api.service;

import fr.ihm2026.core.Session;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bot de tri des inscriptions.
 *
 * <p>Il s'agit d'un moteur de règles explicables (IA symbolique), pas d'un modèle de
 * langage : aucune donnée d'inscription n'est envoyée à NVIDIA et aucune décision
 * pédagogique n'est prise ici. Le bot peut seulement pré-accepter des apprenants
 * selon la politique explicitement configurée sur PC1. Le compte reste inactif tant
 * que le PIN envoyé à l'adresse e-mail n'a pas été saisi. Les enseignants et les cas
 * ambigus restent dans la file de l'administrateur.</p>
 */
public final class BotInscription {

    public enum Mode {
        MANUEL,
        APPRENANTS,
        DOMAINES
    }

    public enum Recommandation {
        ACCEPTATION_CONSEILLEE,
        REVISION_ADMIN,
        REFUS_CONSEILLE,
        DEJA_TRAITE
    }

    public record Analyse(Recommandation recommandation, String motif, boolean autoAccepter) {
    }

    private static final String REGEX_DOMAINE =
            "^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$";

    private final Mode mode;
    private final Set<String> domainesAuto;
    private final Set<String> domainesBloques;

    public BotInscription(Map<String, String> environnement) {
        Objects.requireNonNull(environnement, "environnement");
        String brut = environnement.getOrDefault("IHM_INSCRIPTION_BOT_MODE", "MANUEL");
        try {
            mode = Mode.valueOf(brut == null || brut.isBlank()
                    ? "MANUEL"
                    : brut.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "IHM_INSCRIPTION_BOT_MODE doit valoir MANUEL, APPRENANTS ou DOMAINES.", ex);
        }
        domainesAuto = lireDomaines(environnement.get("IHM_INSCRIPTION_DOMAINES_AUTO"),
                "IHM_INSCRIPTION_DOMAINES_AUTO");
        domainesBloques = lireDomaines(environnement.get("IHM_INSCRIPTION_DOMAINES_BLOQUES"),
                "IHM_INSCRIPTION_DOMAINES_BLOQUES");
        if (mode == Mode.DOMAINES && domainesAuto.isEmpty()) {
            throw new IllegalStateException(
                    "IHM_INSCRIPTION_BOT_MODE=DOMAINES exige IHM_INSCRIPTION_DOMAINES_AUTO.");
        }
    }

    public static BotInscription depuisEnvironnement() {
        return new BotInscription(System.getenv());
    }

    public Mode mode() {
        return mode;
    }

    public Analyse analyser(String email, Session.Role role, String statutInscription) {
        if (!"EN_ATTENTE".equals(statutInscription)) {
            return new Analyse(Recommandation.DEJA_TRAITE,
                    "Demande déjà traitée ou compte déjà créé.", false);
        }
        if (role == null) {
            return new Analyse(Recommandation.REVISION_ADMIN,
                    "Rôle inconnu : révision par l'administrateur.", false);
        }
        if (role == Session.Role.ENSEIGNANT) {
            return new Analyse(Recommandation.REVISION_ADMIN,
                    "Rôle enseignant privilégié : validation humaine obligatoire.", false);
        }
        if (role != Session.Role.APPRENANT) {
            return new Analyse(Recommandation.REVISION_ADMIN,
                    "Rôle sensible : validation humaine obligatoire.", false);
        }

        String domaine = domaineEmail(email);
        if (domaine == null) {
            return new Analyse(Recommandation.REVISION_ADMIN,
                    "Domaine e-mail illisible : révision par l'administrateur.", false);
        }
        if (correspond(domaine, domainesBloques)) {
            return new Analyse(Recommandation.REFUS_CONSEILLE,
                    "Domaine e-mail bloqué par la politique du serveur.", false);
        }

        boolean eligible = switch (mode) {
            case MANUEL -> false;
            case APPRENANTS -> true;
            case DOMAINES -> correspond(domaine, domainesAuto);
        };
        if (eligible) {
            return new Analyse(Recommandation.ACCEPTATION_CONSEILLEE,
                    "Apprenant éligible à la pré-acceptation ; le PIN e-mail reste obligatoire.",
                    true);
        }
        if (mode == Mode.DOMAINES) {
            return new Analyse(Recommandation.REVISION_ADMIN,
                    "Domaine hors liste d'auto-acceptation : révision par l'administrateur.", false);
        }
        return new Analyse(Recommandation.REVISION_ADMIN,
                "Bot en mode manuel : décision par l'administrateur.", false);
    }

    private static Set<String> lireDomaines(String valeur, String nomVariable) {
        if (valeur == null || valeur.isBlank()) {
            return Set.of();
        }
        Set<String> domaines = new LinkedHashSet<>();
        for (String brut : valeur.split("[,;\\s]+")) {
            if (brut == null || brut.isBlank()) {
                continue;
            }
            String domaine = normaliserDomaine(brut);
            if (!domaine.matches(REGEX_DOMAINE)) {
                throw new IllegalStateException(nomVariable + " contient un domaine invalide.");
            }
            domaines.add(domaine);
        }
        return Set.copyOf(domaines);
    }

    private static String domaineEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalise = email.strip().toLowerCase(Locale.ROOT);
        int arobase = normalise.lastIndexOf('@');
        if (arobase < 0 || arobase == normalise.length() - 1) {
            return null;
        }
        String domaine = normaliserDomaine(normalise.substring(arobase + 1));
        return domaine.matches(REGEX_DOMAINE) ? domaine : null;
    }

    private static String normaliserDomaine(String brut) {
        String domaine = brut.strip().toLowerCase(Locale.ROOT);
        while (domaine.startsWith("@")) {
            domaine = domaine.substring(1);
        }
        while (domaine.endsWith(".")) {
            domaine = domaine.substring(0, domaine.length() - 1);
        }
        return domaine;
    }

    private static boolean correspond(String domaine, Set<String> domaines) {
        return domaines.stream().anyMatch(autorise ->
                domaine.equals(autorise) || domaine.endsWith("." + autorise));
    }
}
