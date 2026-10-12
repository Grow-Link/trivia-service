package com.growlink.trivia.ws;

import com.growlink.trivia.application.Destinos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
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

// HU salir-de-sala, de punta a punta por WebSocket (mismo estilo que SalaWebSocketIntegrationTest y
// RevanchaWebSocketIntegrationTest). application-test.yml deja trivia.salida.gracia-ms mas corto que
// en produccion para que las pruebas de desconexion no tengan que esperar los 8s reales, pero
// pollNegativo() siempre espera mas que ese margen (nunca un numero fijo aparte): reconectar un
// WebSocket de verdad (handshake nuevo + STOMP CONNECT/CONNECTED) puede tardar mas de lo que tarda en
// una maquina local, sobre todo en un runner de CI compartido, y un margen fijo mas corto que eso hizo
// que esta prueba fallara una vez en CI aunque la reconexion si habia llegado a tiempo.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SalirDeSalaWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Value("${trivia.salida.gracia-ms}")
    private long graciaSalidaMs;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    void mensajeSalirEnEspera_difundeJugadorSalioYLuegoSalaUpdate() throws Exception {
        String codigo = crearSala(); // Ana (1L) es la host

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectar();
        session.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        poll(mensajes); // SALA_UPDATE con 2 participantes

        session.send("/app/salas/" + codigo + "/salir", Map.of("usuarioId", 2));

        Map<String, Object> jugadorSalio = poll(mensajes);
        assertThat(jugadorSalio.get("type")).isEqualTo("JUGADOR_SALIO");
        assertThat(jugadorSalio.get("codigo")).isEqualTo(codigo);
        assertThat(jugadorSalio.get("usuarioId")).isEqualTo(2);
        assertThat(jugadorSalio.get("nombre")).isEqualTo("Beto");
        assertThat(jugadorSalio.get("nuevoHostUsuarioId")).isNull(); // Beto no era el host
        assertThat(jugadorSalio.get("estado")).isEqualTo("ESPERANDO");

        Map<String, Object> salaUpdate = poll(mensajes);
        assertThat(salaUpdate.get("type")).isEqualTo("SALA_UPDATE");
        assertThat(salaUpdate.get("estado")).isEqualTo("ESPERANDO");
        assertThat((List<?>) salaUpdate.get("participantes")).hasSize(1);
    }

    @Test
    void mensajeSalirDelHost_difundeConElNuevoHost() throws Exception {
        String codigo = crearSala();

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectar();
        session.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        poll(mensajes); // SALA_UPDATE

        session.send("/app/salas/" + codigo + "/salir", Map.of("usuarioId", 1)); // Ana, la host, se va

        Map<String, Object> jugadorSalio = poll(mensajes);
        assertThat(jugadorSalio.get("usuarioId")).isEqualTo(1);
        assertThat(jugadorSalio.get("nuevoHostUsuarioId")).isEqualTo(2); // le toca a Beto

        Map<String, Object> salaUpdate = poll(mensajes);
        assertThat(salaUpdate.get("estado")).isEqualTo("ESPERANDO");
        assertThat((List<?>) salaUpdate.get("participantes")).hasSize(1);
    }

    @Test
    void salirEnJuego_siQuedaUnSoloActivo_terminaConAbandonoYSinGanador() throws Exception {
        String codigo = crearSala();

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectar();
        session.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        poll(mensajes); // SALA_UPDATE

        session.send("/app/salas/" + codigo + "/iniciar", Map.of());
        poll(mensajes); // SALA_UPDATE en EN_CURSO
        poll(mensajes); // PREGUNTA

        session.send("/app/salas/" + codigo + "/salir", Map.of("usuarioId", 2)); // Beto se va a mitad de la partida

        Map<String, Object> jugadorSalio = poll(mensajes);
        assertThat(jugadorSalio.get("type")).isEqualTo("JUGADOR_SALIO");
        assertThat(jugadorSalio.get("estado")).isEqualTo("EN_CURSO");
        assertThat(jugadorSalio.get("nuevoHostUsuarioId")).isNull();

        Map<String, Object> resultados = poll(mensajes);
        assertThat(resultados.get("type")).isEqualTo("RESULTADOS_FINALES");
        assertThat(resultados.get("abandono")).isEqualTo(true);
        assertThat(resultados.get("ganadorUsuarioId")).isNull();
        assertThat((List<?>) resultados.get("ganadoresUsuarioIds")).isEmpty();
    }

    @Test
    void mandarSalirDosVeces_laSegundaNoDifundeNada() throws Exception {
        String codigo = crearSala();

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectar();
        session.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        poll(mensajes); // SALA_UPDATE

        session.send("/app/salas/" + codigo + "/salir", Map.of("usuarioId", 2));
        poll(mensajes); // JUGADOR_SALIO
        poll(mensajes); // SALA_UPDATE

        session.send("/app/salas/" + codigo + "/salir", Map.of("usuarioId", 2)); // segunda vez, ya no estaba

        assertThat(pollNegativo(mensajes)).isNull(); // ni JUGADOR_SALIO ni ERROR ni nada
    }

    @Test
    void desconexionConGraciaYVueltaAUnirse_noCuentaComoSalida() throws Exception {
        String codigo = crearSala();

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession sesionAna = conectar();
        sesionAna.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        StompSession sesionBeto = conectar();
        sesionBeto.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        poll(mensajes); // SALA_UPDATE con 2 participantes

        sesionBeto.disconnect(); // cerro la pestana (o se le cayo la red)

        // vuelve enseguida, mucho antes de que se cumpla el margen de gracia de la prueba
        StompSession sesionBetoNueva = conectar();
        sesionBetoNueva.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        Map<String, Object> salaUpdateReconexion = poll(mensajes);
        assertThat(salaUpdateReconexion.get("type")).isEqualTo("SALA_UPDATE");
        assertThat((List<?>) salaUpdateReconexion.get("participantes")).hasSize(2);

        // se espera mas que el margen de gracia: si la salida se hubiera disparado igual,
        // aqui llegaria un JUGADOR_SALIO que ya no debe aparecer
        assertThat(pollNegativo(mensajes)).isNull();

        RestTemplate rest = new RestTemplate();
        @SuppressWarnings("unchecked")
        Map<String, Object> sala = rest.getForEntity("http://localhost:" + port + "/api/salas/" + codigo, Map.class).getBody();
        assertThat(sala.get("estado")).isEqualTo("ESPERANDO");
    }

    @Test
    void desconexionSinVolverAUnirse_siCuentaComoSalida() throws Exception {
        String codigo = crearSala();

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession sesionAna = conectar();
        sesionAna.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        StompSession sesionBeto = conectar();
        sesionBeto.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        poll(mensajes); // SALA_UPDATE con 2 participantes

        sesionBeto.disconnect();

        // esta vez nadie vuelve a unirse: pasado el margen de gracia la desconexion cuenta como salida
        Map<String, Object> jugadorSalio = pollConTimeout(mensajes, 5);
        assertThat(jugadorSalio.get("type")).isEqualTo("JUGADOR_SALIO");
        assertThat(jugadorSalio.get("usuarioId")).isEqualTo(2);

        Map<String, Object> salaUpdate = poll(mensajes);
        assertThat(salaUpdate.get("type")).isEqualTo("SALA_UPDATE");
        assertThat((List<?>) salaUpdate.get("participantes")).hasSize(1);
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
        return pollConTimeout(queue, 5);
    }

    private Map<String, Object> pollConTimeout(BlockingQueue<Map<String, Object>> queue, int segundos) throws InterruptedException {
        Map<String, Object> mensaje = queue.poll(segundos, TimeUnit.SECONDS);
        assertThat(mensaje).as("no llego ningun mensaje en " + segundos + " segundos").isNotNull();
        return mensaje;
    }

    // espera bastante mas que el margen de gracia configurado (nunca un numero fijo aparte, ver el
    // comentario de la clase) y confirma que no llego nada
    private Map<String, Object> pollNegativo(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        return queue.poll(graciaSalidaMs + 2000, TimeUnit.MILLISECONDS);
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
