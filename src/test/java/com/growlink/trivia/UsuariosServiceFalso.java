package com.growlink.trivia;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

// Un usuarios-service de mentiras para las pruebas de IdentidadDesdeUsuariosService: un HttpServer
// del JDK que atiende GET /api/usuarios/me, igual que LlavesDePrueba hace con el JWKS. Nada sale
// a la red y no hace falta ninguna dependencia nueva (ni WireMock ni MockWebServer).
//
// Se usa un servidor de verdad y no MockRestServiceServer porque hay dos cosas que un mock de
// RestClient no puede simular: un timeout de socket real y un servidor que no contesta. Ambos son
// casos que esta HU tiene que cubrir, porque de ellos depende que NUNCA se acepte un token sin
// saber de quien es.
//
// Cuenta las llamadas, que es lo que permite probar el cache: dos peticiones con el mismo token
// tienen que dejar el contador en 1.
//
// Tambien atiende POST /api/interno/trivias-ganadas/{id} (lo que llama UsuariosRestClient al
// terminar una partida) y guarda el X-Request-Id con que llego cada llamada, para probar que la
// traza pasa de trivia a usuarios (ver ReenvioDeRequestId).
public final class UsuariosServiceFalso implements AutoCloseable {

    private final HttpServer servidor;
    private final AtomicInteger llamadas = new AtomicInteger();
    private final AtomicReference<String> ultimoAuthorization = new AtomicReference<>();
    private final AtomicReference<String> ultimoRequestIdDeMe = new AtomicReference<>();
    // cada victoria que le avisaron: el usuarioId de la ruta y el X-Request-Id con que llego
    private final BlockingQueue<Victoria> victorias = new LinkedBlockingQueue<>();

    public record Victoria(long usuarioId, String requestId) {
    }

    // que responder: estado HTTP, cuerpo, y cuanto demorar antes de contestar
    private final AtomicInteger estado = new AtomicInteger(200);
    private final AtomicReference<String> cuerpo = new AtomicReference<>(me(1L, "USUARIO"));
    private final AtomicLong demoraMs = new AtomicLong(0);

    public UsuariosServiceFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (Exception e) {
            throw new IllegalStateException("no se pudo levantar el usuarios-service de prueba", e);
        }
        servidor.createContext("/api/usuarios/me", intercambio -> {
            llamadas.incrementAndGet();
            ultimoAuthorization.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            ultimoRequestIdDeMe.set(intercambio.getRequestHeaders().getFirst("X-Request-Id"));
            long demora = demoraMs.get();
            if (demora > 0) {
                try {
                    Thread.sleep(demora);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] respuesta = cuerpo.get().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado.get(), respuesta.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(respuesta);
            }
        });
        servidor.createContext("/api/interno/trivias-ganadas/", intercambio -> {
            String ruta = intercambio.getRequestURI().getPath();
            long usuarioId = Long.parseLong(ruta.substring(ruta.lastIndexOf('/') + 1));
            victorias.add(new Victoria(usuarioId, intercambio.getRequestHeaders().getFirst("X-Request-Id")));
            intercambio.sendResponseHeaders(204, -1);
            intercambio.close();
        });
        servidor.start();
    }

    // el JSON tal como lo devuelve el MeResponse de usuarios-service: usuarioId, rol, rolElegido,
    // correo y nombre. Se mandan todos para que la prueba cubra tambien que los campos que a
    // trivia no le interesan no molestan
    public static String me(Long usuarioId, String rol) {
        return "{\"usuarioId\":" + usuarioId + ",\"rol\":\"" + rol + "\","
                + "\"rolElegido\":true,\"correo\":\"alguien@escuelaing.edu.co\",\"nombre\":\"Alguien\"}";
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public UsuariosServiceFalso responde(Long usuarioId, String rol) {
        estado.set(200);
        cuerpo.set(me(usuarioId, rol));
        return this;
    }

    public UsuariosServiceFalso respondeConEstado(int codigo) {
        estado.set(codigo);
        cuerpo.set("{\"message\":\"asi es la vida\"}");
        return this;
    }

    public UsuariosServiceFalso respondeCuerpo(String json) {
        estado.set(200);
        cuerpo.set(json);
        return this;
    }

    // para probar el timeout de lectura, que esta en 3 s
    public UsuariosServiceFalso demora(long ms) {
        demoraMs.set(ms);
        return this;
    }

    public int llamadas() {
        return llamadas.get();
    }

    public void reiniciarContador() {
        llamadas.set(0);
    }

    public String ultimoAuthorization() {
        return ultimoAuthorization.get();
    }

    public String ultimoRequestIdDeMe() {
        return ultimoRequestIdDeMe.get();
    }

    /** La siguiente victoria que le avisaron, esperando hasta `segundos`; null si no llego ninguna. */
    public Victoria siguienteVictoria(long segundos) throws InterruptedException {
        return victorias.poll(segundos, TimeUnit.SECONDS);
    }

    // apagarlo es lo que simula "usuarios-service esta caido": el puerto deja de aceptar
    @Override
    public void close() {
        servidor.stop(0);
    }
}
