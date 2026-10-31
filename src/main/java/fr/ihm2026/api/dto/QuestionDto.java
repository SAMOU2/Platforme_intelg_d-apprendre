package fr.ihm2026.api.dto;

import java.util.List;

public record QuestionDto(String enonce, List<String> propositions, int bonneReponse) {
}
