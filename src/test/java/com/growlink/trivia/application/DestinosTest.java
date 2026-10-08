package com.growlink.trivia.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// RabbitMQ rechaza los destinos /topic/... con una barra despues del nombre, y se descubrio
// corriendo las replicas de verdad, no con las pruebas. Esta prueba evita que vuelva a pasar.
class DestinosTest {

    @Test
    void elDestinoDeUnaSalaNoLlevaBarrasDespuesDeTopic() {
        String destino = Destinos.sala("ABC123");

        assertThat(destino).isEqualTo("/topic/salas.ABC123");
        assertThat(destino.substring("/topic/".length())).doesNotContain("/");
    }
}
