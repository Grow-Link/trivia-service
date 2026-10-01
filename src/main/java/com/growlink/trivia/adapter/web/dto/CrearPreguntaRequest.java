package com.growlink.trivia.adapter.web.dto;

import com.growlink.trivia.domain.Categoria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// respuestaCorrecta es la posicion (0 a 3) de la opcion correcta dentro de opciones
public record CrearPreguntaRequest(
        @NotNull Categoria categoria,
        @NotNull Long publicadorUsuarioId,
        @NotBlank @Size(max = 500) String texto,
        @NotNull @Size(min = 4, max = 4, message = "debe tener exactamente 4 opciones")
        List<@NotBlank @Size(max = 255) String> opciones,
        @NotNull @Min(0) @Max(3) Integer respuestaCorrecta
) {
}
