package com.growlink.trivia.adapter.web;

import com.growlink.trivia.adapter.security.TokenService;
import com.growlink.trivia.adapter.web.dto.CrearPreguntaRequest;
import com.growlink.trivia.adapter.web.dto.PreguntaResponse;
import com.growlink.trivia.application.CategoriaNoPermitidaException;
import com.growlink.trivia.application.CursosServiceNoDisponibleException;
import com.growlink.trivia.application.NoAutenticadoException;
import com.growlink.trivia.application.PreguntaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// HU-23: el publicador agrega una pregunta al banco.
// El publicador es el dueño del token, no un id que mande el cliente: si viniera
// en el body, cualquiera con sesion podria crear preguntas a nombre de otro.
// Los errores propios de este endpoint se manejan aqui mismo para no tocar GlobalExceptionHandler.
@RestController
@RequestMapping("/api/preguntas")
public class PreguntaController {

    private final PreguntaService preguntaService;
    private final TokenService tokenService;

    public PreguntaController(PreguntaService preguntaService, TokenService tokenService) {
        this.preguntaService = preguntaService;
        this.tokenService = tokenService;
    }

    @PostMapping
    public ResponseEntity<PreguntaResponse> crear(
            @Valid @RequestBody CrearPreguntaRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Long publicadorUsuarioId = tokenService.usuarioIdDe(authorization);
        var pregunta = preguntaService.crear(request.categoria(), request.texto(), request.opciones(),
                request.respuestaCorrecta(), publicadorUsuarioId, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(PreguntaResponse.from(pregunta));
    }

    @ExceptionHandler(NoAutenticadoException.class)
    public ResponseEntity<Map<String, String>> handleNoAutenticado(NoAutenticadoException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(CategoriaNoPermitidaException.class)
    public ResponseEntity<Map<String, String>> handleCategoriaNoPermitida(CategoriaNoPermitidaException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(CursosServiceNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> handleCursosNoDisponible(CursosServiceNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
    }
}
