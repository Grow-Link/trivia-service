package com.growlink.trivia.adapter.web;

import com.growlink.trivia.application.FaltanParticipantesException;
import com.growlink.trivia.application.JuegoNoDisponibleException;
import com.growlink.trivia.application.NoAutenticadoException;
import com.growlink.trivia.application.NoAutorizadoException;
import com.growlink.trivia.application.NoEsParticipanteException;
import com.growlink.trivia.application.NoHayPreguntasSuficientesException;
import com.growlink.trivia.application.PartidaNoEncontradaException;
import com.growlink.trivia.application.RetoNoEncontradoException;
import com.growlink.trivia.application.RevanchaNoDisponibleException;
import com.growlink.trivia.application.SalaNoEncontradaException;
import com.growlink.trivia.application.SalaYaEmpezoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorBody(String message) {
    }

    @ExceptionHandler({SalaNoEncontradaException.class, RetoNoEncontradoException.class,
            PartidaNoEncontradaException.class})
    public ResponseEntity<ErrorBody> handleNoEncontrada(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler({SalaYaEmpezoException.class, FaltanParticipantesException.class,
            DataIntegrityViolationException.class, JuegoNoDisponibleException.class,
            NoHayPreguntasSuficientesException.class, RevanchaNoDisponibleException.class})
    public ResponseEntity<ErrorBody> handleConflicto(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(NoAutenticadoException.class)
    public ResponseEntity<ErrorBody> handleNoAutenticado(NoAutenticadoException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler({NoAutorizadoException.class, NoEsParticipanteException.class})
    public ResponseEntity<ErrorBody> handleNoAutorizado(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleArgumentoInvalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidacion(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ErrorBody(message));
    }
}
