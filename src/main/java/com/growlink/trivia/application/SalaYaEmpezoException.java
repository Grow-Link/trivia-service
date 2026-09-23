package com.growlink.trivia.application;

// Se usa cuando alguien intenta unirse a una sala que ya paso de ESPERANDO
public class SalaYaEmpezoException extends RuntimeException {
    public SalaYaEmpezoException(String codigo) {
        super("La sala " + codigo + " ya empezo, no te puedes unir");
    }
}
