package com.growlink.trivia.domain;

// los eventos que va dejando una partida, de aqui sale el dashboard del admin (HU-24)
public enum TipoEvento {
    SALA_CREADA,
    PARTICIPANTE_UNIDO,
    PREGUNTA_ENVIADA,
    // la primera respuesta de cada pregunta, trae la latencia desde que se envio
    PRIMERA_RESPUESTA,
    // dos aciertos casi al mismo tiempo, el UPDATE atomico dejo pasar solo a uno
    EMPATE_RESUELTO,
    PARTIDA_FINALIZADA
}
