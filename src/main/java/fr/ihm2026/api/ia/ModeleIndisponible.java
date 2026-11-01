package fr.ihm2026.api.ia;

/** État sûr tant que le fournisseur distant n'est pas intégré et configuré. */
public final class ModeleIndisponible implements ModeleIa {
    @Override public String identifiant() {
        return "ia-desactivee@integration-nvidia-en-attente";
    }
    @Override public ReponseIa repondre(ContexteIa contexte) {
        return ReponseIa.refus(MotifRefus.INDISPONIBLE, ModeleSimule.VERSION_PROMPT);
    }
}
