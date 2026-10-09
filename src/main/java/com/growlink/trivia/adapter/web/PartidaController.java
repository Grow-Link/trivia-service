package com.growlink.trivia.adapter.web;

import com.growlink.trivia.adapter.security.TokenService;
import com.growlink.trivia.application.PartidaService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// El historial de competencias: ultimos ganadores, mis partidas, salon de la fama y el detalle de una partida.
// Cualquier persona con sesion puede verlo.
@RestController
@RequestMapping("/api/partidas")
public class PartidaController {

    private final PartidaService partidaService;
    private final TokenService tokenService;

    public PartidaController(PartidaService partidaService, TokenService tokenService) {
        this.partidaService = partidaService;
        this.tokenService = tokenService;
    }

    @GetMapping("/ganadores")
    public List<PartidaService.ResumenConPodio> ganadores(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "20") int limite) {
        tokenService.usuarioIdDe(authorization);
        return partidaService.ultimosGanadores(limite);
    }

    @GetMapping("/mias")
    public List<PartidaService.MiPartida> mias(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "20") int limite) {
        return partidaService.mias(tokenService.usuarioIdDe(authorization), limite);
    }

    @GetMapping("/salon-de-la-fama")
    public List<PartidaService.SalonDeLaFama> salonDeLaFama(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "10") int limite) {
        tokenService.usuarioIdDe(authorization);
        return partidaService.salonDeLaFama(limite);
    }

    @GetMapping("/{codigo}")
    public PartidaService.Detalle detalle(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String codigo) {
        tokenService.usuarioIdDe(authorization);
        return partidaService.detalle(codigo);
    }
}
