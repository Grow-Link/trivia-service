package com.growlink.trivia.application;

public class RetoNoEncontradoException extends RuntimeException {
    public RetoNoEncontradoException(Long id) {
        super("No existe el reto " + id);
    }
}
