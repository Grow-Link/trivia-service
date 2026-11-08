package com.growlink.trivia.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

// Cuantos WebSocket tiene abiertos ESTA replica ahora mismo. Es un gauge y no un contador porque
// lo que interesa es el valor del momento (sube y baja), y con la etiqueta comun "instancia" se
// puede ver si el balanceador esta repartiendo parejo o si una replica se esta quedando con todo.
//
// Se cuenta sobre SessionConnectedEvent (el CONNECT de STOMP ya aceptado, despues de que el
// interceptor de autenticacion pudo rechazarlo) y no sobre SessionConnectEvent, para que una
// conexion rechazada por token invalido no quede sumada.
@Component
public class WebSocketMetrics {

    private final AtomicInteger activas = new AtomicInteger();

    public WebSocketMetrics(MeterRegistry registry) {
        Gauge.builder("trivia.websocket.conexiones.activas", activas, AtomicInteger::get)
                .description("conexiones de WebSocket abiertas en esta instancia")
                .register(registry);
    }

    @EventListener
    public void alConectar(SessionConnectedEvent event) {
        activas.incrementAndGet();
    }

    // DECREMENTA aunque la sesion nunca se hubiera unido a una sala: este evento lo emite Spring
    // para toda sesion de STOMP que se cierra, sea por un DISCONNECT ordenado o porque se cayo
    @EventListener
    public void alDesconectar(SessionDisconnectEvent event) {
        activas.updateAndGet(actual -> Math.max(0, actual - 1));
    }

    public int activas() {
        return activas.get();
    }
}
