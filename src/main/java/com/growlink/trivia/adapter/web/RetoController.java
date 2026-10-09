package com.growlink.trivia.adapter.web;

import com.growlink.trivia.adapter.security.TokenService;
import com.growlink.trivia.adapter.web.dto.CrearRetoRequest;
import com.growlink.trivia.adapter.web.dto.RetoResponse;
import com.growlink.trivia.application.RetoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// "Te reto a una trivia". Todo sale del token: quien reta y quien responde es siempre quien esta conectado.
@RestController
@RequestMapping("/api/retos")
public class RetoController {

    private final RetoService retoService;
    private final TokenService tokenService;

    public RetoController(RetoService retoService, TokenService tokenService) {
        this.retoService = retoService;
        this.tokenService = tokenService;
    }

    @PostMapping
    public ResponseEntity<RetoResponse> retar(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                              @Valid @RequestBody CrearRetoRequest request) {
        Long retadorId = tokenService.usuarioIdDe(authorization);
        var reto = retoService.retar(retadorId, request.retadorNombre(), request.retadoUsuarioId(),
                request.retadoNombre(), request.categoria(), request.numPreguntas(), request.duracionSegundos(),
                request.mensaje());
        return ResponseEntity.status(HttpStatus.CREATED).body(RetoResponse.from(reto));
    }

    @GetMapping("/recibidos")
    public List<RetoResponse> recibidos(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return retoService.recibidos(tokenService.usuarioIdDe(authorization)).stream().map(RetoResponse::from).toList();
    }

    @GetMapping("/enviados")
    public List<RetoResponse> enviados(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return retoService.enviados(tokenService.usuarioIdDe(authorization)).stream().map(RetoResponse::from).toList();
    }

    @PostMapping("/{id}/aceptar")
    public RetoResponse aceptar(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                @PathVariable Long id) {
        return RetoResponse.from(retoService.aceptar(id, tokenService.usuarioIdDe(authorization)));
    }

    @PostMapping("/{id}/rechazar")
    public RetoResponse rechazar(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                 @PathVariable Long id) {
        return RetoResponse.from(retoService.rechazar(id, tokenService.usuarioIdDe(authorization)));
    }
}
