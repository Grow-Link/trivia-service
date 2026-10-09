package com.growlink.trivia.application;

import com.growlink.trivia.adapter.persistence.RetoRepository;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.EstadoReto;
import com.growlink.trivia.domain.EstadoSala;
import com.growlink.trivia.domain.Reto;
import com.growlink.trivia.domain.SalaTrivia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// "Te reto a una trivia": retar crea la sala (el retador queda esperando adentro) y deja una invitacion
// pendiente para la otra persona. Quien recibe el reto lo ve en su bandeja, y al aceptar entra a esa sala.
@Service
public class RetoService {

    static final int MAXIMO_MENSAJE = 140;
    static final String MENSAJE_POR_DEFECTO = "¡Te reto a una trivia!";

    private final RetoRepository retoRepository;
    private final SalaService salaService;
    private final Duration vigencia;

    public RetoService(RetoRepository retoRepository, SalaService salaService,
                       @Value("${trivia.retos.vigencia-minutos:5}") long vigenciaMinutos) {
        this.retoRepository = retoRepository;
        this.salaService = salaService;
        this.vigencia = Duration.ofMinutes(vigenciaMinutos);
    }

    @Transactional
    public Reto retar(Long retadorId, String retadorNombre, Long retadoId, String retadoNombre, Categoria categoria,
                      int numPreguntas, int duracionSegundos, String mensaje) {
        if (retadorId.equals(retadoId)) {
            throw new IllegalArgumentException("No puedes retarte a ti mismo");
        }
        String texto = mensaje == null || mensaje.isBlank() ? MENSAJE_POR_DEFECTO : mensaje.trim();
        if (texto.length() > MAXIMO_MENSAJE) {
            throw new IllegalArgumentException("El mensaje no puede pasar de " + MAXIMO_MENSAJE + " caracteres");
        }
        Instant ahora = Instant.now();
        // evita llenarle la bandeja a alguien: mientras el reto anterior siga pendiente no se manda otro igual
        if (retoRepository.existsByRetadorUsuarioIdAndRetadoUsuarioIdAndEstadoAndExpiraEnAfter(retadorId, retadoId,
                EstadoReto.PENDIENTE, ahora)) {
            throw new JuegoNoDisponibleException("Ya le enviaste un reto a " + retadoNombre
                    + " y todavia no responde. Espera a que lo acepte, lo rechace o venza");
        }
        SalaTrivia sala = salaService.crear(categoria, retadorId, retadorNombre, numPreguntas, duracionSegundos);
        return retoRepository.save(new Reto(retadorId, retadorNombre, retadoId, retadoNombre, sala, texto,
                ahora.plus(vigencia)));
    }

    // la bandeja de quien recibe retos: solo los pendientes que todavia no vencen
    @Transactional(readOnly = true)
    public List<Reto> recibidos(Long usuarioId) {
        return retoRepository.findByRetadoUsuarioIdAndEstadoAndExpiraEnAfterOrderByCreadoEnDesc(usuarioId,
                EstadoReto.PENDIENTE, Instant.now());
    }

    // lo que yo envie, para ver si ya me aceptaron, rechazaron o vencio
    @Transactional(readOnly = true)
    public List<Reto> enviados(Long usuarioId) {
        return retoRepository.findTop10ByRetadorUsuarioIdOrderByCreadoEnDesc(usuarioId);
    }

    @Transactional
    public Reto aceptar(Long retoId, Long usuarioId) {
        Reto reto = propio(retoId, usuarioId);
        SalaTrivia sala = salaService.obtenerPorCodigo(reto.getCodigoSala());
        if (sala.getEstado() != EstadoSala.ESPERANDO) {
            throw new SalaYaEmpezoException(sala.getCodigo());
        }
        responder(retoId, usuarioId, EstadoReto.ACEPTADO);
        return retoRepository.findById(retoId).orElseThrow(() -> new RetoNoEncontradoException(retoId));
    }

    @Transactional
    public Reto rechazar(Long retoId, Long usuarioId) {
        propio(retoId, usuarioId);
        responder(retoId, usuarioId, EstadoReto.RECHAZADO);
        return retoRepository.findById(retoId).orElseThrow(() -> new RetoNoEncontradoException(retoId));
    }

    private void responder(Long retoId, Long usuarioId, EstadoReto nuevo) {
        if (retoRepository.responder(retoId, usuarioId, nuevo, Instant.now()) == 0) {
            throw new JuegoNoDisponibleException("Este reto ya no esta disponible: ya lo respondiste o se venció");
        }
    }

    // un reto solo lo responde la persona retada; para cualquier otra es como si no existiera
    private Reto propio(Long retoId, Long usuarioId) {
        Reto reto = retoRepository.findById(retoId).orElseThrow(() -> new RetoNoEncontradoException(retoId));
        if (!reto.getRetadoUsuarioId().equals(usuarioId)) {
            throw new RetoNoEncontradoException(retoId);
        }
        return reto;
    }
}
