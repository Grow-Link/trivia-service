package com.growlink.trivia.adapter.ws.dto;

// se manda cada vez que alguien sale de la sala, sea porque mando "salir", cerro la pestana o se le
// cayo la red (ver SalaWebSocketController). nuevoHostUsuarioId solo trae algo cuando el que se fue
// era el host y la sala seguia en ESPERA; en cualquier otro caso no se manda (non_null en Jackson)
public record JugadorSalioBroadcast(String type, String codigo, Long usuarioId, String nombre,
                                    Long nuevoHostUsuarioId, String estado) {
    public static JugadorSalioBroadcast of(String codigo, Long usuarioId, String nombre, Long nuevoHostUsuarioId,
                                           String estado) {
        return new JugadorSalioBroadcast("JUGADOR_SALIO", codigo, usuarioId, nombre, nuevoHostUsuarioId, estado);
    }
}
