package com.growlink.trivia.application;

import com.growlink.trivia.adapter.persistence.SalaParticipanteRepository;
import com.growlink.trivia.adapter.persistence.SalaTriviaRepository;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.EstadoSala;
import com.growlink.trivia.domain.SalaParticipante;
import com.growlink.trivia.domain.SalaTrivia;
import com.growlink.trivia.domain.TipoEvento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

@Service
public class SalaService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin O, I, 0, 1 para que no se confundan al leerlo
    private static final SecureRandom RANDOM = new SecureRandom();

    // valores fijos que pide HU-16, nada de numeros sueltos
    private static final Set<Integer> PREGUNTAS_VALIDAS = Set.of(5, 10, 15);
    private static final Set<Integer> DURACIONES_VALIDAS = Set.of(10, 15, 20);

    private final SalaTriviaRepository salaRepository;
    private final SalaParticipanteRepository participanteRepository;

    private final MetricaService metricaService;

    public SalaService(SalaTriviaRepository salaRepository, SalaParticipanteRepository participanteRepository,
                       MetricaService metricaService) {
        this.salaRepository = salaRepository;
        this.participanteRepository = participanteRepository;
        this.metricaService = metricaService;
    }

    @Transactional
    public SalaTrivia crear(Categoria categoria, Long hostUsuarioId, String hostNombre, int numPreguntas, int duracionSegundos) {
        if (!PREGUNTAS_VALIDAS.contains(numPreguntas)) {
            throw new IllegalArgumentException("numPreguntas debe ser 5, 10 o 15");
        }
        if (!DURACIONES_VALIDAS.contains(duracionSegundos)) {
            throw new IllegalArgumentException("duracionSegundos debe ser 10, 15 o 20");
        }
        String codigo = generarCodigoUnico();
        SalaTrivia sala = salaRepository.save(new SalaTrivia(codigo, categoria, hostUsuarioId, numPreguntas, duracionSegundos));
        // el host tambien queda como participante, ya esta en la sala de espera desde que la crea
        participanteRepository.save(new SalaParticipante(sala.getId(), hostUsuarioId, hostNombre));
        metricaService.registrar(TipoEvento.SALA_CREADA, sala.getId());
        return sala;
    }

    public SalaTrivia obtenerPorCodigo(String codigo) {
        return salaRepository.findByCodigo(codigo).orElseThrow(() -> new SalaNoEncontradaException(codigo));
    }

    @Transactional
    public SalaParticipante unirse(String codigo, Long usuarioId, String nombre) {
        SalaTrivia sala = obtenerPorCodigo(codigo);
        if (sala.getEstado() != EstadoSala.ESPERANDO) {
            throw new SalaYaEmpezoException(codigo);
        }
        SalaParticipante participante = participanteRepository.save(new SalaParticipante(sala.getId(), usuarioId, nombre));
        metricaService.registrar(TipoEvento.PARTICIPANTE_UNIDO, sala.getId());
        return participante;
    }

    public List<SalaParticipante> listarParticipantes(Long salaId) {
        return participanteRepository.findBySalaId(salaId);
    }

    @Transactional
    public void iniciar(String codigo) {
        SalaTrivia sala = obtenerPorCodigo(codigo);
        long total = participanteRepository.countBySalaId(sala.getId());
        if (total < 2) {
            throw new FaltanParticipantesException();
        }
        int filasActualizadas = salaRepository.intentarIniciar(sala.getId());
        if (filasActualizadas == 0) {
            // alguien mas ya la inicio justo antes, no pasa nada raro
            throw new SalaYaEmpezoException(codigo);
        }
    }

    // HU bono: al terminar la partida, cualquier jugador puede proponer revancha.
    // El lock de fila sobre la sala vieja (mismo truco que el de responder en JuegoService)
    // sirve para que, si dos jugadores le dan al boton casi al mismo tiempo, el segundo
    // espere a que el primero termine su transaccion antes de decidir que hacer: si al
    // despertar ya ve revanchaCodigo puesto, simplemente devuelve esa sala y no crea otra.
    @Transactional
    public SalaTrivia crearRevancha(String codigoViejo, Long usuarioId, String nombre) {
        SalaTrivia salaVieja = salaRepository.buscarConLockPorCodigo(codigoViejo)
                .orElseThrow(() -> new SalaNoEncontradaException(codigoViejo));
        if (salaVieja.getEstado() != EstadoSala.FINALIZADA) {
            throw new RevanchaNoDisponibleException(codigoViejo);
        }
        if (!participanteRepository.existsBySalaIdAndUsuarioId(salaVieja.getId(), usuarioId)) {
            throw new NoEsParticipanteException(codigoViejo);
        }

        if (salaVieja.getRevanchaCodigo() != null) {
            // alguien mas ya la propuso justo antes (o es un segundo clic del mismo
            // jugador); se devuelve la misma sala para que no quede ninguna huerfana
            return obtenerPorCodigo(salaVieja.getRevanchaCodigo());
        }

        SalaTrivia nueva = crear(salaVieja.getCategoria(), usuarioId, nombre,
                salaVieja.getNumPreguntas(), salaVieja.getDuracionSegundos());
        salaVieja.fijarRevanchaCodigo(nueva.getCodigo());
        salaRepository.save(salaVieja);
        return nueva;
    }

    private String generarCodigoUnico() {
        String codigo;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
            }
            codigo = sb.toString();
        } while (salaRepository.findByCodigo(codigo).isPresent());
        return codigo;
    }
}
