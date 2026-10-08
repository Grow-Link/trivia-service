package com.growlink.trivia.adapter.ws;

import com.growlink.trivia.adapter.ws.dto.*;
import com.growlink.trivia.application.Destinos;
import com.growlink.trivia.application.JuegoService;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.domain.SalaTrivia;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

// El cliente manda a /app/salas/{codigo}/unirse, /iniciar o /responder
// y todos los que estan en la sala escuchan en /topic/salas.{codigo}
@Controller
public class SalaWebSocketController {

    private final SalaService salaService;
    private final JuegoService juegoService;
    private final SimpMessagingTemplate messagingTemplate;

    public SalaWebSocketController(SalaService salaService, JuegoService juegoService,
                                    SimpMessagingTemplate messagingTemplate) {
        this.salaService = salaService;
        this.juegoService = juegoService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/salas/{codigo}/unirse")
    public void unirse(@DestinationVariable String codigo, @Payload UnirseRequest request) {
        intentar(codigo, () -> {
            salaService.unirse(codigo, request.usuarioId(), request.nombre());
            avisarATodos(codigo);
        });
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
