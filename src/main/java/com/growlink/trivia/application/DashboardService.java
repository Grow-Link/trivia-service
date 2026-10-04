package com.growlink.trivia.application;

import com.growlink.trivia.adapter.persistence.MetricaEventoRepository;
import com.growlink.trivia.adapter.persistence.SalaParticipanteRepository;
import com.growlink.trivia.adapter.persistence.SalaTriviaRepository;
import com.growlink.trivia.domain.EstadoSala;
import com.growlink.trivia.domain.TipoEvento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

// HU-24: todo son consultas agregadas sobre las tablas del juego y metrica_evento,
// sin herramientas externas de monitoreo
@Service
public class DashboardService {

    public record Dashboard(
            long salasActivas,
            long salasEnEspera,
            long salasEnCurso,
            long participantesConectados,
            Double latenciaPromedioMs,
            long empatesResueltos,
            long partidasFinalizadas,
            Double duracionPromedioPartidaMs,
            Integer ventanaMinutos,
            Instant generadoEn) {
    }

    private final SalaTriviaRepository salaRepository;
    private final SalaParticipanteRepository participanteRepository;
    private final MetricaEventoRepository metricaRepository;
    private final long salaActivaMinutos;

    public DashboardService(SalaTriviaRepository salaRepository, SalaParticipanteRepository participanteRepository,
                            MetricaEventoRepository metricaRepository,
                            @Value("${trivia.dashboard.sala-activa-minutos:120}") long salaActivaMinutos) {
        this.salaRepository = salaRepository;
        this.participanteRepository = participanteRepository;
        this.metricaRepository = metricaRepository;
        this.salaActivaMinutos = salaActivaMinutos;
    }

    // ultimosMinutos limita latencia, empates y partidas a los eventos recientes
    // si viene vacio se cuenta todo lo que hay registrado
    public Dashboard obtener(Integer ultimosMinutos) {
        Instant ahora = Instant.now();
        Instant desdeEventos = ultimosMinutos == null ? Instant.EPOCH : ahora.minus(Duration.ofMinutes(ultimosMinutos));

        // una sala que quedo abandonada en espera no puede contar como activa para siempre
        Instant desdeSalas = ahora.minus(Duration.ofMinutes(salaActivaMinutos));
        long enEspera = salaRepository.countByEstadoAndCreadaEnGreaterThanEqual(EstadoSala.ESPERANDO, desdeSalas);
        long enCurso = salaRepository.countByEstadoAndCreadaEnGreaterThanEqual(EstadoSala.EN_CURSO, desdeSalas);

        return new Dashboard(
                enEspera + enCurso,
                enEspera,
                enCurso,
                participanteRepository.contarEnSalasActivas(desdeSalas),
                metricaRepository.latenciaPromedio(TipoEvento.PRIMERA_RESPUESTA, desdeEventos),
                metricaRepository.countByTipoAndMomentoGreaterThanEqual(TipoEvento.EMPATE_RESUELTO, desdeEventos),
                metricaRepository.countByTipoAndMomentoGreaterThanEqual(TipoEvento.PARTIDA_FINALIZADA, desdeEventos),
                metricaRepository.duracionPromedio(TipoEvento.PARTIDA_FINALIZADA, desdeEventos),
                ultimosMinutos,
                ahora);
    }
}
