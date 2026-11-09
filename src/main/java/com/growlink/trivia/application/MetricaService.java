package com.growlink.trivia.application;

import com.growlink.trivia.adapter.persistence.MetricaEventoRepository;
import com.growlink.trivia.config.MetricasNegocio;
import com.growlink.trivia.domain.MetricaEvento;
import com.growlink.trivia.domain.TipoEvento;
import org.springframework.stereotype.Service;

// aqui solo se escriben eventos, las consultas del dashboard se agregan en HU-24.
//
// Este es tambien el unico sitio por donde pasan los eventos que interesan al tablero de
// Prometheus, asi que de paso se mueven los contadores de MetricasNegocio: el evento queda
// guardado en la base de datos (historico para el dashboard) y contado en memoria (para el
// scrape), sin repartir llamadas de metricas por SalaService y JuegoService
@Service
public class MetricaService {

    private final MetricaEventoRepository repository;
    private final MetricasNegocio metricasNegocio;

    public MetricaService(MetricaEventoRepository repository, MetricasNegocio metricasNegocio) {
        this.repository = repository;
        this.metricasNegocio = metricasNegocio;
    }

    public void registrar(TipoEvento tipo, Long salaId) {
        repository.save(new MetricaEvento(tipo, salaId, null, null, null));
        if (tipo == TipoEvento.SALA_CREADA) {
            metricasNegocio.salaCreada();
        } else if (tipo == TipoEvento.PARTICIPANTE_UNIDO) {
            metricasNegocio.jugadorUnido();
        }
    }

    public void registrarPrimeraRespuesta(Long salaId, long latenciaMs) {
        repository.save(new MetricaEvento(TipoEvento.PRIMERA_RESPUESTA, salaId, latenciaMs, null, null));
    }

    public void registrarPartidaFinalizada(Long salaId, long duracionMs, int participantes) {
        repository.save(new MetricaEvento(TipoEvento.PARTIDA_FINALIZADA, salaId, null, duracionMs, participantes));
        metricasNegocio.partidaTerminada();
    }
}
