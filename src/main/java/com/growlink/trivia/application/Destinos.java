package com.growlink.trivia.application;

// Un solo lugar donde se escribe el destino STOMP de cada sala.
// Lleva punto y no barra (salas.ABC123, no salas/ABC123) porque RabbitMQ, que es el broker cuando hay
// varias replicas de trivia, rechaza los destinos /topic/... que traen una barra despues del nombre
// ("not a valid topic destination"). Con el broker en memoria da igual, pero asi funciona en los dos.
public final class Destinos {

    private Destinos() {
    }

    public static String sala(String codigo) {
        return "/topic/salas." + codigo;
    }
}
