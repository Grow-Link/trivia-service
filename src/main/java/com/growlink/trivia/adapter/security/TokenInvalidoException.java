package com.growlink.trivia.adapter.security;

// El token del CONNECT falta, esta vencido, viene firmado con otra llave, o no es para nosotros
// (emisor o audiencia que no cuadran). El motivo va en el mensaje para que quede en el log del
// servidor; al cliente solo se le dice que el CONNECT fue rechazado, sin detalles que le sirvan
// para adivinar que parte de su token hay que arreglar.
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String motivo) {
        super(motivo);
    }

    public TokenInvalidoException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
