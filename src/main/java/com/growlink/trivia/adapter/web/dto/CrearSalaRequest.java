package com.growlink.trivia.adapter.web.dto;

import com.growlink.trivia.domain.Categoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CrearSalaRequest(
        @NotNull Categoria categoria,
        @NotNull Long hostUsuarioId,
        @NotBlank String hostNombre,
        @NotNull Integer numPreguntas,
        @NotNull Integer duracionSegundos
) {
}
