package com.growlink.trivia.ws;

import com.growlink.trivia.LlavesDePrueba;
import com.growlink.trivia.UsuariosServiceFalso;
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

import static com.growlink.trivia.LlavesDePrueba.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El WebSocket con auth.identity-source=usuarios-service: la identidad se resuelve UNA vez, en el
// CONNECT, y queda en el Principal de la sesion. Los handlers siguen ignorando cualquier
// usuarioId que venga en el mensaje, solo que ahora el numero no sale del sub (que es opaco) sino
// de lo que contesto usuarios-service.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StompIdentidadUsuariosServiceTest {

    private static final String CLIENT_ID = "4c1a9f70-1111-2222-3333-abcdefabcdef";

    private static final UsuariosServiceFalso USUARIOS = new UsuariosServiceFalso();

    @DynamicPropertySource
    static void modoOidcConUsuariosService(DynamicPropertyRegistry registro) {
        registro.add("auth.mode", () -> "oidc");
        registro.add("auth.multi-tenant", () -> "true");
        registro.add("auth.identity-source", () -> "usuarios-service");
        registro.add("auth.usuarios-service-url", USUARIOS::baseUrl);
        registro.add("auth.oidc.audience", () -> "api://" + CLIENT_ID);
        registro.add("auth.oidc.jwks-uri", LlavesDePrueba::jwksUri);
    }

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        USUARIOS.responde(42L, "USUARIO").demora(0);
        USUARIOS.reiniciarContador();
    }

    // El punto de toda la HU en el WebSocket: la sesion se autentica con un token cuyo sub es un
    // texto opaco de Entra, usuarios-service dice que es el 42, y el jugador entra a la sala como
    // el 42, no como lo que diga el mensaje
    @Test
    void laIdentidadDelJugadorSaleDeUsuariosServiceYNoDelSubNiDelMensaje() throws Exception {
        USUARIOS.responde(42L, "USUARIO");
        String codigo = crearSala(); // la crea Ana, el usuario 1, que queda de host

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectarCon(tokenConSub("AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy782bbtaQ"));
        session.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        // se une diciendo que es el 99: tiene que entrar como el 42, el que dijo usuarios-service
        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 99, "nombre", "Beto"));

        Map<String, Object> actualizacion = poll(mensajes);
        List<?> participantes = (List<?>) actualizacion.get("participantes");
        assertThat(idsDe(participantes)).containsExactlyInAnyOrder(1, 42).doesNotContain(99);
    }

    // se consulta solo al conectar: el CONNECT pregunta una vez y los SEND siguientes no vuelven
    @Test
    void soloSeConsultaAlConectarYNoEnCadaMensaje() throws Exception {
        USUARIOS.responde(42L, "USUARIO");
        String codigo = crearSala();

        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession session = conectarCon(tokenConSub("sub-opaco-de-beto"));
        session.subscribe(Destinos.sala(codigo), new QueueFrameHandler(mensajes));

        session.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 42, "nombre", "Beto"));
        poll(mensajes);
        session.send("/app/salas/" + codigo + "/iniciar", Map.of());
        poll(mensajes);

        assertThat(USUARIOS.llamadas()).isEqualTo(1);
    }

    // el X-Request-Id del CONNECT llega a usuarios-service al resolver la identidad (ver
    // RequestIdStompInterceptor y ReenvioDeRequestId): la conexion se sigue de un servicio al otro
    @Test
    void elIdDelConnectLlegaAUsuariosServiceAlResolverLaIdentidad() throws Exception {
        USUARIOS.responde(42L, "USUARIO");
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + tokenConSub("sub-con-traza"));
        headers.add("X-Request-Id", "connect-de-prueba-0001");

        stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);

        assertThat(USUARIOS.ultimoRequestIdDeMe()).isEqualTo("connect-de-prueba-0001");
    }

    // sin id en el CONNECT, se le manda uno nuevo que usuarios-service acepta tal cual
    @Test
    void sinIdEnElConnectSeLeMandaUnoNuevoValido() throws Exception {
        USUARIOS.responde(42L, "USUARIO");

        conectarCon(tokenConSub("sub-sin-traza"));

        assertThat(USUARIOS.ultimoRequestIdDeMe()).matches("^[A-Za-z0-9._-]{8,64}$");
    }

    @Test
    void siUsuariosServiceNoReconoceElTokenNoSeAbreLaConexion() {
        USUARIOS.respondeConEstado(401);

        assertThatThrownBy(() -> conectarCon(tokenConSub("sub-desconocido"))).isNotNull();
    }

    // Sin identidad no se puede saber quien juega, asi que la conexion se rechaza. Lo que NO pasa
    // es que se acepte creyendole al sub del token
    @Test
    void siUsuariosServiceFallaNoSeAbreLaConexion() {
        USUARIOS.respondeConEstado(500);

        assertThatThrownBy(() -> conectarCon(tokenConSub("sub-con-500"))).isNotNull();
    }

    @Test
    void siUsuariosServiceNoContestaATiempoNoSeAbreLaConexion() {
        USUARIOS.responde(42L, "USUARIO").demora(4_000);

        assertThatThrownBy(() -> conectarCon(tokenConSub("sub-con-timeout"))).isNotNull();
    }

    // con la identidad ya en cache, una caida de usuarios-service no impide reconectar: es lo que
    // evita que una partida en curso se caiga entera por un reinicio del otro servicio
    @Test
    void conLaIdentidadEnCacheSePuedeReconectarAunqueUsuariosServiceSeCaiga() throws Exception {
        USUARIOS.responde(42L, "USUARIO");
        String token = tokenConSub("sub-que-queda-en-cache");

        conectarCon(token).disconnect();
        assertThat(USUARIOS.llamadas()).isEqualTo(1);

        USUARIOS.respondeConEstado(500);

        StompSession reconexion = conectarCon(token);
        assertThat(reconexion.isConnected()).isTrue();
    }

    private String tokenConSub(String sub) {
        return LlavesDePrueba.tokenDeEntraConSubYAudiencia(sub, TENANT_A, "api://" + CLIENT_ID);
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

    private StompSession conectarCon(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                headers, new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);
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
