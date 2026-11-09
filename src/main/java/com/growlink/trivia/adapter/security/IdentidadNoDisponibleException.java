package com.growlink.trivia.adapter.security;

// El token esta bien, pero no se pudo averiguar quien es su dueno: usuarios-service no contesto,
// se paso del timeout, o devolvio un 5xx.
//
// Es a proposito un caso DISTINTO de TokenInvalidoException, porque la respuesta correcta es
// distinta: un token invalido es culpa de quien llama (401, y no insista con ese token), mientras
// que esto es un problema nuestro (503, vuelva a intentar). Confundirlos manda al frontend a
// pedir un login nuevo por una caida de un servicio de atras, que no arregla nada.
//
// Lo que NUNCA se hace al llegar aqui es caer al modo claims: eso convertiria una caida de
// usuarios-service en "aceptamos el token y le creemos al sub", justo lo que esta HU viene a
// quitar. Si hay un valor vigente en cache si se usa, eso pasa antes de llegar aqui.
public class IdentidadNoDisponibleException extends RuntimeException {

    public IdentidadNoDisponibleException(String motivo) {
        super(motivo);
    }

    public IdentidadNoDisponibleException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
