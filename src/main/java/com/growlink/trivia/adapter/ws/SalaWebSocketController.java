package com.growlink.trivia.adapter.ws;

import com.growlink.trivia.adapter.ws.dto.*;
import com.growlink.trivia.application.Destinos;
import com.growlink.trivia.application.JuegoService;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.domain.EstadoSala;
import com.growlink.trivia.domain.SalaParticipante;
import com.growlink.trivia.domain.SalaTrivia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// El cliente manda a /app/salas/{codigo}/unirse, /iniciar, /responder o /salir
// y todos los que estan en la sala escuchan en /topic/salas.{codigo}
@Controller
public class SalaWebSocketController implements ApplicationListener<SessionDisconnectEvent> {

    private final SalaService salaService;
    private final JuegoService juegoService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;
    private final long graciaSalidaMs;

    // en que sala y como que usuario esta cada sesion de WebSocket, para saber a quien tratar
    // como salida si la conexion se cae. Es estado de la conexion (no de la partida), asi que
    // vivir en memoria por replica esta bien: si esa replica cae, la conexion tambien cae con ella.
    // Lo que SI podria pasar a otra replica es la reconexion, y por eso esta clase no intenta
    // cancelar el temporizador de gracia por su cuenta: SalaService.salirPorDesconexion decide,
    // leyendo SalaParticipante.ultimaConexion en la base de datos, si de verdad hubo salida
    private final Map<String, Participante> porSesion = new ConcurrentHashMap<>();

    private record Participante(String codigo, Long usuarioId) {
    }

    public SalaWebSocketController(SalaService salaService, JuegoService juegoService,
                                    SimpMessagingTemplate messagingTemplate, TaskScheduler taskScheduler,
                                    @Value("${trivia.salida.gracia-ms:8000}") long graciaSalidaMs) {
        this.salaService = salaService;
        this.juegoService = juegoService;
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
        this.graciaSalidaMs = graciaSalidaMs;
    }

    @MessageMapping("/salas/{codigo}/unirse")
    public void unirse(@DestinationVariable String codigo, @Payload UnirseRequest request,
                        @Header("simpSessionId") String sessionId) {
        intentar(codigo, () -> {
            salaService.unirse(codigo, request.usuarioId(), request.nombre());
            porSesion.put(sessionId, new Participante(codigo, request.usuarioId()));
            avisarATodos(codigo);
        });
    }

    @MessageMapping("/salas/{codigo}/salir")
    public void salir(@DestinationVariable String codigo, @Payload SalirRequest request) {
        intentar(codigo, () -> emitirSalida(codigo, request.usuarioId(), salaService.salir(codigo, request.usuarioId())));
    }

    // se dispara cuando se cae la conexion (cerraron la pestana, se les fue la red, etc). No se
    // trata como salida de inmediato: se espera un margen corto por si es un refresh de pagina o
    // una caida de red momentanea y el cliente vuelve a mandar "unirse", a esta misma replica o a
    // cualquier otra. momentoDesconexion queda fijo ahora, antes de esperar, para que
    // salirPorDesconexion pueda comparar contra la ultima conexion de verdad que haya en la base de
    // datos en el momento en que el margen se cumpla
    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        Participante participante = porSesion.remove(event.getSessionId());
        if (participante == null) {
            return; // esta sesion nunca llego a unirse a ninguna sala
        }
        Instant momentoDesconexion = Instant.now();
        taskScheduler.schedule(
                () -> intentar(participante.codigo(), () -> emitirSalida(participante.codigo(), participante.usuarioId(),
                        salaService.salirPorDesconexion(participante.codigo(), participante.usuarioId(), momentoDesconexion))),
                Instant.now().plusMillis(graciaSalidaMs));
    }

    private void emitirSalida(String codigo, Long usuarioId, SalaService.SalidaResultado resultado) {
        if (resultado == null) {
            return; // idempotente: no habia nada que hacer (ya se habia ido, hubo reconexion, o la sala ya termino)
        }
        messagingTemplate.convertAndSend(Destinos.sala(codigo),
                JugadorSalioBroadcast.of(codigo, usuarioId, resultado.nombre(), resultado.nuevoHostUsuarioId(),
                        resultado.estado().name()));
        if (resultado.estado() != EstadoSala.EN_CURSO) {
            avisarATodos(codigo); // ESPERANDO (traspaso de host) o CANCELADA (sala vacia)
        }
        if (resultado.terminarPartida()) {
            juegoService.finalizarPorAbandono(codigo);
        }
    }

    @MessageMapping("/salas/{codigo}/iniciar")
    public void iniciar(@DestinationVariable String codigo) {
        intentar(codigo, () -> {
            salaService.iniciar(codigo);
            avisarATodos(codigo);
            juegoService.empezarJuego(salaService.obtenerPorCodigo(codigo));
        });
    }

    @MessageMapping("/salas/{codigo}/responder")
    public void responder(@DestinationVariable String codigo, @Payload RespuestaRequest request) {
        intentar(codigo, () ->
                juegoService.responder(codigo, request.usuarioId(), request.indice(), request.opcionElegida()));
    }

    // el servicio ya confirmo la transaccion cuando esto se ejecuta (crearRevancha es
    // @Transactional y aqui ya retorno), asi que nadie recibe el codigo nuevo antes de
    // que la sala exista de verdad en la base de datos
    @MessageMapping("/salas/{codigo}/revancha")
    public void revancha(@DestinationVariable String codigo, @Payload RevanchaRequest request) {
        intentar(codigo, () -> {
            SalaTrivia nueva = salaService.crearRevancha(codigo, request.usuarioId(), request.nombre());
            String hostNombre = salaService.listarParticipantes(nueva.getId()).stream()
                    .filter(p -> p.getUsuarioId().equals(nueva.getHostUsuarioId()))
                    .map(SalaParticipante::getNombre)
                    .findFirst()
                    .orElse(request.nombre());
            messagingTemplate.convertAndSend(Destinos.sala(codigo),
                    RevanchaBroadcast.of(codigo, nueva.getCodigo(), nueva.getHostUsuarioId(), hostNombre));
        });
    }

    private void avisarATodos(String codigo) {
        SalaTrivia sala = salaService.obtenerPorCodigo(codigo);
        var participantes = salaService.listarParticipantes(sala.getId()).stream()
                .map(p -> new ParticipanteView(p.getUsuarioId(), p.getNombre()))
                .toList();
        messagingTemplate.convertAndSend(Destinos.sala(codigo),
                SalaUpdateMessage.of(codigo, sala.getEstado().name(), participantes));
    }

    private void intentar(String codigo, Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            // si algo sale mal no tumbamos la conexion, solo le avisamos a la sala
            messagingTemplate.convertAndSend(Destinos.sala(codigo), ErrorMessage.of(e.getMessage()));
        }
    }
}
