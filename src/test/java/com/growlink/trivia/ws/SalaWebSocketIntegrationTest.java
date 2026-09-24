package com.growlink.trivia.ws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Crea una sala real por REST, conecta un cliente STOMP de verdad, se une
// y arranca la partida. Nada mockeado, igual que probamos realtime-service.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SalaWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    void crearUnirseEIniciarUnaPartidaDeVerdad() throws Exception {
        String codigo = crearSala();

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectar();
        session.subscribe("/topic/salas/" + codigo, new QueueFrameHandler(mensajes));

        // Beto se une (Ana la host ya quedo adentro desde que se creo la sala)
        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        Map<String, Object> despuesDeUnirse = poll(mensajes);
        assertThat(despuesDeUnirse.get("estado")).isEqualTo("ESPERANDO");
        assertThat((List<?>) despuesDeUnirse.get("participantes")).hasSize(2);

        // con 2 participantes ya se puede iniciar
        session.send("/app/salas/" + codigo + "/iniciar", Map.of());
        Map<String, Object> despuesDeIniciar = poll(mensajes);
        assertThat(despuesDeIniciar.get("estado")).isEqualTo("EN_CURSO");

        // apenas inicia, tambien se manda la primera pregunta
        Map<String, Object> primeraPregunta = poll(mensajes);
        assertThat(primeraPregunta.get("type")).isEqualTo("PREGUNTA");
        assertThat(primeraPregunta.get("indice")).isEqualTo(0);

        // alguien que llega tarde ya no se puede unir
        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 3, "nombre", "Carla"));
        Map<String, Object> error = poll(mensajes);
        assertThat(error.get("type")).isEqualTo("ERROR");
    }

    @Test
    void juegaUnaPartidaCompletaDePrincipioAFin() throws Exception {
        String codigo = crearSala(); // BACKEND, 5 preguntas, 10 segundos

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectar();
        session.subscribe("/topic/salas/" + codigo, new QueueFrameHandler(mensajes));

        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        poll(mensajes); // SALA_UPDATE con los 2 participantes

        session.send("/app/salas/" + codigo + "/iniciar", Map.of());
        poll(mensajes); // SALA_UPDATE en EN_CURSO

        for (int indice = 0; indice < 5; indice++) {
            Map<String, Object> pregunta = poll(mensajes);
            assertThat(pregunta.get("type")).isEqualTo("PREGUNTA");
            assertThat(pregunta.get("indice")).isEqualTo(indice);
            assertThat((List<?>) pregunta.get("opciones")).hasSize(4);
            assertThat(pregunta.get("enviadaEnEpochMs")).isNotNull();

            // las preguntas sembradas siempre tienen la opcion 0 como correcta
            session.send("/app/salas/" + codigo + "/responder", Map.of("usuarioId", 1, "indice", indice, "opcionElegida", 0));
            session.send("/app/salas/" + codigo + "/responder", Map.of("usuarioId", 2, "indice", indice, "opcionElegida", 0));

            Map<String, Object> leaderboard = poll(mensajes);
            assertThat(leaderboard.get("type")).isEqualTo("LEADERBOARD");
            assertThat((List<?>) leaderboard.get("ranking")).hasSize(2);

            if (indice < 4) {
                continue; // la siguiente vuelta del for ya espera la proxima PREGUNTA
            }

            Map<String, Object> resultados = poll(mensajes);
            assertThat(resultados.get("type")).isEqualTo("RESULTADOS_FINALES");
            assertThat(resultados.get("ganadorUsuarioId")).isIn(1, 2);
            assertThat((List<?>) resultados.get("ranking")).hasSize(2);
        }
    }

    @Test
    void noSePuedeIniciarConUnSoloParticipante() throws Exception {
        String codigo = crearSala();

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectar();
        session.subscribe("/topic/salas/" + codigo, new QueueFrameHandler(mensajes));

        session.send("/app/salas/" + codigo + "/iniciar", Map.of());
        Map<String, Object> error = poll(mensajes);
        assertThat(error.get("type")).isEqualTo("ERROR");
    }

    private String crearSala() {
        RestTemplate rest = new RestTemplate();
        Map<String, Object> body = Map.of(
                "categoria", "BACKEND",
                "hostUsuarioId", 1,
                "hostNombre", "Ana",
                "numPreguntas", 5,
                "duracionSegundos", 10);
        @SuppressWarnings("unchecked")
        Map<String, Object> respuesta = rest.postForEntity(
                "http://localhost:" + port + "/api/salas", body, Map.class).getBody();
        return (String) respuesta.get("codigo");
    }

    private StompSession conectar() throws Exception {
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);
    }

    private Map<String, Object> poll(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        Map<String, Object> mensaje = queue.poll(5, TimeUnit.SECONDS);
        assertThat(mensaje).as("no llego ningun mensaje en 5 segundos").isNotNull();
        return mensaje;
    }

    private static class QueueFrameHandler implements StompFrameHandler {
        private final BlockingQueue<Map<String, Object>> queue;

        QueueFrameHandler(BlockingQueue<Map<String, Object>> queue) {
            this.queue = queue;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            queue.add((Map<String, Object>) payload);
        }
    }
}
