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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    // mismo lock de fila que usa crearRevancha. Lo usa RetoService.aceptar para que, si el host
    // le da a "iniciar" justo cuando el retado acepta, una de las dos transacciones espere a que
    // la otra termine en vez de que ambas lean "ESPERANDO" y las dos sigan de largo
    public SalaTrivia obtenerConLockPorCodigo(String codigo) {
        return salaRepository.buscarConLockPorCodigo(codigo).orElseThrow(() -> new SalaNoEncontradaException(codigo));
    }

    // El lock de fila (el mismo que usa salir/salirPorDesconexion sobre la misma sala) es lo que
    // permite que ultimaConexion sea confiable entre replicas: cualquier "unirse" y cualquier
    // evaluacion del margen de gracia para esa misma sala quedan en fila una tras otra, nunca se
    // entrelazan, sin importar en que replica haya llegado cada mensaje
    @Transactional
    public SalaParticipante unirse(String codigo, Long usuarioId, String nombre) {
        SalaTrivia sala = obtenerConLockPorCodigo(codigo);

        // reconexion (cerro la pestana o se le fue la red, y volvio antes de que se cumpliera el
        // margen de gracia): ya era participante, no hay nada que crear. Si se habia marcado
        // inactivo por la desconexion (no por un "salir" explicito) y la partida sigue en curso,
        // esta reconexion lo reactiva; si salio a proposito, se queda inactivo tal cual estaba
        Optional<SalaParticipante> existente = participanteRepository.findBySalaIdAndUsuarioId(sala.getId(), usuarioId);
        if (existente.isPresent()) {
            SalaParticipante participante = existente.get();
            participante.registrarConexion();
            if (sala.getEstado() == EstadoSala.EN_CURSO && !participante.isActivo() && !participante.isSalidaVoluntaria()) {
                participante.reactivar();
            }
            participanteRepository.save(participante);
            return participante;
        }

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

    // lo que le hace falta al controlador de WebSocket para armar el JUGADOR_SALIO y decidir si
    // tiene que avisar el estado de la sala de espera o pedirle a JuegoService que cierre la partida.
    // null significa que no habia nada que hacer (ya no era participante, ya habia salido, o la
    // sala ya termino), asi "salir" queda siendo idempotente sin que el llamador tenga que adivinar
    public record SalidaResultado(Long usuarioId, String nombre, Long nuevoHostUsuarioId, EstadoSala estado,
                                  boolean terminarPartida) {
    }

    // HU salir-de-sala, mensaje explicito "/salir". El lock de fila sobre la sala (mismo que usa
    // crearRevancha) sirve para que, si dos jugadores se van casi al mismo tiempo mientras la sala
    // sigue en ESPERA, el traspaso de host y el conteo de "ya no queda nadie" se decidan uno
    // despues del otro y no con datos viejos. Una salida explicita siempre se aplica: a diferencia
    // de salirPorDesconexion, aqui no hay margen de gracia que pueda deshacerla
    @Transactional
    public SalidaResultado salir(String codigo, Long usuarioId) {
        SalaTrivia sala = obtenerConLockPorCodigo(codigo);
        SalaParticipante participante = participanteRepository.findBySalaIdAndUsuarioId(sala.getId(), usuarioId).orElse(null);
        return aplicarSalida(sala, participante, usuarioId, true);
    }

    // La llama SalaWebSocketController cuando se cumple el margen de gracia despues de que se cayo
    // una conexion. ultimaConexionDeLaSesionQueSeFue es el valor exacto que tenia
    // SalaParticipante.ultimaConexion cuando ESA sesion hizo "unirse" (lo captura el controlador ahi
    // mismo, no es "la hora en que se detecto la caida"): si para cuando se evalua esto el valor en la
    // base de datos sigue siendo ese mismo, nadie volvio a conectarse y la desconexion cuenta como
    // salida; si es mas nuevo, alguien ya se reconecto (a esta replica o a cualquier otra, todas
    // comparten la misma base de datos) y no hay nada que hacer.
    //
    // A proposito NO se compara contra "la hora en que este proceso detecto la desconexion": bajo
    // carga, el evento de desconexion del WebSocket puede tardar en procesarse mas que la reconexion
    // misma, y comparar contra esa hora de deteccion (en vez de contra la conexion de la sesion que se
    // fue) puede invertir el orden y tratar una reconexion legitima como si no hubiera pasado. Esto se
    // encontro en CI: la reconexion llegaba a tiempo pero el chequeo igual disparaba la salida.
    @Transactional
    public SalidaResultado salirPorDesconexion(String codigo, Long usuarioId, Instant ultimaConexionDeLaSesionQueSeFue) {
        SalaTrivia sala = obtenerConLockPorCodigo(codigo);
        SalaParticipante participante = participanteRepository.findBySalaIdAndUsuarioId(sala.getId(), usuarioId).orElse(null);
        if (participante != null && participante.getUltimaConexion().isAfter(ultimaConexionDeLaSesionQueSeFue)) {
            return null; // ya hay una conexion mas nueva que la de la sesion que se cayo
        }
        return aplicarSalida(sala, participante, usuarioId, false);
    }

    // voluntaria distingue un "salir" explicito de una desconexion que agoto su margen de gracia:
    // solo la primera deja al participante marcado para que una reconexion posterior ya NO lo
    // reactive dentro de una partida en curso (ver unirse())
    private SalidaResultado aplicarSalida(SalaTrivia sala, SalaParticipante participante, Long usuarioId, boolean voluntaria) {
        if (sala.getEstado() == EstadoSala.ESPERANDO) {
            if (participante == null) {
                return null; // ya no estaba (p.ej. segunda vez que manda "salir")
            }
            participanteRepository.delete(participante);

            Long nuevoHost = null;
            if (sala.getHostUsuarioId().equals(usuarioId)) {
                nuevoHost = participanteRepository.findFirstBySalaIdOrderByUnidoEnAsc(sala.getId())
                        .map(SalaParticipante::getUsuarioId).orElse(null);
                if (nuevoHost != null) {
                    sala.fijarHostUsuarioId(nuevoHost);
                }
            }
            if (participanteRepository.countBySalaId(sala.getId()) == 0) {
                sala.marcarCancelada();
            }
            salaRepository.save(sala);
            return new SalidaResultado(usuarioId, participante.getNombre(), nuevoHost, sala.getEstado(), false);
        }

        if (sala.getEstado() == EstadoSala.EN_CURSO) {
            if (participante == null || !participante.isActivo()) {
                return null; // ya no estaba o ya habia salido antes, idempotente
            }
            participante.marcarInactivo(voluntaria);
            participanteRepository.save(participante);
            long activos = participanteRepository.countBySalaIdAndActivoTrue(sala.getId());
            return new SalidaResultado(usuarioId, participante.getNombre(), null, sala.getEstado(), activos < 2);
        }

        // FINALIZADA o CANCELADA: salir no hace nada especial, no rompe la revancha ni el historial
        return null;
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
