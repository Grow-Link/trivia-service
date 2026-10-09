package com.growlink.trivia.adapter.web.dto;

import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.SalaTrivia;

public record SalaResponse(Long id, String codigo, Categoria categoria, String estado,
                            int numPreguntas, int duracionSegundos, String revanchaCodigo) {
    public static SalaResponse from(SalaTrivia s) {
        return new SalaResponse(s.getId(), s.getCodigo(), s.getCategoria(), s.getEstado().name(),
                s.getNumPreguntas(), s.getDuracionSegundos(), s.getRevanchaCodigo());
    }
}
