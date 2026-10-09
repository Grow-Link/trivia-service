package com.growlink.trivia.application;

// se usa cuando alguien que no jugo en la sala intenta proponer una revancha de ella
public class NoEsParticipanteException extends RuntimeException {
    public NoEsParticipanteException(String codigo) {
        super("No participaste en la sala " + codigo + ", no puedes proponer revancha");
    }
}
