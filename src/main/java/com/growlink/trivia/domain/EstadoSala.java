package com.growlink.trivia.domain;

public enum EstadoSala {
    ESPERANDO,
    EN_CURSO,
    FINALIZADA,
    // la sala quedo vacia en ESPERA porque todos salieron antes de que empezara la partida
    CANCELADA
}
