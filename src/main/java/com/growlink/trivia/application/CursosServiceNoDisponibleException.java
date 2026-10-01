package com.growlink.trivia.application;

public class CursosServiceNoDisponibleException extends RuntimeException {
    public CursosServiceNoDisponibleException(Throwable causa) {
        super("No se pudo consultar cursos-service, intenta de nuevo en un momento", causa);
    }
}
