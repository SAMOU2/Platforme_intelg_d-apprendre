package fr.ihm2026.api.dto;

/** Objet reçu du serveur (jamais exposé directement à la vue). */
public record NiveauDto(int rang, String titre, String etat, int seuil, int note,
                        int tentativesRestantes, String langage) {
}
