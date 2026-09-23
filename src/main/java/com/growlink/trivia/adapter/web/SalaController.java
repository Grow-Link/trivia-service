package com.growlink.trivia.adapter.web;

import com.growlink.trivia.adapter.web.dto.CrearSalaRequest;
import com.growlink.trivia.adapter.web.dto.SalaResponse;
import com.growlink.trivia.application.SalaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Crear la sala es lo unico que pasa por REST, unirse e iniciar es por WebSocket
// ver SalaWebSocketController
@RestController
@RequestMapping("/api/salas")
public class SalaController {

    private final SalaService salaService;

    public SalaController(SalaService salaService) {
        this.salaService = salaService;
    }

    @PostMapping
    public ResponseEntity<SalaResponse> crear(@Valid @RequestBody CrearSalaRequest request) {
        var sala = salaService.crear(request.categoria(), request.hostUsuarioId(), request.hostNombre(),
                request.numPreguntas(), request.duracionSegundos());
        return ResponseEntity.status(HttpStatus.CREATED).body(SalaResponse.from(sala));
    }

    @GetMapping("/{codigo}")
    public SalaResponse obtener(@PathVariable String codigo) {
        return SalaResponse.from(salaService.obtenerPorCodigo(codigo));
    }
}
