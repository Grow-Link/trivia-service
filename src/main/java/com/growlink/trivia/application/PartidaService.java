package com.growlink.trivia.application;

import com.growlink.trivia.adapter.persistence.PartidaPuestoRepository;
import com.growlink.trivia.adapter.persistence.PartidaResultadoRepository;
import com.growlink.trivia.adapter.ws.dto.LeaderboardEntry;
import com.growlink.trivia.domain.PartidaPuesto;
import com.growlink.trivia.domain.PartidaResultado;
import com.growlink.trivia.domain.SalaTrivia;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// El historial de competencias: se escribe una vez al terminar cada partida y se lee para mostrar quien gano,
// el detalle de cada puesto y el salon de la fama.
@Service
public class PartidaService {

    static final int LIMITE_MAXIMO = 50;

    public record Resumen(String codigo, String categoria, int numPreguntas, int duracionPreguntaSegundos,
                          int totalJugadores, Long ganadorUsuarioId, String ganadorNombre, int puntosGanador,
                          boolean empate, long duracionTotalSegundos, Instant finalizadaEn) {
        static Resumen de(PartidaResultado p) {
            return new Resumen(p.getCodigo(), p.getCategoria().name(), p.getNumPreguntas(),
                    p.getDuracionPreguntaSegundos(), p.getTotalJugadores(), p.getGanadorUsuarioId(),
                    p.getGanadorNombre(), p.getPuntosGanador(), p.isEmpate(), p.getDuracionTotalMs() / 1000,
                    p.getFinalizadaEn());
        }
    }

    public record Puesto(int posicion, Long usuarioId, String nombre, int puntos, int aciertos) {
        static Puesto de(PartidaPuesto p) {
            return new Puesto(p.getPosicion(), p.getUsuarioId(), p.getNombre(), p.getPuntos(), p.getAciertos());
        }
    }

    public record Detalle(Resumen partida, List<Puesto> ranking) {
    }

    // una partida de la lista "mis partidas", con el resultado de quien pregunta
    public record MiPartida(Resumen partida, int miPosicion, int misPuntos, int misAciertos) {
    }

    // los resumenes de la lista de ganadores llevan sus primeros puestos para poder pintar un podio sin pedir mas
    public record ResumenConPodio(Resumen partida, List<Puesto> podio) {
    }

    public record SalonDeLaFama(Long usuarioId, String nombre, long victorias, int mejorPuntaje) {
    }

    private final PartidaResultadoRepository partidaRepository;
    private final PartidaPuestoRepository puestoRepository;

    public PartidaService(PartidaResultadoRepository partidaRepository, PartidaPuestoRepository puestoRepository) {
        this.partidaRepository = partidaRepository;
        this.puestoRepository = puestoRepository;
    }

    // La llama JuegoService dentro de la misma transaccion que cierra la partida.
    // Si por cualquier razon se intenta guardar dos veces la misma sala, la segunda no hace nada.
    @Transactional
    public void registrar(SalaTrivia sala, List<LeaderboardEntry> rankingFinal, long duracionMs) {
        if (partidaRepository.existsBySalaId(sala.getId())) {
            return;
        }
        int mayor = rankingFinal.isEmpty() ? 0 : rankingFinal.get(0).puntos();
        long conPuntajeMayor = rankingFinal.stream().filter(e -> e.puntos() == mayor).count();
        boolean hayGanador = mayor > 0;
        LeaderboardEntry ganador = hayGanador ? rankingFinal.get(0) : null;

        PartidaResultado partida = partidaRepository.save(new PartidaResultado(sala, rankingFinal.size(),
                ganador == null ? null : ganador.usuarioId(), ganador == null ? null : ganador.nombre(),
                mayor, hayGanador && conPuntajeMayor > 1, duracionMs));

        int posicion = 1;
        for (LeaderboardEntry entrada : rankingFinal) {
            puestoRepository.save(new PartidaPuesto(partida.getId(), posicion++, entrada.usuarioId(),
                    entrada.nombre(), entrada.puntos(), entrada.aciertos()));
        }
    }

    @Transactional(readOnly = true)
    public List<ResumenConPodio> ultimosGanadores(int limite) {
        List<PartidaResultado> partidas = partidaRepository
                .findAllByOrderByFinalizadaEnDesc(PageRequest.of(0, acotar(limite)));
        if (partidas.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PartidaPuesto>> puestosPorPartida = puestoRepository
                .findByPartidaIdInOrderByPosicion(partidas.stream().map(PartidaResultado::getId).toList())
                .stream().collect(Collectors.groupingBy(PartidaPuesto::getPartidaId));
        return partidas.stream()
                .map(p -> new ResumenConPodio(Resumen.de(p),
                        puestosPorPartida.getOrDefault(p.getId(), List.of()).stream().limit(3).map(Puesto::de).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Detalle detalle(String codigo) {
        PartidaResultado partida = partidaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new SalaNoEncontradaException(codigo));
        return new Detalle(Resumen.de(partida),
                puestoRepository.findByPartidaIdOrderByPosicion(partida.getId()).stream().map(Puesto::de).toList());
    }

    @Transactional(readOnly = true)
    public List<MiPartida> mias(Long usuarioId, int limite) {
        List<PartidaPuesto> mios = puestoRepository.findByUsuarioIdOrderByPartidaIdDesc(usuarioId,
                PageRequest.of(0, acotar(limite)));
        if (mios.isEmpty()) {
            return List.of();
        }
        Map<Long, PartidaResultado> partidas = partidaRepository
                .findAllById(mios.stream().map(PartidaPuesto::getPartidaId).toList()).stream()
                .collect(Collectors.toMap(PartidaResultado::getId, Function.identity()));
        return mios.stream()
                .filter(m -> partidas.containsKey(m.getPartidaId()))
                .map(m -> new MiPartida(Resumen.de(partidas.get(m.getPartidaId())), m.getPosicion(), m.getPuntos(),
                        m.getAciertos()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SalonDeLaFama> salonDeLaFama(int limite) {
        return partidaRepository.salonDeLaFama(PageRequest.of(0, acotar(limite))).stream()
                .map(f -> new SalonDeLaFama((Long) f[0], (String) f[1], ((Number) f[2]).longValue(),
                        ((Number) f[3]).intValue()))
                .toList();
    }

    private int acotar(int limite) {
        return Math.max(1, Math.min(limite, LIMITE_MAXIMO));
    }
}
