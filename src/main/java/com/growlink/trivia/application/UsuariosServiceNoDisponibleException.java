package com.growlink.trivia.application;

// El token era valido pero no se pudo averiguar quien es su dueno porque usuarios-service no
// contesto (ver IdentidadDesdeUsuariosService). Sale como 503 y no como 401: el problema es
// nuestro, no del token, y quien llama deberia reintentar con el MISMO token y no pedir otro.
//
// Misma idea que CursosServiceNoDisponibleException, pero esta tiene que estar en
// GlobalExceptionHandler porque afecta a cualquier endpoint que pase por TokenService.
public class UsuariosServiceNoDisponibleException extends RuntimeException {

    public UsuariosServiceNoDisponibleException() {
        super("no se pudo verificar la sesion con usuarios-service, intente de nuevo");
    }
}
