package com.growlink.trivia.adapter.web.dto;

import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.PreguntaBanco;

import java.util.List;

public record PreguntaResponse(Long id, Categoria categoria, String texto, List<String> opciones,
                               int respuestaCorrecta) {

    public static PreguntaResponse from(PreguntaBanco pregunta) {
        return new PreguntaResponse(pregunta.getId(), pregunta.getCategoria(), pregunta.getTexto(),
                pregunta.getOpciones(), pregunta.getRespuestaCorrecta());
    }
}
