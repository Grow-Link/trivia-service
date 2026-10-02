package com.growlink.trivia.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growlink.trivia.adapter.persistence.PreguntaBancoRepository;
import com.growlink.trivia.application.CursosClient;
import com.growlink.trivia.application.CursosServiceNoDisponibleException;
import com.growlink.trivia.domain.Categoria;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HU-23. cursos-service se simula con un mock de CursosClient: aqui solo se prueba
// la regla de trivia (publicador sacado del token + categoria propia + exactamente
// 4 opciones), no la red. Los tokens se firman con el secreto de application-test.yml.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PreguntaControllerTest {

    private static final long PUBLICADOR_ID = 701L;
    private static final List<String> CUATRO = List.of("A", "B", "C", "D");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PreguntaBancoRepository preguntaRepository;

    @MockBean
    private CursosClient cursosClient;

    @Value("${growlink.jwt.secret}")
    private String secret;

    private String tokenDe(long usuarioId, String secreto) {
        return "Bearer " + Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("rol", "PUBLICADOR")
                .signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private ResultActions crearConToken(String authorization, Categoria categoria, String texto,
                                        List<String> opciones, int respuestaCorrecta) throws Exception {
        Map<String, Object> body = Map.of("categoria", categoria.name(), "texto", texto,
                "opciones", opciones, "respuestaCorrecta", respuestaCorrecta);
        var request = post("/api/preguntas")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body));
        if (authorization != null) {
            request = request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }

    private ResultActions crear(Categoria categoria, String texto, List<String> opciones, int respuestaCorrecta)
            throws Exception {
        return crearConToken(tokenDe(PUBLICADOR_ID, secret), categoria, texto, opciones, respuestaCorrecta);
    }

    @Test
    void publicadorCreaPreguntaEnUnaCategoriaDondeTieneCursos() throws Exception {
        when(cursosClient.categoriasPublicadas(eq(PUBLICADOR_ID), any()))
                .thenReturn(Set.of(Categoria.INGENIERIA_SISTEMAS));

        crear(Categoria.INGENIERIA_SISTEMAS, "Pregunta HU-23 valida", CUATRO, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.opciones.length()").value(4))
                .andExpect(jsonPath("$.respuestaCorrecta").value(2))
                .andExpect(jsonPath("$.publicadorUsuarioId").value(PUBLICADOR_ID));

        assertThat(preguntaRepository.findByCategoria(Categoria.INGENIERIA_SISTEMAS))
                .anyMatch(p -> p.getTexto().equals("Pregunta HU-23 valida") && p.getOpciones().equals(CUATRO)
                        && PUBLICADOR_ID == p.getPublicadorUsuarioId());
        // el header Authorization se reenvia a cursos-service
        verify(cursosClient).categoriasPublicadas(PUBLICADOR_ID, tokenDe(PUBLICADOR_ID, secret));
    }

    @Test
    void elPublicadorSeSacaDelTokenYNoDelBody() throws Exception {
        long otroPublicador = 999L;
        when(cursosClient.categoriasPublicadas(eq(otroPublicador), any())).thenReturn(Set.of(Categoria.DERECHO));
        when(cursosClient.categoriasPublicadas(eq(PUBLICADOR_ID), any())).thenReturn(Set.of());

        // aunque el body diga que es de otro publicador, cuenta el dueño del token
        Map<String, Object> body = Map.of("categoria", "DERECHO", "publicadorUsuarioId", otroPublicador,
                "texto", "Suplantando a otro", "opciones", CUATRO, "respuestaCorrecta", 0);
        mockMvc.perform(post("/api/preguntas")
                        .header("Authorization", tokenDe(PUBLICADOR_ID, secret))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verify(cursosClient, never()).categoriasPublicadas(eq(otroPublicador), any());
    }

    @Test
    void sinTokenOConTokenInvalidoDevuelve401() throws Exception {
        crearConToken(null, Categoria.IDIOMAS, "Sin token", CUATRO, 0).andExpect(status().isUnauthorized());
        crearConToken("Bearer basura", Categoria.IDIOMAS, "Token basura", CUATRO, 0)
                .andExpect(status().isUnauthorized());
        crearConToken(tokenDe(PUBLICADOR_ID, "otro-secreto-que-no-es-el-compartido-32b"), Categoria.IDIOMAS,
                "Firma ajena", CUATRO, 0).andExpect(status().isUnauthorized());

        verifyNoInteractions(cursosClient);
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
