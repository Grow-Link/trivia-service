package com.growlink.trivia.adapter.ws;

import com.growlink.trivia.adapter.ws.dto.ErrorMessage;
import com.growlink.trivia.adapter.ws.dto.ParticipanteView;
import com.growlink.trivia.adapter.ws.dto.SalaUpdateMessage;
import com.growlink.trivia.adapter.ws.dto.UnirseRequest;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.domain.SalaTrivia;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

// El cliente manda a /app/salas/{codigo}/unirse o /iniciar
// y todos los que estan en la sala escuchan en /topic/salas/{codigo}
@Controller
public class SalaWebSocketController {

    private final SalaService salaService;
    private final SimpMessagingTemplate messagingTemplate;

    public SalaWebSocketController(SalaService salaService, SimpMessagingTemplate messagingTemplate) {
        this.salaService = salaService;
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
        });
    }

    private void avisarATodos(String codigo) {
        SalaTrivia sala = salaService.obtenerPorCodigo(codigo);
        var participantes = salaService.listarParticipantes(sala.getId()).stream()
                .map(p -> new ParticipanteView(p.getUsuarioId(), p.getNombre()))
                .toList();
        messagingTemplate.convertAndSend("/topic/salas/" + codigo,
                SalaUpdateMessage.of(codigo, sala.getEstado().name(), participantes));
    }

    private void intentar(String codigo, Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            // si algo sale mal no tumbamos la conexion, solo le avisamos a la sala
            messagingTemplate.convertAndSend("/topic/salas/" + codigo, ErrorMessage.of(e.getMessage()));
        }
    }
}
