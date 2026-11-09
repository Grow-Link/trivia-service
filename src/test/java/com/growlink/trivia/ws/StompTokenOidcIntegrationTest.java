package com.growlink.trivia.ws;

import com.growlink.trivia.LlavesDePrueba;
import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.application.Destinos;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.domain.SalaTrivia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.growlink.trivia.RespuestasDePrueba.correcta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El modo auth.mode=oidc de punta a punta, con un emisor de OIDC de mentiras levantado por la
// prueba (par de llaves RSA generado en el momento + JWKS servido en localhost, ver LlavesDePrueba)
// y clientes de STOMP de verdad, igual que el resto de las pruebas de WebSocket del proyecto.
//
// Nota: a proposito NO se configura auth.oidc.jwks-uri, para que la prueba tambien ejercite la
// ruta por defecto que arma AuthModeConfig, <issuer>/.well-known/jwks.json.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StompTokenOidcIntegrationTest {

    @DynamicPropertySource
    static void modoOidc(DynamicPropertyRegistry registro) {
        registro.add("auth.mode", () -> "oidc");
        // identity-source=claims conserva el comportamiento anterior (el usuarioId sale del sub),
        // que es justo lo que estas pruebas cubren, y evita tener que levantar usuarios-service
        registro.add("auth.identity-source", () -> "claims");
        registro.add("auth.oidc.issuer-uri", LlavesDePrueba::issuer);
        registro.add("auth.oidc.audience", () -> LlavesDePrueba.AUDIENCE);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private SalaService salaService;
    @Autowired
    private SalaPreguntaRepository salaPreguntaRepository;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    void sinTokenNoSeAbreLaConexion() {
        assertThatThrownBy(() -> conectarCon(null))
                .as("el CONNECT sin token tiene que ser rechazado")
                .isNotNull();
    }

    @Test
    void conUnTokenVencidoNoSeAbreLaConexion() {
        assertThatThrownBy(() -> conectarCon(LlavesDePrueba.tokenVencido(2))).isNotNull();
    }

    @Test
    void conUnTokenFirmadoPorOtroNoSeAbreLaConexion() {
        assertThatThrownBy(() -> conectarCon(LlavesDePrueba.tokenFirmadoPorUnImpostor(2))).isNotNull();
    }

    @Test
    void conUnTokenValidoSeAbreLaConexionYSePuedeJugar() throws Exception {
        String codigo = crearSala();

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectarCon(LlavesDePrueba.tokenValido(2));
        assertThat(session.isConnected()).isTrue();
        session.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        // el nombre si viaja en el mensaje (no es una credencial y el token no tiene por que
        // traerlo), pero el usuarioId que decide quien se unio sale del sub del token
        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));

        Map<String, Object> actualizacion = poll(mensajes);
        assertThat(actualizacion.get("estado")).isEqualTo("ESPERANDO");
        assertThat((List<?>) actualizacion.get("participantes")).hasSize(2);
    }

    // El punto importante de toda la HU: la sesion esta autenticada como el usuario 2, pero manda
    // mensajes con el usuarioId de otro jugador en el payload. Ese usuarioId se tiene que ignorar,
    // porque si no cualquiera con la conexion abierta podria responder (o salirse) en nombre de otro.
    @Test
    void noSePuedeResponderANombreDeOtroJugador() throws Exception {
        String codigo = crearSala(); // la crea Ana, el usuario 1, que queda de host

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectarCon(LlavesDePrueba.tokenValido(2)); // la sesion es del usuario 2
        session.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        // se une diciendo que es el 99: tiene que entrar como el 2, el del token
        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 99, "nombre", "Beto"));
        Map<String, Object> actualizacion = poll(mensajes);
        List<?> participantes = (List<?>) actualizacion.get("participantes");
        assertThat(participantes).hasSize(2);
        assertThat(idsDe(participantes)).containsExactlyInAnyOrder(1, 2).doesNotContain(99);

        session.send("/app/salas/" + codigo + "/iniciar", Map.of());
        assertThat(poll(mensajes).get("estado")).isEqualTo("EN_CURSO");
        Map<String, Object> pregunta = poll(mensajes);
        assertThat(pregunta.get("type")).isEqualTo("PREGUNTA");

        // responde diciendo que es Ana, el usuario 1 y host de la sala
        SalaTrivia sala = salaService.obtenerPorCodigo(codigo);
        session.send("/app/salas/" + codigo + "/responder",
                Map.of("usuarioId", 1, "indice", 0, "opcionElegida", correcta(salaPreguntaRepository, sala, 0)));

        Map<String, Object> registrada = poll(mensajes);
        assertThat(registrada.get("type")).isEqualTo("RESPUESTA_REGISTRADA");
        // la respuesta quedo a nombre del 2 (el del token), no del 1 que iba en el mensaje
        assertThat(registrada.get("usuarioId")).isEqualTo(2);

        // y Ana sigue sin responder: no llega ningun LEADERBOARD, que es lo que se manda cuando ya
        // respondieron todos. Si el usuarioId del mensaje hubiera valido, aqui ya estaria
        assertThat(mensajes.poll(1, TimeUnit.SECONDS))
                .as("nadie respondio por Ana, la ronda no puede estar cerrada")
                .isNull();
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

    // el token va en el frame CONNECT de STOMP y no en el handshake del WebSocket porque el
    // navegador no puede ponerle headers a un WebSocket; asi lo manda cualquier cliente de stomp.js
    private StompSession conectarCon(String token) throws Exception {
        StompHeaders headersDelConnect = new StompHeaders();
        if (token != null) {
            headersDelConnect.add("Authorization", "Bearer " + token);
        }
        headersDelConnect.add("X-Request-Id", "traza-de-la-prueba");
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                headersDelConnect, new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    private static List<Integer> idsDe(List<?> participantes) {
        return participantes.stream()
                .map(p -> (Integer) ((Map<?, ?>) p).get("usuarioId"))
                .toList();
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
