package com.growlink.trivia.service;

import com.growlink.trivia.adapter.persistence.PartidaPuestoRepository;
import com.growlink.trivia.adapter.persistence.PartidaResultadoRepository;
import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.adapter.persistence.SalaTriviaRepository;
import com.growlink.trivia.application.JuegoService;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.application.UsuariosClient;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.EstadoSala;
import com.growlink.trivia.domain.PartidaPuesto;
import com.growlink.trivia.domain.PartidaResultado;
import com.growlink.trivia.domain.SalaTrivia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.growlink.trivia.RespuestasDePrueba.correcta;
import static com.growlink.trivia.RespuestasDePrueba.incorrecta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

// La prueba del profe: varias personas jugando a la vez. Aqui son 6 jugadores, con hilos reales, que contestan
// cada pregunta en el mismo instante durante una partida entera. Nada se pierde, nada se duplica y la partida
// termina una sola vez, con su acta completa.
@SpringBootTest
@ActiveProfiles("test")
class PartidaCompletaConcurrenteTest {

    private static final int PREGUNTAS = 10;

    @Autowired
    private SalaService salaService;
    @Autowired
    private JuegoService juegoService;
    @Autowired
    private SalaTriviaRepository salaRepository;
    @Autowired
    private SalaPreguntaRepository preguntas;
    @Autowired
    private PartidaResultadoRepository partidaRepository;
    @Autowired
    private PartidaPuestoRepository puestoRepository;

    @MockBean
    private UsuariosClient usuariosClient;

    @Test
    void seisJugadoresContestanTodasLasPreguntasALaVezYLaPartidaTerminaUnaSolaVez() throws Exception {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 801L, "Jugador 1", PREGUNTAS, 20);
        List<Long> jugadores = new ArrayList<>(List.of(801L));
        for (long id = 802; id <= 806; id++) {
            salaService.unirse(sala.getCodigo(), id, "Jugador " + (id - 800));
            jugadores.add(id);
        }
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        ExecutorService pool = Executors.newFixedThreadPool(jugadores.size());
        AtomicInteger errores = new AtomicInteger();
        try {
            for (int pregunta = 0; pregunta < PREGUNTAS; pregunta++) {
                final int indice = pregunta;
                CountDownLatch arrancar = new CountDownLatch(1);
                List<Future<?>> respuestas = new ArrayList<>();
                for (int j = 0; j < jugadores.size(); j++) {
                    final long usuarioId = jugadores.get(j);
                    // el jugador 1 acierta todo, el 2 acierta casi todo, los demas aciertan de manera alterna
                    final boolean acierta = j == 0 || (j == 1 && indice % 5 != 0) || (j > 1 && (indice + j) % 2 == 0);
                    respuestas.add(pool.submit(() -> {
                        arrancar.await();
                        try {
                            int opcion = acierta ? correcta(preguntas, sala, indice) : incorrecta(preguntas, sala, indice);
                            juegoService.responder(sala.getCodigo(), usuarioId, indice, opcion);
                        } catch (RuntimeException e) {
                            errores.incrementAndGet();
                        }
                        return null;
                    }));
                }
                arrancar.countDown();
                for (Future<?> f : respuestas) {
                    f.get(20, TimeUnit.SECONDS);
                }
            }
        } finally {
            pool.shutdown();
        }

        assertThat(errores.get()).isZero();
        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.FINALIZADA);

        // un acta, no dos, y con los seis jugadores
        assertThat(partidaRepository.findAll().stream().filter(p -> p.getSalaId().equals(sala.getId()))).hasSize(1);
        PartidaResultado acta = partidaRepository.findByCodigo(sala.getCodigo()).orElseThrow();
        assertThat(acta.getTotalJugadores()).isEqualTo(6);
        assertThat(acta.getGanadorUsuarioId()).isEqualTo(801L);

        List<PartidaPuesto> puestos = puestoRepository.findByPartidaIdOrderByPosicion(acta.getId());
        assertThat(puestos).hasSize(6);
        assertThat(puestos.stream().map(PartidaPuesto::getUsuarioId)).containsExactlyInAnyOrderElementsOf(jugadores);
        assertThat(puestos.get(0).getAciertos()).isEqualTo(PREGUNTAS);
        for (int i = 1; i < puestos.size(); i++) {
            assertThat(puestos.get(i).getPuntos()).isLessThanOrEqualTo(puestos.get(i - 1).getPuntos());
        }
        verify(usuariosClient, atLeastOnce()).registrarVictoria(anyLong());
    }
}
