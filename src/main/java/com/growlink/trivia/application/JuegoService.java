package com.growlink.trivia.application;

import com.growlink.trivia.adapter.persistence.*;
import com.growlink.trivia.adapter.ws.dto.LeaderboardBroadcast;
import com.growlink.trivia.adapter.ws.dto.LeaderboardEntry;
import com.growlink.trivia.adapter.ws.dto.PreguntaBroadcast;
import com.growlink.trivia.adapter.ws.dto.ResultadosFinalesBroadcast;
import com.growlink.trivia.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class JuegoService {

    private final SalaTriviaRepository salaRepository;
    private final SalaParticipanteRepository participanteRepository;
    private final PreguntaBancoRepository bancoRepository;
    private final SalaPreguntaRepository salaPreguntaRepository;
    private final RespuestaSalaRepository respuestaRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public JuegoService(SalaTriviaRepository salaRepository, SalaParticipanteRepository participanteRepository,
                         PreguntaBancoRepository bancoRepository, SalaPreguntaRepository salaPreguntaRepository,
                         RespuestaSalaRepository respuestaRepository, SimpMessagingTemplate messagingTemplate) {
        this.salaRepository = salaRepository;
        this.participanteRepository = participanteRepository;
        this.bancoRepository = bancoRepository;
        this.salaPreguntaRepository = salaPreguntaRepository;
        this.respuestaRepository = respuestaRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // se llama justo despues de que la sala pasa a EN_CURSO
    // elige las preguntas de una vez y manda la primera
    @Transactional
    public void empezarJuego(SalaTrivia sala) {
        List<PreguntaBanco> disponibles = new ArrayList<>(bancoRepository.findByCategoria(sala.getCategoria()));
        if (disponibles.size() < sala.getNumPreguntas()) {
            throw new NoHayPreguntasSuficientesException(sala.getCategoria().name(), sala.getNumPreguntas(), disponibles.size());
        }
        Collections.shuffle(disponibles);

        for (int i = 0; i < sala.getNumPreguntas(); i++) {
            PreguntaBanco elegida = disponibles.get(i);
            SalaPregunta salaPregunta = new SalaPregunta(sala.getId(), i, elegida.getTexto(),
                    elegida.getOpciones(), elegida.getRespuestaCorrecta());
            salaPreguntaRepository.save(salaPregunta);
        }

        activarPregunta(sala, 0);
    }

    @Transactional
    public void responder(String codigo, Long usuarioId, int indice, int opcionElegida) {
        SalaTrivia sala = salaRepository.findByCodigo(codigo).orElseThrow(() -> new SalaNoEncontradaException(codigo));
        if (sala.getEstado() != EstadoSala.EN_CURSO) {
            throw new JuegoNoDisponibleException("La sala " + codigo + " no esta en curso");
        }

        SalaPregunta referencia = salaPreguntaRepository.findBySalaIdAndIndice(sala.getId(), indice)
                .orElseThrow(() -> new JuegoNoDisponibleException("No existe la pregunta " + indice + " en esta sala"));

        // el lock se pide aqui, con esto la fila queda tomada hasta que termine
        // esta transaccion, y la siguiente respuesta que llegue para esta misma
        // pregunta tiene que esperar a que esta termine antes de contar cuantos
        // ya respondieron
        SalaPregunta pregunta = salaPreguntaRepository.buscarConLockPorId(referencia.getId()).orElseThrow();

        boolean esCorrecta = opcionElegida == pregunta.getRespuestaCorrecta();
        int puntos = esCorrecta ? calcularPuntos(pregunta, sala.getDuracionSegundos()) : 0;

        // si ya habia una respuesta de este usuario para esta pregunta, la
        // restriccion unica de la tabla la rechaza aqui mismo
        respuestaRepository.save(new RespuestaSala(pregunta.getId(), usuarioId, opcionElegida, esCorrecta, puntos));

        if (esCorrecta) {
            salaPreguntaRepository.intentarMarcarPrimerAcertante(pregunta.getId(), usuarioId);
        }

        long totalRespuestas = respuestaRepository.countBySalaPreguntaId(pregunta.getId());
        long totalParticipantes = participanteRepository.countBySalaId(sala.getId());

        if (totalRespuestas < totalParticipantes) {
            return; // todavia faltan jugadores por responder esta pregunta
        }

        List<LeaderboardEntry> ranking = calcularLeaderboard(sala.getId());
        messagingTemplate.convertAndSend("/topic/salas/" + codigo, LeaderboardBroadcast.of(codigo, ranking));

        boolean eraLaUltima = indice == sala.getNumPreguntas() - 1;
        if (eraLaUltima) {
            finalizarJuego(sala, ranking);
        } else {
            activarPregunta(sala, indice + 1);
        }
    }

    private void activarPregunta(SalaTrivia sala, int indice) {
        SalaPregunta pregunta = salaPreguntaRepository.findBySalaIdAndIndice(sala.getId(), indice).orElseThrow();
        pregunta.marcarEnviada();
        salaPreguntaRepository.save(pregunta);
        messagingTemplate.convertAndSend("/topic/salas/" + sala.getCodigo(),
                PreguntaBroadcast.of(sala.getCodigo(), indice, sala.getNumPreguntas(), pregunta.getTexto(),
                        pregunta.getOpciones(), sala.getDuracionSegundos(), pregunta.getEnviadaEn().toEpochMilli()));
    }

    private void finalizarJuego(SalaTrivia sala, List<LeaderboardEntry> rankingFinal) {
        salaRepository.intentarFinalizar(sala.getId());
        Long ganadorUsuarioId = rankingFinal.isEmpty() ? null : rankingFinal.get(0).usuarioId();
        messagingTemplate.convertAndSend("/topic/salas/" + sala.getCodigo(),
                ResultadosFinalesBroadcast.of(sala.getCodigo(), rankingFinal, ganadorUsuarioId));
    }

    private List<LeaderboardEntry> calcularLeaderboard(Long salaId) {
        List<Long> preguntaIds = salaPreguntaRepository.findBySalaIdOrderByIndice(salaId).stream()
                .map(SalaPregunta::getId).toList();
        List<RespuestaSala> respuestas = respuestaRepository.findBySalaPreguntaIdIn(preguntaIds);

        Map<Long, Integer> puntosPorUsuario = new HashMap<>();
        for (RespuestaSala r : respuestas) {
            puntosPorUsuario.merge(r.getUsuarioId(), r.getPuntos(), Integer::sum);
        }

        Map<Long, String> nombresPorUsuario = new HashMap<>();
        for (SalaParticipante p : participanteRepository.findBySalaId(salaId)) {
            nombresPorUsuario.put(p.getUsuarioId(), p.getNombre());
            puntosPorUsuario.putIfAbsent(p.getUsuarioId(), 0); // el que no respondio nada tambien sale con 0
        }

        return puntosPorUsuario.entrySet().stream()
                .map(e -> new LeaderboardEntry(e.getKey(), nombresPorUsuario.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparingInt(LeaderboardEntry::puntos).reversed())
                .toList();
    }

    // 100 puntos si responde de inmediato, y baja de forma pareja hasta 0
    // en el instante justo en que se agota el tiempo
    private int calcularPuntos(SalaPregunta pregunta, int duracionSegundos) {
        long transcurridoMs = System.currentTimeMillis() - pregunta.getEnviadaEn().toEpochMilli();
        long duracionMs = duracionSegundos * 1000L;
        double fraccionRestante = Math.max(0, (duracionMs - transcurridoMs) / (double) duracionMs);
        return (int) Math.round(100 * fraccionRestante);
    }
}
