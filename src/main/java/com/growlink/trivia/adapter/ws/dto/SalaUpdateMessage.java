package com.growlink.trivia.adapter.ws.dto;

import java.util.List;

// Esto es lo que le llega a todos los que estan viendo la sala de espera
// cada vez que alguien se une o el host le da a iniciar
public record SalaUpdateMessage(String type, String codigo, String estado, List<ParticipanteView> participantes) {
    public static SalaUpdateMessage of(String codigo, String estado, List<ParticipanteView> participantes) {
        return new SalaUpdateMessage("SALA_UPDATE", codigo, estado, participantes);
    }
}
