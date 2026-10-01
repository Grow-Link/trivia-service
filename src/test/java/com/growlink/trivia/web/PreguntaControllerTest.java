package com.growlink.trivia.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growlink.trivia.adapter.persistence.PreguntaBancoRepository;
import com.growlink.trivia.application.CursosClient;
import com.growlink.trivia.application.CursosServiceNoDisponibleException;
import com.growlink.trivia.domain.Categoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HU-23. cursos-service se simula con un mock de CursosClient: aqui solo se prueba
// la regla de trivia (categoria propia + exactamente 4 opciones), no la red.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PreguntaControllerTest {

    private static final long PUBLICADOR_ID = 701L;
    private static final String TOKEN = "Bearer token-de-prueba";
    private static final List<String> CUATRO = List.of("A", "B", "C", "D");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PreguntaBancoRepository preguntaRepository;

    @MockBean
    private CursosClient cursosClient;

    private ResultActions crear(Categoria categoria, String texto, List<String> opciones, int respuestaCorrecta)
            throws Exception {
        Map<String, Object> body = Map.of("categoria", categoria.name(), "publicadorUsuarioId", PUBLICADOR_ID,
                "texto", texto, "opciones", opciones, "respuestaCorrecta", respuestaCorrecta);
        return mockMvc.perform(post("/api/preguntas")
                .header("Authorization", TOKEN)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void publicadorCreaPreguntaEnUnaCategoriaDondeTieneCursos() throws Exception {
        when(cursosClient.categoriasPublicadas(eq(PUBLICADOR_ID), any()))
                .thenReturn(Set.of(Categoria.INGENIERIA_SISTEMAS));

        crear(Categoria.INGENIERIA_SISTEMAS, "Pregunta HU-23 valida", CUATRO, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.opciones.length()").value(4))
                .andExpect(jsonPath("$.respuestaCorrecta").value(2));

        assertThat(preguntaRepository.findByCategoria(Categoria.INGENIERIA_SISTEMAS))
                .anyMatch(p -> p.getTexto().equals("Pregunta HU-23 valida") && p.getOpciones().equals(CUATRO));
        // el header Authorization se reenvia a cursos-service
        verify(cursosClient).categoriasPublicadas(PUBLICADOR_ID, TOKEN);
    }

    @Test
    void categoriaDondeNoTieneCursosSeRechazaConForbidden() throws Exception {
        when(cursosClient.categoriasPublicadas(eq(PUBLICADOR_ID), any()))
                .thenReturn(Set.of(Categoria.INGENIERIA_SISTEMAS));

        crear(Categoria.DERECHO, "Pregunta HU-23 ajena", CUATRO, 0)
                .andExpect(status().isForbidden());

        assertThat(preguntaRepository.findByCategoria(Categoria.DERECHO))
                .noneMatch(p -> p.getTexto().equals("Pregunta HU-23 ajena"));
    }

    @Test
    void conMenosOMasDeCuatroOpcionesSeRechaza() throws Exception {
        when(cursosClient.categoriasPublicadas(eq(PUBLICADOR_ID), any())).thenReturn(Set.of(Categoria.IDIOMAS));

        crear(Categoria.IDIOMAS, "Tres opciones", List.of("A", "B", "C"), 0).andExpect(status().isBadRequest());
        crear(Categoria.IDIOMAS, "Cinco opciones", List.of("A", "B", "C", "D", "E"), 0)
                .andExpect(status().isBadRequest());
    }

    @Test
    void opcionVaciaORespuestaCorrectaFueraDeRangoSeRechaza() throws Exception {
        when(cursosClient.categoriasPublicadas(eq(PUBLICADOR_ID), any())).thenReturn(Set.of(Categoria.IDIOMAS));

        crear(Categoria.IDIOMAS, "Opcion vacia", List.of("A", "B", "C", " "), 0).andExpect(status().isBadRequest());
        crear(Categoria.IDIOMAS, "Respuesta 4", CUATRO, 4).andExpect(status().isBadRequest());
        crear(Categoria.IDIOMAS, "Respuesta -1", CUATRO, -1).andExpect(status().isBadRequest());
    }

    @Test
    void siCursosServiceNoResponderDevuelve503() throws Exception {
        when(cursosClient.categoriasPublicadas(eq(PUBLICADOR_ID), any()))
                .thenThrow(new CursosServiceNoDisponibleException(new RuntimeException("caido")));

        crear(Categoria.IDIOMAS, "Cursos caido", CUATRO, 0).andExpect(status().isServiceUnavailable());
    }
}
