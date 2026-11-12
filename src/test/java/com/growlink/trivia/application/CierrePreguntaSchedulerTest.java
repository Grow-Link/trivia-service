package com.growlink.trivia.application;

import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.config.RequestIdFilter;
import com.growlink.trivia.domain.SalaPregunta;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Cada cierre por tiempo de una pregunta tiene su propio X-Request-Id mientras se atiende: si ese cierre termina la
// partida, la llamada que le suma la victoria al ganador en usuarios-service lo lleva (ver ReenvioDeRequestId).
class CierrePreguntaSchedulerTest {

    private final SalaPreguntaRepository repositorio = mock(SalaPreguntaRepository.class);
    private final JuegoService juego = mock(JuegoService.class);
    private final CierrePreguntaScheduler barredor = new CierrePreguntaScheduler(repositorio, juego);

    @Test
    void cadaCierreTieneSuIdYNoSeLeQuedaPegadoAlHilo() {
        // los mocks de las preguntas se arman antes: Mockito no deja crear un stub dentro de otro
        List<SalaPregunta> vencidas = List.of(pregunta(1L), pregunta(2L));
        when(repositorio.buscarActivasSinCerrar()).thenReturn(vencidas);
        List<String> vistos = new ArrayList<>();
        doAnswer(invocacion -> vistos.add(MDC.get(RequestIdFilter.MDC_KEY))).when(juego).intentarCerrarPorTiempo(anyLong());

        barredor.cerrarPreguntasVencidas();

        assertThat(vistos).hasSize(2).doesNotContainNull();
        // la misma regla que acepta usuarios-service, si no el id se cambiaria al llegar alla
        assertThat(vistos).allMatch(id -> id.matches("^cierre-[A-Za-z0-9._-]{1,57}$"));
        assertThat(vistos.get(0)).isNotEqualTo(vistos.get(1));
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void siUnCierreFallaElIdTambienSeBorra() {
        List<SalaPregunta> vencidas = List.of(pregunta(3L));
        when(repositorio.buscarActivasSinCerrar()).thenReturn(vencidas);
        doThrow(new IllegalStateException("se cayo la base")).when(juego).intentarCerrarPorTiempo(anyLong());

        barredor.cerrarPreguntasVencidas();

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    private static SalaPregunta pregunta(Long id) {
        SalaPregunta pregunta = mock(SalaPregunta.class);
        when(pregunta.getId()).thenReturn(id);
        return pregunta;
    }
}
