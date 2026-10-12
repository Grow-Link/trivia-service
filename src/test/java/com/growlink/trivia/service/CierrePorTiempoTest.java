package com.growlink.trivia.service;

import com.growlink.trivia.adapter.persistence.PartidaResultadoRepository;
import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.adapter.persistence.SalaTriviaRepository;
import com.growlink.trivia.application.CierrePreguntaScheduler;
import com.growlink.trivia.application.JuegoNoDisponibleException;
import com.growlink.trivia.application.JuegoService;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.application.UsuariosClient;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.EstadoSala;
import com.growlink.trivia.domain.PartidaResultado;
import com.growlink.trivia.domain.SalaPregunta;
import com.growlink.trivia.domain.SalaTrivia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.growlink.trivia.RespuestasDePrueba.correcta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Cierre por tiempo: si una pregunta vence (se acaba su duracion + el margen de tolerancia) y
// no se habia cerrado por otro camino, el barredor (CierrePreguntaScheduler, @Scheduled) la
// cierra igual que si todos hubieran respondido. application-test.yml deja el margen y el
// intervalo del barredor en milisegundos para que estas pruebas no tengan que esperar segundos
// reales; en vez de eso, "vencemos" una pregunta adelantando su enviadaEn hacia el pasado.
@SpringBootTest
@ActiveProfiles("test")
class CierrePorTiempoTest {

    @Autowired
    private SalaService salaService;
    @Autowired
    private JuegoService juegoService;
    @Autowired
    private CierrePreguntaScheduler scheduler;
    @Autowired
    private SalaTriviaRepository salaRepository;
    @Autowired
    private SalaPreguntaRepository salaPreguntaRepository;
    @Autowired
    private PartidaResultadoRepository partidaRepository;

    @MockBean
    private UsuariosClient usuariosClient;

    @Test
    void siNadieResponde_elBarredorAvanzaLaPartidaHastaTerminar() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        for (int i = 0; i < 5; i++) {
            vencerPreguntaActual(sala, i);
            scheduler.cerrarPreguntasVencidas();
        }

        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.FINALIZADA);
        for (int i = 0; i < 5; i++) {
            assertThat(salaPreguntaRepository.findBySalaIdAndIndice(sala.getId(), i).orElseThrow().isCerrada()).isTrue();
        }
    }

    @Test
    void siSoloAlgunosResponden_elBarredorAvanzaIgual() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.unirse(sala.getCodigo(), 3L, "Carla");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        for (int i = 0; i < 5; i++) {
            // solo Ana responde, Beto y Carla nunca contestan esta pregunta
            juegoService.responder(sala.getCodigo(), 1L, i, correcta(salaPreguntaRepository, sala, i));
            vencerPreguntaActual(sala, i);
            scheduler.cerrarPreguntasVencidas();
        }

        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.FINALIZADA);
        PartidaResultado acta = partidaRepository.findByCodigo(sala.getCodigo()).orElseThrow();
        assertThat(acta.getGanadorUsuarioId()).isEqualTo(1L);
        assertThat(acta.getTotalJugadores()).isEqualTo(3);
        verify(usuariosClient, never()).registrarVictoria(2L);
        verify(usuariosClient, never()).registrarVictoria(3L);
    }

    @Test
    void siTodosResponden_elBarredorYaNoTieneNadaQueCerrar() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        for (int i = 0; i < 5; i++) {
            juegoService.responder(sala.getCodigo(), 1L, i, correcta(salaPreguntaRepository, sala, i));
            juegoService.responder(sala.getCodigo(), 2L, i, correcta(salaPreguntaRepository, sala, i));
            // la pregunta ya quedo cerrada por responder(); el barredor no debe romper nada
            scheduler.cerrarPreguntasVencidas();
        }

        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.FINALIZADA);
        assertThat(partidaRepository.findAll().stream().filter(p -> p.getSalaId().equals(sala.getId()))).hasSize(1);
    }

    @Test
    void unaRespuestaTardiaSeRechaza() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        vencerPreguntaActual(sala, 0);
        scheduler.cerrarPreguntasVencidas();

        assertThatThrownBy(() -> juegoService.responder(sala.getCodigo(), 1L, 0, correcta(salaPreguntaRepository, sala, 0)))
                .isInstanceOf(JuegoNoDisponibleException.class)
                .hasMessageContaining("tiempo");
    }

    @Test
    void siNadieSumaPuntosPorTiempoNoHayGanadorNiTriviaGanada() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        for (int i = 0; i < 5; i++) {
            vencerPreguntaActual(sala, i);
            scheduler.cerrarPreguntasVencidas();
        }

        PartidaResultado acta = partidaRepository.findByCodigo(sala.getCodigo()).orElseThrow();
        assertThat(acta.getGanadorUsuarioId()).isNull();
        assertThat(acta.isEmpate()).isFalse();
        verify(usuariosClient, never()).registrarVictoria(anyLong());
    }

    @Test
    void laUltimaRespuestaCompiteConElCierrePorTiempoYLaPartidaAvanzaUnaSolaVez() throws Exception {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        // las primeras 4 preguntas se juegan normal
        for (int i = 0; i < 4; i++) {
            juegoService.responder(sala.getCodigo(), 1L, i, correcta(salaPreguntaRepository, sala, i));
            juegoService.responder(sala.getCodigo(), 2L, i, correcta(salaPreguntaRepository, sala, i));
        }

        // en la ultima, Ana ya respondio, falta Beto. Se vence el tiempo justo ahora
        juegoService.responder(sala.getCodigo(), 1L, 4, correcta(salaPreguntaRepository, sala, 4));
        vencerPreguntaActual(sala, 4);
        Long preguntaId = salaPreguntaRepository.findBySalaIdAndIndice(sala.getId(), 4).orElseThrow().getId();

        // la respuesta de Beto compite con dos "replicas" barriendo al mismo tiempo
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch arrancar = new CountDownLatch(1);
        AtomicInteger erroresRespuesta = new AtomicInteger();
        List<Future<?>> tareas = new ArrayList<>();
        tareas.add(pool.submit(() -> {
            arrancar.await();
            try {
                juegoService.responder(sala.getCodigo(), 2L, 4, correcta(salaPreguntaRepository, sala, 4));
            } catch (RuntimeException e) {
                erroresRespuesta.incrementAndGet();
            }
            return null;
        }));
        for (int i = 0; i < 2; i++) {
            tareas.add(pool.submit(() -> {
                arrancar.await();
                juegoService.intentarCerrarPorTiempo(preguntaId);
                return null;
            }));
        }
        arrancar.countDown();
        for (Future<?> f : tareas) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // gane la respuesta o gane el barredor, la pregunta se cierra y la partida avanza una
        // sola vez (si Beto perdio la carrera, su respuesta se rechazo con "se acabo el tiempo")
        assertThat(erroresRespuesta.get()).isLessThanOrEqualTo(1);
        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.FINALIZADA);
        assertThat(partidaRepository.findAll().stream().filter(p -> p.getSalaId().equals(sala.getId()))).hasSize(1);
        assertThat(salaPreguntaRepository.findBySalaIdAndIndice(sala.getId(), 4).orElseThrow().isCerrada()).isTrue();
    }

    @Test
    void elBarredorRealDeFondoCierraLasPreguntasSinQueNadieLoLlameAMano() throws Exception {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        // nadie responde nunca, y nunca se llama al scheduler a mano: solo se vence cada
        // pregunta y se espera a que el @Scheduled real (cada 100ms, ver application-test.yml)
        // la descubra y la cierre por su cuenta
        for (int i = 0; i < 5; i++) {
            final int indice = i;
            vencerPreguntaActual(sala, indice);
            esperarHasta(() -> salaPreguntaRepository.findBySalaIdAndIndice(sala.getId(), indice).orElseThrow().isCerrada());
        }

        esperarHasta(() -> salaRepository.findById(sala.getId()).orElseThrow().getEstado() == EstadoSala.FINALIZADA);
    }

    private void esperarHasta(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("el barredor de fondo no cerro la pregunta a tiempo");
            }
            Thread.sleep(50);
        }
    }

    // adelanta hacia el pasado la hora de envio de la pregunta actual, lo justo para que ya
    // cuente como vencida (duracion de la sala + un colchon), sin tener que esperar de verdad
    private void vencerPreguntaActual(SalaTrivia sala, int indice) {
        SalaPregunta pregunta = salaPreguntaRepository.findBySalaIdAndIndice(sala.getId(), indice).orElseThrow();
        pregunta.marcarEnviada(Instant.now().minusSeconds(sala.getDuracionSegundos() + 5));
        salaPreguntaRepository.save(pregunta);
    }
}
