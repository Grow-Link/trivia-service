package com.growlink.trivia.application;

public class FaltanParticipantesException extends RuntimeException {
    public FaltanParticipantesException() {
        super("Se necesitan al menos 2 participantes para iniciar la partida");
    }
}
