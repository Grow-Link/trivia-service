package com.growlink.trivia.adapter.ws.dto;

import java.util.List;

// nunca se manda la respuestaCorrecta aqui, eso se queda en el servidor
public record PreguntaBroadcast(String type, String codigo, int indice, int totalPreguntas,
                                 String texto, List<String> opciones, int duracionSegundos, long enviadaEnEpochMs) {
    public static PreguntaBroadcast of(String codigo, int indice, int totalPreguntas, String texto,
                                        List<String> opciones, int duracionSegundos, long enviadaEnEpochMs) {
        return new PreguntaBroadcast("PREGUNTA", codigo, indice, totalPreguntas, texto, opciones,
                duracionSegundos, enviadaEnEpochMs);
    }
}
