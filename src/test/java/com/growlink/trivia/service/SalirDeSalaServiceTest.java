package com.growlink.trivia.service;

import com.growlink.trivia.adapter.persistence.PartidaResultadoRepository;
import com.growlink.trivia.adapter.persistence.SalaParticipanteRepository;
import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.adapter.persistence.SalaTriviaRepository;
import com.growlink.trivia.application.JuegoNoDisponibleException;
import com.growlink.trivia.application.JuegoService;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.application.UsuariosClient;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.EstadoSala;
import com.growlink.trivia.domain.PartidaResultado;
import com.growlink.trivia.domain.SalaParticipante;
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

import static com.growlink.trivia.RespuestasDePrueba.correcta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// HU salir-de-sala. La parte de los broadcasts (JUGADOR_SALIO, SALA_UPDATE y la gracia por
// desconexion) se prueba en SalirDeSalaWebSocketIntegrationTest; aqui se prueba la logica de
// SalaService.salir() y JuegoService directamente, igual que CierrePorTiempoTest prueba el
// barredor sin pasar por WebSocket.
@SpringBootTest
@ActiveProfiles("test")
class SalirDeSalaServiceTest {

    @Autowired
    private SalaService salaService;
    @Autowired
    private JuegoService juegoService;
    @Autowired
    private SalaTriviaRepository salaRepository;
    @Autowired
    private SalaParticipanteRepository participanteRepository;
    @Autowired
    private SalaPreguntaRepository salaPreguntaRepository;
    @Autowired
    private PartidaResultadoRepository partidaRepository;

    @MockBean
    private UsuariosClient usuariosClient;

    @Test
    void salirEnEspera_borraAlParticipanteYNoTocaElHost() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");

        SalaService.SalidaResultado resultado = salaService.salir(sala.getCodigo(), 2L);

        assertThat(resultado).isNotNull();
        assertThat(resultado.nombre()).isEqualTo("Beto");
        assertThat(resultado.nuevoHostUsuarioId()).isNull();
        assertThat(resultado.estado()).isEqualTo(EstadoSala.ESPERANDO);
        assertThat(resultado.terminarPartida()).isFalse();
        assertThat(participanteRepository.existsBySalaIdAndUsuarioId(sala.getId(), 2L)).isFalse();
        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getHostUsuarioId()).isEqualTo(1L);
    }

    @Test
    void salirDelHostEnEspera_pasaElHostAlParticipanteMasAntiguo() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.unirse(sala.getCodigo(), 3L, "Carla");

        SalaService.SalidaResultado resultado = salaService.salir(sala.getCodigo(), 1L);

        assertThat(resultado.nuevoHostUsuarioId()).isEqualTo(2L); // Beto se unio antes que Carla
        assertThat(resultado.estado()).isEqualTo(EstadoSala.ESPERANDO);
        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getHostUsuarioId()).isEqualTo(2L);
    }

    @Test
    void salirDelUltimoParticipanteEnEspera_cancelaLaSala() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);

        SalaService.SalidaResultado resultado = salaService.salir(sala.getCodigo(), 1L);

        assertThat(resultado.nuevoHostUsuarioId()).isNull();
        assertThat(resultado.estado()).isEqualTo(EstadoSala.CANCELADA);
        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.CANCELADA);
    }

    @Test
    void salirDosVeces_esIdempotente() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");

        assertThat(salaService.salir(sala.getCodigo(), 2L)).isNotNull();
        assertThat(salaService.salir(sala.getCodigo(), 2L)).isNull(); // ya no estaba, no explota
    }

    @Test
    void salirEnJuego_marcaInactivoPeroNoBorraLaFilaDelParticipante() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.unirse(sala.getCodigo(), 3L, "Carla");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        SalaService.SalidaResultado resultado = salaService.salir(sala.getCodigo(), 3L);

        assertThat(resultado.estado()).isEqualTo(EstadoSala.EN_CURSO);
        assertThat(resultado.terminarPartida()).isFalse(); // todavia quedan Ana y Beto, 2 activos
        assertThat(participanteRepository.existsBySalaIdAndUsuarioId(sala.getId(), 3L)).isTrue(); // no se borro
        assertThat(participanteRepository.findBySalaIdAndUsuarioId(sala.getId(), 3L).orElseThrow().isActivo()).isFalse();
    }

    @Test
    void salirEnJuegoConTresJugadores_laPartidaSigueSinEsperarAlQueSalio() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.unirse(sala.getCodigo(), 3L, "Carla");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        salaService.salir(sala.getCodigo(), 3L); // Carla se va antes de responder nada

        // solo responden Ana y Beto (los 2 activos); la pregunta se cierra sin esperar a Carla
        for (int i = 0; i < 5; i++) {
            juegoService.responder(sala.getCodigo(), 1L, i, correcta(salaPreguntaRepository, sala, i));
            juegoService.responder(sala.getCodigo(), 2L, i, correcta(salaPreguntaRepository, sala, i));
            assertThat(salaPreguntaRepository.findBySalaIdAndIndice(sala.getId(), i).orElseThrow().isCerrada()).isTrue();
        }

        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.FINALIZADA);
        PartidaResultado acta = partidaRepository.findByCodigo(sala.getCodigo()).orElseThrow();
        assertThat(acta.getGanadorUsuarioId()).isIn(1L, 2L); // Carla no puede ganar, ya salio
        assertThat(acta.getTotalJugadores()).isEqualTo(3); // pero su fila sigue contando para el historial
    }

    @Test
    void quedarConUnSoloActivo_terminaLaPartidaConAbandonoYSinGanador() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        juegoService.responder(sala.getCodigo(), 1L, 0, correcta(salaPreguntaRepository, sala, 0));

        SalaService.SalidaResultado resultado = salaService.salir(sala.getCodigo(), 2L);
        assertThat(resultado.terminarPartida()).isTrue(); // solo queda Ana activa
        juegoService.finalizarPorAbandono(sala.getCodigo());

        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.FINALIZADA);
        PartidaResultado acta = partidaRepository.findByCodigo(sala.getCodigo()).orElseThrow();
        assertThat(acta.getGanadorUsuarioId()).isNull();
        assertThat(acta.isEmpate()).isFalse();
        verify(usuariosClient, never()).registrarVictoria(anyLong());

        // responder ya no se puede: la sala no quedo en curso
        assertThatThrownBy(() -> juegoService.responder(sala.getCodigo(), 1L, 1, correcta(salaPreguntaRepository, sala, 1)))
                .isInstanceOf(JuegoNoDisponibleException.class);
    }

    @Test
    void salirDeUnaSalaFinalizada_noHaceNadaYNoRompeElHistorial() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));
        for (int i = 0; i < 5; i++) {
            juegoService.responder(sala.getCodigo(), 1L, i, correcta(salaPreguntaRepository, sala, i));
            juegoService.responder(sala.getCodigo(), 2L, i, correcta(salaPreguntaRepository, sala, i));
        }
        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.FINALIZADA);

        assertThat(salaService.salir(sala.getCodigo(), 1L)).isNull();
        assertThat(participanteRepository.existsBySalaIdAndUsuarioId(sala.getId(), 1L)).isTrue();
    }

    // Simula el problema de varias replicas: el temporizador de gracia (en SalaWebSocketController)
    // vive en memoria de la replica que tenia la conexion, pero la reconexion puede llegar a
    // CUALQUIER otra replica. Aqui no hay ningun mapa en memoria involucrado: se llama directo a
    // salirPorDesconexion (lo que haria el temporizador de "la replica A" al cumplirse el margen),
    // pasandole la conexion que tenia Beto cuando se unio (lo que el controlador capturo ahi mismo,
    // no una hora de desconexion aparte), despues de que unirse() (lo que atenderia "la replica B")
    // ya actualizo ultimaConexion en la base de datos compartida. Si la correccion funciona, la
    // desconexion ya no cuenta como salida.
    @Test
    void reconexionEnOtraReplica_cancelaLaSalidaPorDesconexionAunqueElTemporizadorSigaCorriendo() throws InterruptedException {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        SalaParticipante beto = salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        // "la replica A" guarda la conexion que tenia Beto cuando se unio
        Instant conexionDeLaSesionQueSeFue = beto.getUltimaConexion();
        Thread.sleep(5); // asegura que la reconexion quede estrictamente despues, sin depender de la resolucion del reloj

        // Beto recarga la pagina y su nueva conexion cae en "la replica B": ella no sabe nada
        // del temporizador que arranco la replica A, solo llama a unirse() como siempre
        salaService.unirse(sala.getCodigo(), 2L, "Beto");

        // ahora se cumple el margen de gracia en "la replica A": su temporizador llama a
        // salirPorDesconexion con la conexion vieja que guardo, sin haberse enterado nunca de la reconexion
        SalaService.SalidaResultado resultado = salaService.salirPorDesconexion(sala.getCodigo(), 2L, conexionDeLaSesionQueSeFue);

        assertThat(resultado).isNull(); // la reconexion (en la OTRA replica) la cancelo
        assertThat(participanteRepository.findBySalaIdAndUsuarioId(sala.getId(), 2L).orElseThrow().isActivo()).isTrue();
        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.EN_CURSO);
    }

    @Test
    void desconexionSinReconexionEnNingunaReplica_siCuentaComoSalidaAlCumplirseLaGracia() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        SalaParticipante carla = salaService.unirse(sala.getCodigo(), 3L, "Carla"); // 3 participantes, para que no termine la partida

        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        // nadie vuelve a conectarse en ninguna replica antes de que el temporizador evalue, asi que la
        // conexion de Carla sigue siendo la misma que tenia cuando se unio
        SalaService.SalidaResultado resultado = salaService.salirPorDesconexion(sala.getCodigo(), 3L, carla.getUltimaConexion());

        assertThat(resultado).isNotNull();
        assertThat(resultado.terminarPartida()).isFalse(); // quedan Ana y Beto, 2 activos
        var carlaActualizada = participanteRepository.findBySalaIdAndUsuarioId(sala.getId(), 3L).orElseThrow();
        assertThat(carlaActualizada.isActivo()).isFalse();
        assertThat(carlaActualizada.isSalidaVoluntaria()).isFalse(); // fue por desconexion, no por "salir"
    }

    @Test
    void reconectarseDespuesDeUnaDesconexionConGracia_reactivaAlParticipante() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        SalaParticipante carla = salaService.unirse(sala.getCodigo(), 3L, "Carla");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        // el margen de gracia de Carla se cumplio sin que nadie la viera reconectarse
        salaService.salirPorDesconexion(sala.getCodigo(), 3L, carla.getUltimaConexion());
        assertThat(participanteRepository.findBySalaIdAndUsuarioId(sala.getId(), 3L).orElseThrow().isActivo()).isFalse();

        // pero mas tarde si vuelve a mandar "unirse" (a cualquier replica): como no fue un "salir"
        // explicito, se reactiva y puede seguir jugando
        salaService.unirse(sala.getCodigo(), 3L, "Carla");

        var carlaActualizada = participanteRepository.findBySalaIdAndUsuarioId(sala.getId(), 3L).orElseThrow();
        assertThat(carlaActualizada.isActivo()).isTrue();
        assertThat(carlaActualizada.isSalidaVoluntaria()).isFalse();
    }

    @Test
    void reconectarseDespuesDeUnSalirExplicito_noReactiva() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.unirse(sala.getCodigo(), 3L, "Carla");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        salaService.salir(sala.getCodigo(), 3L); // Carla manda "salir" a proposito
        assertThat(participanteRepository.findBySalaIdAndUsuarioId(sala.getId(), 3L).orElseThrow().isActivo()).isFalse();

        // si de todas formas su cliente manda "unirse" otra vez, no se reactiva: ella decidio salir
        salaService.unirse(sala.getCodigo(), 3L, "Carla");

        var carla = participanteRepository.findBySalaIdAndUsuarioId(sala.getId(), 3L).orElseThrow();
        assertThat(carla.isActivo()).isFalse();
        assertThat(carla.isSalidaVoluntaria()).isTrue();
    }

    @Test
    void responderConUnParticipanteQueYaSalio_seRechaza() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.unirse(sala.getCodigo(), 3L, "Carla");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        salaService.salir(sala.getCodigo(), 3L);

        assertThatThrownBy(() -> juegoService.responder(sala.getCodigo(), 3L, 0, correcta(salaPreguntaRepository, sala, 0)))
                .isInstanceOf(JuegoNoDisponibleException.class);
    }

    @Test
    void carreraSalirVsResponder_laPartidaTerminaUnaSolaVez() throws Exception {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 2L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        // las primeras 4 preguntas se juegan normal
        for (int i = 0; i < 4; i++) {
            juegoService.responder(sala.getCodigo(), 1L, i, correcta(salaPreguntaRepository, sala, i));
            juegoService.responder(sala.getCodigo(), 2L, i, correcta(salaPreguntaRepository, sala, i));
        }

        // en la ultima pregunta, Beto responde justo cuando Ana manda "salir": si Beto responde
        // primero la partida termina normal (con ganador); si Ana sale primero, Beto ya queda solo
        // y la partida termina por abandono. Lo que no puede pasar es que termine dos veces.
        int opcionCorrecta = correcta(salaPreguntaRepository, sala, 4);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch arrancar = new CountDownLatch(1);
        List<Future<?>> tareas = new ArrayList<>();
        tareas.add(pool.submit(() -> {
            arrancar.await();
            juegoService.responder(sala.getCodigo(), 2L, 4, opcionCorrecta);
            return null;
        }));
        tareas.add(pool.submit(() -> {
            arrancar.await();
            SalaService.SalidaResultado resultado = salaService.salir(sala.getCodigo(), 1L);
            if (resultado != null && resultado.terminarPartida()) {
                juegoService.finalizarPorAbandono(sala.getCodigo());
            }
            return null;
        }));
        arrancar.countDown();
        for (Future<?> f : tareas) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(salaRepository.findById(sala.getId()).orElseThrow().getEstado()).isEqualTo(EstadoSala.FINALIZADA);
        assertThat(partidaRepository.findAll().stream().filter(p -> p.getSalaId().equals(sala.getId()))).hasSize(1);
    }
}
