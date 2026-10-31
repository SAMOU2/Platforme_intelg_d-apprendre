package fr.ihm2026.api.dto;

public record UtilisateurDto(String nom, String email, String role, boolean actif,
                             int note, int tentatives) {
}
