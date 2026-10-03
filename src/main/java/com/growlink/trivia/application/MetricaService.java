package com.growlink.trivia.application;

import com.growlink.trivia.adapter.persistence.MetricaEventoRepository;
import com.growlink.trivia.domain.MetricaEvento;
import com.growlink.trivia.domain.TipoEvento;
import org.springframework.stereotype.Service;

// aqui solo se escriben eventos, las consultas del dashboard se agregan en HU-24
@Service
public class MetricaService {

    private final MetricaEventoRepository repository;

    public MetricaService(MetricaEventoRepository repository) {
        this.repository = repository;
    }

    public void registrar(TipoEvento tipo, Long salaId) {
        repository.save(new MetricaEvento(tipo, salaId, null, null, null));
    }

    public void registrarPrimeraRespuesta(Long salaId, long latenciaMs) {
        repository.save(new MetricaEvento(TipoEvento.PRIMERA_RESPUESTA, salaId, latenciaMs, null, null));
    }

    public void registrarPartidaFinalizada(Long salaId, long duracionMs, int participantes) {
        repository.save(new MetricaEvento(TipoEvento.PARTIDA_FINALIZADA, salaId, null, duracionMs, participantes));
    }
}
