package com.growlink.trivia.application;

import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.domain.SalaPregunta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Barredor de preguntas vencidas: corre en todas las replicas (ninguna tiene estado propio,
// todo vive en la base de datos), por eso no se puede perder un cierre aunque una replica caiga.
// Cada tick busca las preguntas activas sin cerrar y le pide a JuegoService que intente cerrar
// cada una por su cuenta; el lock de fila dentro de intentarCerrarPorTiempo es lo que evita que
// dos replicas (o el barredor y una respuesta que llega justo en ese momento) avancen dos veces
// la misma pregunta.
@Component
public class CierrePreguntaScheduler {

    private static final Logger log = LoggerFactory.getLogger(CierrePreguntaScheduler.class);

    private final SalaPreguntaRepository salaPreguntaRepository;
    private final JuegoService juegoService;

    public CierrePreguntaScheduler(SalaPreguntaRepository salaPreguntaRepository, JuegoService juegoService) {
        this.salaPreguntaRepository = salaPreguntaRepository;
        this.juegoService = juegoService;
    }

    @Scheduled(fixedDelayString = "${trivia.cierre-pregunta.intervalo-barredor-ms:1000}")
    public void cerrarPreguntasVencidas() {
        List<SalaPregunta> activas;
        try {
            activas = salaPreguntaRepository.buscarActivasSinCerrar();
        } catch (RuntimeException e) {
            log.warn("No se pudo buscar preguntas activas para el cierre por tiempo", e);
            return;
        }
        // una sala con problemas no debe frenar el cierre de las demas en este mismo tick
        for (SalaPregunta activa : activas) {
            try {
                juegoService.intentarCerrarPorTiempo(activa.getId());
            } catch (RuntimeException e) {
                log.warn("No se pudo evaluar el cierre por tiempo de la pregunta {}", activa.getId(), e);
            }
        }
    }
}
