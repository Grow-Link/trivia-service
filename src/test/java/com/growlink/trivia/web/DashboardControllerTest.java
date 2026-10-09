package com.growlink.trivia.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.application.JuegoService;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.application.UsuariosClient;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.SalaTrivia;
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

import java.nio.charset.StandardCharsets;

import static com.growlink.trivia.RespuestasDePrueba.correcta;
import static com.growlink.trivia.RespuestasDePrueba.incorrecta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HU-24. Las pruebas comparten la base H2, por eso se compara antes y despues
// de generar actividad, no contra numeros fijos
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SalaService salaService;
    @Autowired
    private JuegoService juegoService;
    @Autowired
    private SalaPreguntaRepository preguntas;

    @MockBean
    private UsuariosClient usuariosClient;

    @Value("${growlink.jwt.secret}")
    private String secret;

    @Test
    void soloElAdminPuedeVerElDashboard() throws Exception {
        mockMvc.perform(get("/api/metricas/dashboard"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/metricas/dashboard").header("Authorization", tokenDe("USUARIO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/metricas/dashboard").header("Authorization", tokenDe("PUBLICADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/metricas/dashboard").header("Authorization", tokenDe("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/metricas/dashboard").param("ultimosMinutos", "0")
                        .header("Authorization", tokenDe("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lasMetricasReflejanLaActividadNueva() throws Exception {
        JsonNode antes = dashboard();

        // una sala nueva con 2 jugadores esperando
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 1L, "Ana", 5, 10);
        salaService.unirse(sala.getCodigo(), 400L, "Beto");

        JsonNode conSalaEnEspera = dashboard();
        assertThat(conSalaEnEspera.get("salasEnEspera").asLong()).isEqualTo(antes.get("salasEnEspera").asLong() + 1);
        assertThat(conSalaEnEspera.get("salasActivas").asLong()).isEqualTo(antes.get("salasActivas").asLong() + 1);
        assertThat(conSalaEnEspera.get("participantesConectados").asLong())
                .isEqualTo(antes.get("participantesConectados").asLong() + 2);

        // se juega la partida completa: Ana acierta todo, Beto acierta las 2 primeras
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));
        JsonNode enCurso = dashboard();
        assertThat(enCurso.get("salasEnCurso").asLong()).isEqualTo(antes.get("salasEnCurso").asLong() + 1);

        for (int i = 0; i < 5; i++) {
            juegoService.responder(sala.getCodigo(), 1L, i, correcta(preguntas, sala, i));
            juegoService.responder(sala.getCodigo(), 400L, i, i < 2 ? correcta(preguntas, sala, i) : incorrecta(preguntas, sala, i));
        }

        // la sala termino: ya no es activa, y quedaron sus eventos
        JsonNode despues = dashboard();
        assertThat(despues.get("salasActivas").asLong()).isEqualTo(antes.get("salasActivas").asLong());
        assertThat(despues.get("participantesConectados").asLong())
                .isEqualTo(antes.get("participantesConectados").asLong());
        assertThat(despues.get("partidasFinalizadas").asLong()).isEqualTo(antes.get("partidasFinalizadas").asLong() + 1);
        assertThat(despues.get("empatesResueltos").asLong()).isEqualTo(antes.get("empatesResueltos").asLong() + 2);
        assertThat(despues.get("latenciaPromedioMs").isNull()).isFalse();
        assertThat(despues.get("latenciaPromedioMs").asDouble()).isGreaterThanOrEqualTo(0);
        assertThat(despues.get("duracionPromedioPartidaMs").isNull()).isFalse();

        // con una ventana muy corta tambien aparece lo que acaba de pasar
        JsonNode reciente = dashboard("?ultimosMinutos=5");
        assertThat(reciente.get("partidasFinalizadas").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(reciente.get("ventanaMinutos").asInt()).isEqualTo(5);
    }

    private JsonNode dashboard() throws Exception {
        return dashboard("");
    }

    private JsonNode dashboard(String query) throws Exception {
        String json = mockMvc.perform(get("/api/metricas/dashboard" + query).header("Authorization", tokenDe("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private String tokenDe(String rol) {
        return "Bearer " + Jwts.builder()
                .subject("3")
                .claim("rol", rol)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
