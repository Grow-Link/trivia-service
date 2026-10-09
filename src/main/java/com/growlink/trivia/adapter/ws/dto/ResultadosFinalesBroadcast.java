package com.growlink.trivia.adapter.ws.dto;

import java.util.List;

// ganadorUsuarioId es el primero del ranking (null si nadie sumo puntos). Si hubo empate en el puntaje mas alto,
// ganadoresUsuarioIds trae a todos los que empataron y empate va en true.
public record ResultadosFinalesBroadcast(String type, String codigo, List<LeaderboardEntry> ranking,
                                         Long ganadorUsuarioId, List<Long> ganadoresUsuarioIds, boolean empate,
                                         int numPreguntas) {
    public static ResultadosFinalesBroadcast of(String codigo, List<LeaderboardEntry> ranking, Long ganadorUsuarioId,
                                                List<Long> ganadoresUsuarioIds, int numPreguntas) {
        return new ResultadosFinalesBroadcast("RESULTADOS_FINALES", codigo, ranking, ganadorUsuarioId,
                ganadoresUsuarioIds, ganadoresUsuarioIds.size() > 1, numPreguntas);
    }
}
