package com.growlink.trivia.ws;

import com.growlink.trivia.UsuariosServiceFalso;
import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.application.CursosClient;
import com.growlink.trivia.application.Destinos;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.application.UsuariosClient;
import com.growlink.trivia.config.RequestIdFilter;
import com.growlink.trivia.domain.SalaTrivia;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.growlink.trivia.RespuestasDePrueba.correcta;
import static com.growlink.trivia.RespuestasDePrueba.incorrecta;
import static org.assertj.core.api.Assertions.assertThat;

// ATRIBUTO observabilidad: el X-Request-Id de lo que este servicio esta atendiendo viaja en cada llamada a
// usuarios-service y a cursos-service (ver ReenvioDeRequestId). Lo importante es la partida: cuando la ultima
// respuesta termina la partida, la llamada que le suma la victoria al ganador lleva el id de ESA respuesta (que es un
// mensaje del WebSocket, no una peticion HTTP: el id lo pone RequestIdStompInterceptor). Con un usuarios-service y un
// cursos-service de mentiras que anotan con que id les llego cada llamada.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:triviareenvio;MODE=PostgreSQL")
class ReenvioDeRequestIdIntegrationTest {

    private static final UsuariosServiceFalso USUARIOS = new UsuariosServiceFalso();
    private static final AtomicReference<String> REQUEST_ID_EN_CURSOS = new AtomicReference<>();
    private static final HttpServer CURSOS = cursosFalso();

    @DynamicPropertySource
    static void serviciosDeMentira(DynamicPropertyRegistry registro) {
        registro.add("usuarios.base-url", USUARIOS::baseUrl);
        registro.add("cursos.base-url", () -> "http://127.0.0.1:" + CURSOS.getAddress().getPort());
    }

    @LocalServerPort
    private int port;
    @Autowired
    private SalaService salaService;
    @Autowired
    private SalaPreguntaRepository salaPreguntaRepository;
    @Autowired
    private UsuariosClient usuariosClient;
    @Autowired
    private CursosClient cursosClient;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void preparar() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void limpiar() {
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    @AfterAll
    static void apagar() {
        USUARIOS.close();
        CURSOS.stop(0);
    }

    @Test
    void laVictoriaQueTerminaLaPartidaLlevaElIdDeLaUltimaRespuesta() throws Exception {
        String codigo = crearSala(); // la crea Ana (1), que queda de host
        BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        StompSession sesion = conectar();
        sesion.subscribe(Destinos.sala(codigo), new AlaCola(mensajes));
        sesion.send("/app/salas/" + codigo + "/unirse", Map.of("usuarioId", 2, "nombre", "Beto"));
        esperar(mensajes, "SALA_UPDATE");
        sesion.send("/app/salas/" + codigo + "/iniciar", Map.of());
        SalaTrivia sala = salaService.obtenerPorCodigo(codigo);

        // Ana acierta todas y Beto ninguna: Ana gana. Las dos respuestas de la ultima pregunta llevan el mismo id,
        // porque cualquiera de las dos puede ser la que termine la partida (se atienden en hilos distintos)
        for (int indice = 0; indice < 5; indice++) {
            esperar(mensajes, "PREGUNTA");
            String id = indice == 4 ? "respuesta-final-0001" : null;
            responder(sesion, codigo, 1, indice, correcta(salaPreguntaRepository, sala, indice), id);
            responder(sesion, codigo, 2, indice, incorrecta(salaPreguntaRepository, sala, indice), id);
        }
        esperar(mensajes, "RESULTADOS_FINALES");

        UsuariosServiceFalso.Victoria victoria = victoriaDe(1L);
        assertThat(victoria).as("trivia-service no le aviso la victoria a usuarios-service").isNotNull();
        assertThat(victoria.requestId()).isEqualTo("respuesta-final-0001");
    }

    @Test
    void unaLlamadaAUsuariosServiceLlevaElIdDeLoQueSeEstaAtendiendo() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "llamada-directa-0001");

        usuariosClient.registrarVictoria(7L);

        assertThat(victoriaDe(7L).requestId()).isEqualTo("llamada-directa-0001");
    }

    @Test
    void unaLlamadaACursosServiceTambien() {
        MDC.put(RequestIdFilter.MDC_KEY, "llamada-a-cursos-0001");

        cursosClient.categoriasPublicadas(5L, null);

        assertThat(REQUEST_ID_EN_CURSOS.get()).isEqualTo("llamada-a-cursos-0001");
    }

    @Test
    void sinNadaQueAtenderNoSeInventaUnaCabecera() throws Exception {
        // fuera de una peticion o un mensaje no hay id que reenviar: usuarios-service pondra el suyo
        usuariosClient.registrarVictoria(8L);

        assertThat(victoriaDe(8L).requestId()).isNull();
    }

    // ---------------------------------------------------------------- utilidades

    private void responder(StompSession sesion, String codigo, int usuarioId, int indice, int opcion, String requestId) {
        StompHeaders cabeceras = new StompHeaders();
        cabeceras.setDestination("/app/salas/" + codigo + "/responder");
        if (requestId != null) {
            cabeceras.set(RequestIdFilter.HEADER, requestId);
        }
        sesion.send(cabeceras, Map.of("usuarioId", usuarioId, "indice", indice, "opcionElegida", opcion));
    }

    // la victoria de ese usuario (las de otras pruebas de esta misma clase se descartan)
    private UsuariosServiceFalso.Victoria victoriaDe(long usuarioId) throws InterruptedException {
        long hasta = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < hasta) {
            UsuariosServiceFalso.Victoria v = USUARIOS.siguienteVictoria(1);
            if (v != null && v.usuarioId() == usuarioId) {
                return v;
            }
        }
        return null;
    }

    private static Map<String, Object> esperar(BlockingQueue<Map<String, Object>> cola, String tipo)
            throws InterruptedException {
        long hasta = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < hasta) {
            Map<String, Object> mensaje = cola.poll(1, TimeUnit.SECONDS);
            if (mensaje != null && tipo.equals(mensaje.get("type"))) {
                return mensaje;
            }
            if (mensaje != null && "ERROR".equals(mensaje.get("type"))) {
                throw new AssertionError("trivia-service aviso un error: " + mensaje.get("message"));
            }
        }
        throw new AssertionError("no llego ningun " + tipo + " en 10 segundos");
    }

    private String crearSala() {
        Map<String, Object> cuerpo = Map.of("categoria", "INGENIERIA_SISTEMAS", "hostUsuarioId", 1,
                "hostNombre", "Ana", "numPreguntas", 5, "duracionSegundos", 10);
        @SuppressWarnings("unchecked")
        Map<String, Object> sala = new RestTemplate()
                .postForEntity("http://localhost:" + port + "/api/salas", cuerpo, Map.class).getBody();
        return (String) sala.get("codigo");
    }

    private StompSession conectar() throws Exception {
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                new StompHeaders(), new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);
    }

    // un cursos-service de mentiras que contesta GET /api/cursos con una lista vacia y anota el X-Request-Id
    private static HttpServer cursosFalso() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/api/cursos", intercambio -> {
                REQUEST_ID_EN_CURSOS.set(intercambio.getRequestHeaders().getFirst(RequestIdFilter.HEADER));
                byte[] respuesta = "[]".getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, respuesta.length);
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(respuesta);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo levantar el cursos-service de prueba", e);
        }
    }

    private static final class AlaCola implements StompFrameHandler {
        private final BlockingQueue<Map<String, Object>> cola;

        private AlaCola(BlockingQueue<Map<String, Object>> cola) {
            this.cola = cola;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            cola.add((Map<String, Object>) payload);
        }
    }
}
