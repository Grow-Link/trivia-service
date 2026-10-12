package com.growlink.trivia.adapter.ws.dto;

import java.util.List;

// ganadorUsuarioId es el primero del ranking (null si nadie sumo puntos). Si hubo empate en el puntaje mas alto,
// ganadoresUsuarioIds trae a todos los que empataron y empate va en true.
// abandono va en true cuando la partida se corto porque quedo con menos de 2 jugadores activos (HU
// salir-de-sala): en ese caso nunca hay ganador, aunque el ranking muestre puntos distintos entre los dos.
public record ResultadosFinalesBroadcast(String type, String codigo, List<LeaderboardEntry> ranking,
                                         Long ganadorUsuarioId, List<Long> ganadoresUsuarioIds, boolean empate,
                                         int numPreguntas, boolean abandono) {
    public static ResultadosFinalesBroadcast of(String codigo, List<LeaderboardEntry> ranking, Long ganadorUsuarioId,
                                                List<Long> ganadoresUsuarioIds, int numPreguntas) {
        return new ResultadosFinalesBroadcast("RESULTADOS_FINALES", codigo, ranking, ganadorUsuarioId,
                ganadoresUsuarioIds, ganadoresUsuarioIds.size() > 1, numPreguntas, false);
    }

    public static ResultadosFinalesBroadcast porAbandono(String codigo, List<LeaderboardEntry> ranking, int numPreguntas) {
        return new ResultadosFinalesBroadcast("RESULTADOS_FINALES", codigo, ranking, null, List.of(), false,
                numPreguntas, true);
    }
}
