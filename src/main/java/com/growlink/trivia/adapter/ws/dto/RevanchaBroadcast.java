package com.growlink.trivia.adapter.ws.dto;

// se manda a la sala VIEJA cuando alguien propone revancha, asi todos los que
// estaban ahi (incluido quien se conecte tarde a /api/salas/{codigo}, por el
// revanchaCodigo que trae SalaResponse) ven la invitacion a la sala nueva
public record RevanchaBroadcast(String type, String codigo, String nuevoCodigo,
                                 Long hostUsuarioId, String hostNombre) {
    public static RevanchaBroadcast of(String codigo, String nuevoCodigo, Long hostUsuarioId, String hostNombre) {
        return new RevanchaBroadcast("REVANCHA", codigo, nuevoCodigo, hostUsuarioId, hostNombre);
    }
}
