package com.growlink.trivia.domain;

// EXPIRADO no se guarda: se calcula al leer, cuando un reto PENDIENTE ya paso de su hora limite
public enum EstadoReto {
    PENDIENTE,
    ACEPTADO,
    RECHAZADO,
    EXPIRADO
}
