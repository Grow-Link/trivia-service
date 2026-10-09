package com.growlink.trivia.application;

// se usa cuando se propone revancha de una sala que todavia no ha finalizado
public class RevanchaNoDisponibleException extends RuntimeException {
    public RevanchaNoDisponibleException(String codigo) {
        super("La sala " + codigo + " todavia no ha finalizado, no se puede proponer revancha");
    }
}
