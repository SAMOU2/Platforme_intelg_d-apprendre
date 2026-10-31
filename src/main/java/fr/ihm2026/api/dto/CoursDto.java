package fr.ihm2026.api.dto;

import java.util.List;

public record CoursDto(String titre, String langage, List<BlocDto> blocs) {

    public record BlocDto(String type, String langage, String contenu) {
    }
}
