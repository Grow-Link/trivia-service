package com.growlink.trivia.application;

// se usa cuando alguien responde a una sala que no esta en curso, o a una
// pregunta que ya no es la actual
public class JuegoNoDisponibleException extends RuntimeException {
    public JuegoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
