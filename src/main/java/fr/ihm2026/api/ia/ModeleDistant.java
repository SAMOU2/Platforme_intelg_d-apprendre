package fr.ihm2026.api.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.ihm2026.core.Config;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Modèle réel, appelé via le service REST du serveur (jamais depuis le poste apprenant, BNF-T6).
 *
 * <p>Étape 4 : cette classe est une <b>implémentation</b> de {@link ModeleIa}, pas un point
 * d'accroche. Le fournisseur est enfermé dans un {@link TransportModele} injectable ; passer à un
 * modèle auto-hébergé, ou à un autre fournisseur, ne touche que ce transport.</p>
 *
 * <p>La politique de repli est appliquée ici, à l'endroit où la panne se produit : délai borné,
 * aucune relance automatique, aucun échec ne remonte sous forme d'exception. Une réponse sans
 * citation est traitée comme une indisponibilité : elle ne serait pas affichable, donc ce n'est
 * pas une réponse.</p>
 */
public final class ModeleDistant implements ModeleIa {

    /** Modèle retenu à l'étape 4 (prix et arguments dans {@code strategie-modele.json}). */
    public static final String MODELE_RETENU = "mistral-small-4";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Le prompt chargé est celui de la version courante : le chemin suit {@code VERSION_PROMPT}. */
    private static final String RESSOURCE_PROMPT =
            "/fr/ihm2026/ia/" + ModeleSimule.VERSION_PROMPT + ".md";
    private static final String PROMPT_SYSTEME = chargerPrompt();

    private final String base;
    private final TransportModele transport;
    private final String modele;

    public ModeleDistant() {
        this(Config.API_BASE);
    }

    public ModeleDistant(String base) {
        this(base, new TransportHttp());
    }

    public ModeleDistant(String base, TransportModele transport) {
        this(base, transport, MODELE_RETENU);
    }

    public ModeleDistant(String base, TransportModele transport, String modele) {
        this.base = base == null ? "" : base;
        this.transport = transport;
        this.modele = modele;
    }

    @Override
    public String identifiant() {
        return "modele-distant@" + base + " · " + modele + " · " + ModeleSimule.VERSION_PROMPT;
    }

    public String base() {
        return base;
    }

    @Override
    public ReponseIa repondre(ContexteIa contexte) {
        // 1. Sans clé, on ne tente rien : l'indisponibilité est décidée avant l'appel. Ce cas
        //    n'ouvre pas le disjoncteur — c'est une configuration, pas une panne du fournisseur.
        if (transport instanceof TransportHttp http && !http.clePresente()) {
            return refus();
        }

        // 2. Un seul essai, un délai borné : l'apprenant n'attend jamais indéfiniment.
        TransportModele.Reponse reponse;
        try {
            reponse = transport.poster(url(), requete(contexte), PolitiqueModele.delai());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();      // on ne perd pas l'interruption
            PolitiqueModele.signalerEchecTechnique();
            return refus();
        } catch (Exception e) {
            PolitiqueModele.signalerEchecTechnique();
            return refus();
        }

        if (reponse == null || reponse.code() != 200) {
            PolitiqueModele.signalerEchecTechnique();
            return refus();
        }

        ReponseIa lue = interpreter(reponse.corps());
        if (lue.estRefus()) {
            PolitiqueModele.signalerEchecTechnique();   // réponse illisible ou sans citation
        } else {
            PolitiqueModele.signalerSucces();
        }
        return lue;
    }

    private String url() {
        return base.endsWith("/") ? base + "ia/repondre" : base + "/ia/repondre";
    }

    private static ReponseIa refus() {
        return ReponseIa.refus(MotifRefus.INDISPONIBLE, ModeleSimule.VERSION_PROMPT);
    }

    /** Corps de la requête : le contexte d'aide, et rien de plus (aucune donnée personnelle). */
    String requete(ContexteIa contexte) {
        ObjectNode racine = MAPPER.createObjectNode();
        racine.put("modele", modele);
        racine.put("temperature", 0.2);
        racine.put("max_tokens", 400);
        ArrayNode messages = racine.putArray("messages");
        messages.addObject().put("role", "system").put("content", PROMPT_SYSTEME);
        messages.addObject().put("role", "user").put("content",
                "Langage : " + contexte.langage()
                        + " · Niveau : " + contexte.rangNiveau()
                        + "\nQuestion : " + contexte.question()
                        + (contexte.code() == null || contexte.code().isBlank()
                        ? "" : "\nCode en cours :\n" + contexte.code()));
        return racine.toString();
    }

    /**
     * Lit la réponse du fournisseur — étape 5 : le format est défini une seule fois, dans
     * {@link SortieIa}. Ce qui ne s'y conforme pas est rejeté, jamais rafistolé : une sortie
     * illisible devient une indisponibilité, ce que l'interface sait déjà afficher.
     */
    ReponseIa interpreter(String corps) {
        return SortieIa.lire(corps)
                .map(SortieIa::versReponse)
                .orElseGet(ModeleDistant::refus);
    }

    private static String chargerPrompt() {
        try (InputStream in = ModeleDistant.class.getResourceAsStream(RESSOURCE_PROMPT)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignore) {
            // Prompt illisible : la requête partira sans consigne, et le garde-fou de sortie
            // reste le dernier rempart. L'incident ne doit pas empêcher l'application de démarrer.
        }
        return "Tu es un assistant pédagogique. Tu cites la section du cours. Tu ne notes pas.";
    }
}
