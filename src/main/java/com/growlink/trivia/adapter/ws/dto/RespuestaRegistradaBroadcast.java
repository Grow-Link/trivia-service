package com.growlink.trivia.adapter.ws.dto;

// se manda a toda la sala apenas se guarda la respuesta de un jugador, para que
// el frontend le de feedback instantaneo sin esperar a que todos respondan
// (eso lo sigue haciendo el LEADERBOARD). el frontend filtra por usuarioId
// para solo reaccionar a su propio mensaje
public record RespuestaRegistradaBroadcast(String type, String codigo, Long usuarioId, int indice,
                                             boolean correcta, int puntos) {
    public static RespuestaRegistradaBroadcast of(String codigo, Long usuarioId, int indice,
                                                    boolean correcta, int puntos) {
        return new RespuestaRegistradaBroadcast("RESPUESTA_REGISTRADA", codigo, usuarioId, indice, correcta, puntos);
    }
}
