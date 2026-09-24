package com.growlink.trivia.adapter.ws.dto;

import java.util.List;

public record ResultadosFinalesBroadcast(String type, String codigo, List<LeaderboardEntry> ranking, Long ganadorUsuarioId) {
    public static ResultadosFinalesBroadcast of(String codigo, List<LeaderboardEntry> ranking, Long ganadorUsuarioId) {
        return new ResultadosFinalesBroadcast("RESULTADOS_FINALES", codigo, ranking, ganadorUsuarioId);
    }
}
