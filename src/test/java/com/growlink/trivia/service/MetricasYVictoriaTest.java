package com.growlink.trivia.service;

import com.growlink.trivia.adapter.persistence.MetricaEventoRepository;
import com.growlink.trivia.application.JuegoService;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.application.UsuariosClient;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.SalaTrivia;
import com.growlink.trivia.domain.TipoEvento;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// HU-22 (trivias ganadas) y los eventos que alimentan el dashboard de HU-24
// usuarios-service es falso aqui, solo se revisa que le avisemos bien al ganador
@SpringBootTest
@ActiveProfiles("test")
class MetricasYVictoriaTest {

    @Autowired
    private SalaService salaService;
    @Autowired
    private JuegoService juegoService;
    @Autowired
    private MetricaEventoRepository eventos;

    @MockBean
    private UsuariosClient usuariosClient;

    @Disabled("Pendiente: asume que la opcion 0 siempre es la correcta, y el banco de 200 preguntas ya no cumple eso")
    @Test
    void unaPartidaCompletaDejaSusEventosYLeSumaLaVictoriaSoloAlGanador() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 300L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        // Ana acierta las 5 y siempre contesta primero
        // Beto acierta las 3 primeras (pierde la carrera por el primer acierto) y falla las 2 ultimas
        for (int i = 0; i < 5; i++) {
            juegoService.responder(sala.getCodigo(), 1L, i, 0);
            juegoService.responder(sala.getCodigo(), 300L, i, i < 3 ? 0 : 1);
        }

        assertThat(eventos.countBySalaIdAndTipo(sala.getId(), TipoEvento.SALA_CREADA)).isEqualTo(1);
        assertThat(eventos.countBySalaIdAndTipo(sala.getId(), TipoEvento.PARTICIPANTE_UNIDO)).isEqualTo(1);
        assertThat(eventos.countBySalaIdAndTipo(sala.getId(), TipoEvento.PREGUNTA_ENVIADA)).isEqualTo(5);
        assertThat(eventos.countBySalaIdAndTipo(sala.getId(), TipoEvento.PRIMERA_RESPUESTA)).isEqualTo(5);
        assertThat(eventos.countBySalaIdAndTipo(sala.getId(), TipoEvento.EMPATE_RESUELTO)).isEqualTo(3);
        assertThat(eventos.countBySalaIdAndTipo(sala.getId(), TipoEvento.PARTIDA_FINALIZADA)).isEqualTo(1);

        var finalizada = eventos.findAll().stream()
                .filter(e -> e.getTipo() == TipoEvento.PARTIDA_FINALIZADA && e.getSalaId().equals(sala.getId()))
                .findFirst().orElseThrow();
        assertThat(finalizada.getParticipantes()).isEqualTo(2);
        assertThat(finalizada.getDuracionMs()).isNotNull().isGreaterThanOrEqualTo(0);

        // la victoria se suma una sola vez, y es la de Ana
        verify(usuariosClient, times(1)).registrarVictoria(1L);
        verify(usuariosClient, never()).registrarVictoria(300L);
    }

    @Disabled("Pendiente: asume que la opcion 0 siempre es la correcta, y el banco de 200 preguntas ya no cumple eso")
    @Test
    void siNadieSumaPuntosNoHayGanador() {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 301L, "Beto");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));

        // las dos responden mal todas las preguntas
        for (int i = 0; i < 5; i++) {
            juegoService.responder(sala.getCodigo(), 1L, i, 1);
            juegoService.responder(sala.getCodigo(), 301L, i, 1);
        }

        assertThat(eventos.countBySalaIdAndTipo(sala.getId(), TipoEvento.PARTIDA_FINALIZADA)).isEqualTo(1);
        assertThat(eventos.countBySalaIdAndTipo(sala.getId(), TipoEvento.EMPATE_RESUELTO)).isZero();
        verify(usuariosClient, never()).registrarVictoria(1L);
        verify(usuariosClient, never()).registrarVictoria(301L);
    }
}
