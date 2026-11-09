package com.growlink.trivia.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growlink.trivia.LlavesDePrueba;
import com.growlink.trivia.application.UsuariosClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Con auth.mode=oidc los endpoints REST tienen que validar el token del proveedor, igual que el
// CONNECT del WebSocket: si el REST se quedara con el modo legacy, el token del proveedor seria
// adorno y cualquiera con un JWT firmado con el secreto compartido entraria por la puerta de al
// lado (/api/retos, /api/partidas, /api/preguntas, /api/metricas).
//
// El emisor de OIDC es el de mentiras de LlavesDePrueba: par de llaves RSA generado en la prueba
// y JWKS servido en localhost.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RestTokenOidcTest {

    @DynamicPropertySource
    static void modoOidc(DynamicPropertyRegistry registro) {
        registro.add("auth.mode", () -> "oidc");
        // identity-source=claims conserva el comportamiento anterior (el usuarioId sale del sub),
        // que es justo lo que estas pruebas cubren, y evita tener que levantar usuarios-service
        registro.add("auth.identity-source", () -> "claims");
        registro.add("auth.oidc.issuer-uri", LlavesDePrueba::issuer);
        registro.add("auth.oidc.audience", () -> LlavesDePrueba.AUDIENCE);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UsuariosClient usuariosClient;

    @Value("${growlink.jwt.secret}")
    private String secretoLegacy;

    // ------------------------------------------------------------- 401

    @Test
    void sinTokenLosEndpointsRespondenConUnNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/partidas/ganadores")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/partidas/mias")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/retos/recibidos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/metricas/dashboard")).andExpect(status().isUnauthorized());
    }

    // el punto de toda la tarea: en modo oidc el token viejo (HS256 con el secreto compartido de
    // usuarios-service) ya NO sirve. Si esto pasara, el modo oidc no estaria cerrando nada
    @Test
    void elTokenLegacyDeUsuariosServiceYaNoSirveEnModoOidc() throws Exception {
        mockMvc.perform(get("/api/partidas/ganadores").header(HttpHeaders.AUTHORIZATION, tokenLegacy(7L, "USUARIO")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/metricas/dashboard").header(HttpHeaders.AUTHORIZATION, tokenLegacy(7L, "ADMIN")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenVencidoNoSirve() throws Exception {
        pedirGanadoresCon(LlavesDePrueba.tokenVencido(7)).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDeOtroEmisorNoSirve() throws Exception {
        pedirGanadoresCon(LlavesDePrueba.tokenDeOtroEmisor(7)).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenParaOtraAudienciaNoSirve() throws Exception {
        pedirGanadoresCon(LlavesDePrueba.tokenParaOtraAudiencia(7)).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenFirmadoPorOtroNoSirve() throws Exception {
        pedirGanadoresCon(LlavesDePrueba.tokenFirmadoPorUnImpostor(7)).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenConAlgNoneNoSirve() throws Exception {
        pedirGanadoresCon(LlavesDePrueba.tokenConAlgNone(7)).andExpect(status().isUnauthorized());
    }

    @Test
    void laConfusionDeAlgoritmoConHs256NoSirve() throws Exception {
        pedirGanadoresCon(LlavesDePrueba.tokenConHs256FirmadoConLaLlavePublica(7))
                .andExpect(status().isUnauthorized());
    }

    // el 401 que le vuelve al cliente es pelado, asi que el motivo tiene que quedar en el log del
    // servidor (y con el X-Request-Id de la peticion, que RequestIdFilter deja en el MDC): sin eso
    // un token mal configurado en un ambiente es imposible de diagnosticar
    @Test
    void elRechazoQuedaEnElLogConElMotivoYElRequestId(CapturedOutput salida) throws Exception {
        mockMvc.perform(get("/api/partidas/ganadores")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + LlavesDePrueba.tokenVencido(7))
                        .header("X-Request-Id", "traza-del-401"))
                .andExpect(status().isUnauthorized());

        assertThat(salida.getOut())
                .contains("peticion REST rechazada con 401")
                .contains("vencido")
                .contains("traza-del-401");
    }

    // ------------------------------------------------------------- token bueno

    @Test
    void unTokenValidoDelProveedorSiEntra() throws Exception {
        pedirGanadoresCon(LlavesDePrueba.tokenValido(7)).andExpect(status().isOk());
    }

    // la identidad sale del sub del token y no de nada que mande el cliente en el cuerpo
    @Test
    void laIdentidadDelQueRetaSaleDelSubDelToken() throws Exception {
        Map<String, Object> cuerpo = Map.of(
                "retadoUsuarioId", 902,
                "retadoNombre", "Beto",
                "retadorNombre", "Ana",
                "categoria", "INGENIERIA_SISTEMAS",
                "numPreguntas", 5,
                "duracionSegundos", 10);

        mockMvc.perform(post("/api/retos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + LlavesDePrueba.tokenValido(901))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retadorUsuarioId").value(901));
    }

    // ------------------------------------------------------------- el rol, para el dashboard

    @Test
    void elDashboardSigueSiendoSoloDelAdmin() throws Exception {
        mockMvc.perform(get("/api/metricas/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + LlavesDePrueba.tokenValidoConRol(7, "USUARIO")))
                .andExpect(status().isForbidden());

        // un token valido pero sin el claim del rol tampoco alcanza
        mockMvc.perform(get("/api/metricas/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + LlavesDePrueba.tokenValido(7)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/metricas/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + LlavesDePrueba.tokenValidoConRol(7, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void elRolTambienSirveCuandoElProveedorLoPublicaComoLista() throws Exception {
        mockMvc.perform(get("/api/metricas/dashboard")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + LlavesDePrueba.tokenValidoConRoles(7, List.of("USUARIO", "ADMIN"))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions pedirGanadoresCon(String token) throws Exception {
        return mockMvc.perform(get("/api/partidas/ganadores").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // el JWT que emite usuarios-service: HS256 con el secreto compartido, el del modo legacy
    private String tokenLegacy(long usuarioId, String rol) {
        return "Bearer " + Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("rol", rol)
                .signWith(Keys.hmacShaKeyFor(secretoLegacy.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
