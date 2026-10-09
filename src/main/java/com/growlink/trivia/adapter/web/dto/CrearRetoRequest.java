package com.growlink.trivia.adapter.web.dto;

import com.growlink.trivia.domain.Categoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// quien reta sale del token, no del cuerpo: nadie puede retar "a nombre de" otra persona
public record CrearRetoRequest(
        @NotNull Long retadoUsuarioId,
        @NotBlank String retadoNombre,
        @NotBlank String retadorNombre,
        @NotNull Categoria categoria,
        @NotNull Integer numPreguntas,
        @NotNull Integer duracionSegundos,
        @Size(max = 140) String mensaje
) {
}
