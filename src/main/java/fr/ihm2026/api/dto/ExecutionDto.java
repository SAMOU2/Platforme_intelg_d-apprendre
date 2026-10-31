package fr.ihm2026.api.dto;

/** Réponse du service d'exécution isolé (jamais exécuté dans le client, BNF-03). */
public record ExecutionDto(boolean ok, String sortie, String erreur, int ligneErreur) {
}
