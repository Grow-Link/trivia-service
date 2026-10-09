package com.growlink.trivia.service;

import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.adapter.persistence.SalaParticipanteRepository;
import com.growlink.trivia.application.JuegoService;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.SalaPregunta;
import com.growlink.trivia.domain.SalaTrivia;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Estas dos pruebas son la contraparte de ReservationConcurrencyTest en
 * opportunities-service: hilos reales, no simulados, contra el mismo
 * mecanismo (un solo UPDATE con condicion en el WHERE, o un lock de fila).
 */
@SpringBootTest
@ActiveProfiles("test")
class JuegoConcurrencyTest {

    @Autowired
    private SalaService salaService;

    @Autowired
    private JuegoService juegoService;

    @Autowired
    private SalaParticipanteRepository participanteRepository;

    @Autowired
    private SalaPreguntaRepository salaPreguntaRepository;

    @Test
    void quinceUsuariosSeUnenALaVezSinPerderNiDuplicarANadie() throws Exception {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);

        int contendientes = 15;
        ExecutorService pool = Executors.newFixedThreadPool(contendientes);
        CountDownLatch listos = new CountDownLatch(contendientes);
        CountDownLatch arrancar = new CountDownLatch(1);
        List<Future<?>> resultados = new java.util.ArrayList<>();

        for (int i = 0; i < contendientes; i++) {
            long usuarioId = 100 + i;
            resultados.add(pool.submit(() -> {
                listos.countDown();
                arrancar.await();
                salaService.unirse(sala.getCodigo(), usuarioId, "user-" + usuarioId);
                return null;
            }));
        }

        listos.await();
        arrancar.countDown();
        for (Future<?> f : resultados) {
            f.get(10, TimeUnit.SECONDS); // si alguno lanzo excepcion, aqui revienta la prueba
        }
        pool.shutdown();

        // 15 que se unieron + Ana, la host, que ya estaba adentro desde que se creo la sala
        assertThat(participanteRepository.countBySalaId(sala.getId())).isEqualTo(16);
    }

    @Disabled("Pendiente: asume que la opcion 0 siempre es la correcta, y el banco de 200 preguntas ya no cumple eso")
    @Test
    void diezUsuariosRespondenALaVezYSoloUnoGanaElOrden() throws Exception {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);

        // Ana la host ya es participante desde que se creo la sala (1L)
        // le sumamos 9 mas para tener 10 participantes en total
        for (int i = 0; i < 9; i++) {
            salaService.unirse(sala.getCodigo(), 200L + i, "user-" + i);
        }
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        // los 10 que de verdad van a responder, Ana incluida
        List<Long> usuariosQueResponden = new java.util.ArrayList<>();
        usuariosQueResponden.add(1L);
        for (int i = 0; i < 9; i++) {
            usuariosQueResponden.add(200L + i);
        }
        int contendientes = usuariosQueResponden.size();

        ExecutorService pool = Executors.newFixedThreadPool(contendientes);
        CountDownLatch listos = new CountDownLatch(contendientes);
        CountDownLatch arrancar = new CountDownLatch(1);
        AtomicInteger errores = new AtomicInteger();
        List<Future<?>> resultados = new java.util.ArrayList<>();

        for (long usuarioId : usuariosQueResponden) {
            resultados.add(pool.submit(() -> {
                listos.countDown();
                arrancar.await();
                try {
                    // todas las preguntas sembradas tienen la opcion 0 como correcta
                    juegoService.responder(sala.getCodigo(), usuarioId, 0, 0);
                } catch (RuntimeException e) {
                    errores.incrementAndGet();
                }
                return null;
            }));
        }

        listos.await();
        arrancar.countDown();
        for (Future<?> f : resultados) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(errores.get()).isZero();

        SalaPregunta primeraPregunta = salaPreguntaRepository.findBySalaIdAndIndice(sala.getId(), 0).orElseThrow();
        // las 10 respuestas quedaron registradas, ninguna se perdio
        assertThat(primeraPregunta.getPrimerAcertanteUsuarioId()).isNotNull();

        // como las 10 fueron correctas y todas llegaron a la vez, el sistema
        // ya tuvo que haber activado la pregunta 1 exactamente una vez
        SalaPregunta segundaPregunta = salaPreguntaRepository.findBySalaIdAndIndice(sala.getId(), 1).orElseThrow();
        assertThat(segundaPregunta.getEnviadaEn()).isNotNull();
    }
}
