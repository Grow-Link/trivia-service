package com.growlink.trivia.ws;

import com.growlink.trivia.application.Destinos;
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

// HU bono "Revancha": mismo estilo de prueba real de extremo a extremo que
// SalaWebSocketIntegrationTest (cliente STOMP de verdad, nada mockeado)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RevanchaWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    void alTerminarLaPartidaCualquieraProponeRevanchaYSoloSeCreaUnaSalaNueva() throws Exception {
        String codigo = crearSala(); // INGENIERIA_SISTEMAS, 5 preguntas, 10 segundos, Ana es la host (1L)

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectar();
        session.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        poll(mensajes); // SALA_UPDATE con los 2 participantes

        session.send("/app/salas/" + codigo + "/iniciar", Map.of());
        poll(mensajes); // SALA_UPDATE en EN_CURSO

        for (int indice = 0; indice < 5; indice++) {
            poll(mensajes); // PREGUNTA

            // no importa si la opcion es correcta o no para que la partida avance,
            // solo que todos respondan (el banco de 200 preguntas ya no siempre tiene
            // la correcta en la opcion 0)
            session.send("/app/salas/" + codigo + "/responder", Map.of("usuarioId", 1, "indice", indice, "opcionElegida", 0));
            session.send("/app/salas/" + codigo + "/responder", Map.of("usuarioId", 2, "indice", indice, "opcionElegida", 0));

            poll(mensajes); // RESPUESTA_REGISTRADA de Ana
            poll(mensajes); // RESPUESTA_REGISTRADA de Beto
            poll(mensajes); // LEADERBOARD
        }
        Map<String, Object> resultados = poll(mensajes);
        assertThat(resultados.get("type")).isEqualTo("RESULTADOS_FINALES");

        // Ana propone revancha
        session.send("/app/salas/" + codigo + "/revancha", Map.of("usuarioId", 1, "nombre", "Ana"));
        Map<String, Object> revancha = poll(mensajes);
        assertThat(revancha.get("type")).isEqualTo("REVANCHA");
        assertThat(revancha.get("codigo")).isEqualTo(codigo);
        assertThat(revancha.get("hostUsuarioId")).isEqualTo(1);
        assertThat(revancha.get("hostNombre")).isEqualTo("Ana");
        String nuevoCodigo = (String) revancha.get("nuevoCodigo");
        assertThat(nuevoCodigo).isNotNull().isNotEqualTo(codigo);

        // Beto tambien le da al boton: no se crea otra sala, llega la misma invitacion
        session.send("/app/salas/" + codigo + "/revancha", Map.of("usuarioId", 2, "nombre", "Beto"));
        Map<String, Object> revanchaOtraVez = poll(mensajes);
        assertThat(revanchaOtraVez.get("type")).isEqualTo("REVANCHA");
        assertThat(revanchaOtraVez.get("nuevoCodigo")).isEqualTo(nuevoCodigo);
        // el host de la sala de revancha sigue siendo Ana, porque ya existia cuando Beto la pidio
        assertThat(revanchaOtraVez.get("hostUsuarioId")).isEqualTo(1);
        assertThat(revanchaOtraVez.get("hostNombre")).isEqualTo("Ana");

        // quien llega tarde (o se reconecto) tambien encuentra la invitacion por REST
        RestTemplate rest = new RestTemplate();
        @SuppressWarnings("unchecked")
        Map<String, Object> salaViejaActualizada = rest.getForEntity(
                "http://localhost:" + port + "/api/salas/" + codigo, Map.class).getBody();
        assertThat(salaViejaActualizada.get("revanchaCodigo")).isEqualTo(nuevoCodigo);

        // Beto se une a la sala nueva con el "unirse" de siempre, porque nace en ESPERANDO
        BlockingQueue<Map<String, Object>> mensajesSalaNueva = new LinkedBlockingQueue<>();
        StompSession sessionBeto = conectar();
        sessionBeto.subscribe(Destinos.sala(nuevoCodigo), new QueueFrameHandler(mensajesSalaNueva));
        sessionBeto.send("/app/salas/" + nuevoCodigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        Map<String, Object> salaNuevaUpdate = poll(mensajesSalaNueva);
        assertThat(salaNuevaUpdate.get("estado")).isEqualTo("ESPERANDO");
        assertThat((List<?>) salaNuevaUpdate.get("participantes")).hasSize(2);
    }

    @Test
    void noSePuedeProponerRevanchaDeUnaSalaQueNoHaFinalizado() throws Exception {
        String codigo = crearSala();

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectar();
        session.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        poll(mensajes); // SALA_UPDATE

        // la sala sigue en ESPERANDO, nunca se inicio
        session.send("/app/salas/" + codigo + "/revancha", Map.of("usuarioId", 1, "nombre", "Ana"));
        Map<String, Object> error = poll(mensajes);
        assertThat(error.get("type")).isEqualTo("ERROR");
    }

    private String crearSala() {
        RestTemplate rest = new RestTemplate();
        Map<String, Object> body = Map.of(
                "categoria", "INGENIERIA_SISTEMAS",
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
