package com.growlink.trivia.adapter.web.dto;

import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.Reto;

import java.time.Instant;

public record RetoResponse(Long id, Long retadorUsuarioId, String retadorNombre, Long retadoUsuarioId,
                           String retadoNombre, Categoria categoria, int numPreguntas, int duracionSegundos,
                           String mensaje, String codigoSala, String estado, Instant creadoEn, Instant expiraEn) {
    public static RetoResponse from(Reto r) {
        return new RetoResponse(r.getId(), r.getRetadorUsuarioId(), r.getRetadorNombre(), r.getRetadoUsuarioId(),
                r.getRetadoNombre(), r.getCategoria(), r.getNumPreguntas(), r.getDuracionSegundos(), r.getMensaje(),
                r.getCodigoSala(), r.estadoEfectivo(Instant.now()).name(), r.getCreadoEn(), r.getExpiraEn());
    }
}
