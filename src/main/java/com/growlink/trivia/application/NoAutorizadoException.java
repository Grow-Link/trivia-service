package com.growlink.trivia.application;

public class NoAutorizadoException extends RuntimeException {
    public NoAutorizadoException() {
        super("Esto solo lo puede hacer un administrador");
    }
}
