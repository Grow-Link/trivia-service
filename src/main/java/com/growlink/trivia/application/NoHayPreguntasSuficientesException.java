package com.growlink.trivia.application;

public class NoHayPreguntasSuficientesException extends RuntimeException {
    public NoHayPreguntasSuficientesException(String categoria, int necesarias, int disponibles) {
        super("La categoria " + categoria + " necesita " + necesarias + " preguntas pero solo hay " + disponibles);
    }
}
