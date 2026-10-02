package com.growlink.trivia.application;

public class NoAutenticadoException extends RuntimeException {
    public NoAutenticadoException() {
        super("Falta el token o no es valido, inicia sesion de nuevo");
    }
}
