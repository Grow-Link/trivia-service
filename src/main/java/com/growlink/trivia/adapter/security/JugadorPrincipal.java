package com.growlink.trivia.adapter.security;

import java.security.Principal;

// La identidad de la sesion de WebSocket cuando auth.mode=oidc. Se arma con el sub del token en
// el CONNECT (ver StompAuthChannelInterceptor) y Spring la deja pegada a la sesion de STOMP, asi
// que todos los mensajes que lleguen despues por esa misma conexion ya vienen identificados sin
// que el cliente tenga que repetir el token ni mandar su usuarioId.
//
// getName() devuelve el usuarioId en texto porque esa es la firma de Principal; usuarioId() es
// la forma tipada que usan los handlers.
public record JugadorPrincipal(Long usuarioId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(usuarioId);
    }
}
