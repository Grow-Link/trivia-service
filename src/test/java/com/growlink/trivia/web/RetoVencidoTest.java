package com.growlink.trivia.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growlink.trivia.application.UsuariosClient;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Con vigencia 0 minutos, un reto nace ya vencido: no sale en la bandeja, se ve como EXPIRADO y no se puede aceptar.
@SpringBootTest(properties = "trivia.retos.vigencia-minutos=0")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RetoVencidoTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private UsuariosClient usuariosClient;
    @Value("${growlink.jwt.secret}")
    private String secret;

    @Test
    void unRetoVencidoNoSeMuestraNiSePuedeAceptar() throws Exception {
        Map<String, Object> cuerpo = Map.of("retadoUsuarioId", 702, "retadoNombre", "Pedro", "retadorNombre", "Paula",
                "categoria", "MATEMATICAS", "numPreguntas", 5, "duracionSegundos", 10, "mensaje", "va");
        String creado = mockMvc.perform(post("/api/retos").header("Authorization", token(701L))
                        .contentType("application/json").content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode reto = objectMapper.readTree(creado);
        assertThat(reto.get("estado").asText()).isEqualTo("EXPIRADO");

        String bandeja = mockMvc.perform(get("/api/retos/recibidos").header("Authorization", token(702L)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(bandeja)).isEmpty();

        mockMvc.perform(post("/api/retos/" + reto.get("id").asLong() + "/aceptar").header("Authorization", token(702L)))
                .andExpect(status().isConflict());
    }

    private String token(long usuarioId) {
        return "Bearer " + Jwts.builder().subject(String.valueOf(usuarioId)).claim("rol", "USUARIO")
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
