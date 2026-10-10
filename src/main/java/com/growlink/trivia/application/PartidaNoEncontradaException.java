package com.growlink.trivia.application;

// se usa cuando se pide el detalle de una partida que nunca termino (o nunca existio),
// asi no se confunde con que la sala en si no existe (SalaNoEncontradaException)
public class PartidaNoEncontradaException extends RuntimeException {
    public PartidaNoEncontradaException(String codigo) {
        super("No hay ninguna partida finalizada con el codigo " + codigo);
    }
}
