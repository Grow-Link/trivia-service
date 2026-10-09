package com.growlink.trivia.adapter.ws.dto;

public record LeaderboardEntry(Long usuarioId, String nombre, int puntos, int aciertos) {
}
