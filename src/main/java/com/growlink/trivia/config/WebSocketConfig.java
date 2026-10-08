package com.growlink.trivia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// Mismo esquema que ya usamos en realtime-service: el cliente manda a /app/..
// y escucha lo que pasa en la sala en /topic/..
//
// Por defecto el broker vive en la memoria de la instancia. Eso funciona con
// una sola instancia, pero si hay varias (balanceo) un mensaje que sale por una
// no le llega a los jugadores conectados a otra. Para escalar se prende el relay
// (TRIVIA_BROKER_RELAY_ENABLED=true) y todas las instancias comparten un broker
// externo con soporte STOMP, RabbitMQ con el plugin rabbitmq_stomp.
// El estado de la partida ya esta en la base de datos, asi que cualquier
// instancia puede atender cualquier mensaje.
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final boolean relayActivo;
    private final String relayHost;
    private final int relayPort;
    private final String relayUsuario;
    private final String relayClave;

    public WebSocketConfig(@Value("${trivia.broker.relay.enabled:false}") boolean relayActivo,
                           @Value("${trivia.broker.relay.host:localhost}") String relayHost,
                           @Value("${trivia.broker.relay.port:61613}") int relayPort,
                           @Value("${trivia.broker.relay.username:guest}") String relayUsuario,
                           @Value("${trivia.broker.relay.password:guest}") String relayClave) {
        this.relayActivo = relayActivo;
        this.relayHost = relayHost;
        this.relayPort = relayPort;
        this.relayUsuario = relayUsuario;
        this.relayClave = relayClave;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        if (relayActivo) {
            registry.enableStompBrokerRelay("/topic")
                    .setRelayHost(relayHost)
                    .setRelayPort(relayPort)
                    .setClientLogin(relayUsuario)
                    .setClientPasscode(relayClave)
                    .setSystemLogin(relayUsuario)
                    .setSystemPasscode(relayClave);
        } else {
            registry.enableSimpleBroker("/topic");
        }
        registry.setApplicationDestinationPrefixes("/app");
        // Sin esto los mensajes salen por un grupo de hilos que no garantiza el orden: al terminar una
        // ronda se mandan seguidos el ranking y la pregunta siguiente, y a veces el jugador recibia la
        // pregunta ANTES del ranking. Con esto cada jugador recibe los mensajes en el orden en que se publicaron.
        registry.setPreservePublishOrder(true);
    }
}
