package com.growlink.trivia.web;

import com.growlink.trivia.LlavesDePrueba;
import com.growlink.trivia.UsuariosServiceFalso;
import com.growlink.trivia.application.UsuariosClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.growlink.trivia.LlavesDePrueba.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El camino completo con auth.identity-source=usuarios-service: token de Entra con sub opaco,
// usuarios-service simulado local, y endpoints REST de verdad.
//
// Cada prueba usa un sub distinto a proposito: el contexto de Spring se comparte entre metodos y
// con el el cache de identidades, asi que reusar el mismo token haria que una prueba viera el rol
// que configuro la anterior.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RestIdentidadUsuariosServiceTest {

    private static final String CLIENT_ID = "4c1a9f70-1111-2222-3333-abcdefabcdef";

    // estatico y arrancado antes del contexto, porque @DynamicPropertySource necesita su puerto
    private static final UsuariosServiceFalso USUARIOS = new UsuariosServiceFalso();

    @DynamicPropertySource
    static void modoOidcConUsuariosService(DynamicPropertyRegistry registro) {
        registro.add("auth.mode", () -> "oidc");
        registro.add("auth.multi-tenant", () -> "true");
        registro.add("auth.identity-source", () -> "usuarios-service");
        registro.add("auth.usuarios-service-url", USUARIOS::baseUrl);
        registro.add("auth.oidc.audience", () -> "api://" + CLIENT_ID);
        registro.add("auth.oidc.jwks-uri", LlavesDePrueba::jwksUri);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuariosClient usuariosClient;

    @BeforeEach
    void reiniciar() {
        USUARIOS.responde(42L, "USUARIO").demora(0);
        USUARIOS.reiniciarContador();
    }

    private String tokenConSub(String sub) {
        return LlavesDePrueba.tokenDeEntraConSubYAudiencia(sub, TENANT_A, "api://" + CLIENT_ID);
    }

    private ResultActions pedirHistorial(String sub) throws Exception {
        return mockMvc.perform(get("/api/partidas/ganadores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenConSub(sub)));
    }

    private ResultActions pedirDashboard(String sub) throws Exception {
        return mockMvc.perform(get("/api/metricas/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenConSub(sub)));
    }

    // ------------------------------------------------------- el caso central

    // Antes de esta HU esto daba 401, porque el servicio intentaba leer el sub como numero
    @Test
    void unTokenDeEntraConSubOpacoYaEntra() throws Exception {
        USUARIOS.responde(42L, "USUARIO");

        pedirHistorial("AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy782bbtaQ").andExpect(status().isOk());

        assertThat(USUARIOS.llamadas()).isEqualTo(1);
    }

    @Test
    void sinTokenSigueSiendo401() throws Exception {
        mockMvc.perform(get("/api/partidas/ganadores")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/metricas/dashboard")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------- cache

    @Test
    void dosPeticionesConElMismoTokenSoloPreguntanUnaVez() throws Exception {
        USUARIOS.responde(42L, "USUARIO");

        pedirHistorial("sub-que-se-repite").andExpect(status().isOk());
        pedirHistorial("sub-que-se-repite").andExpect(status().isOk());

        assertThat(USUARIOS.llamadas()).isEqualTo(1);
    }

    // ------------------------------------------------------- los roles, por el camino real

    // El rol del dashboard ya NO sale del claim "rol" del token: sale de /api/usuarios/me. Los
    // tokens de estas tres pruebas no traen ningun claim de rol
    @Test
    void elDashboardSoloDejaEntrarAlAdminQueDigaUsuariosService() throws Exception {
        USUARIOS.responde(100L, "USUARIO");
        pedirDashboard("sub-del-usuario").andExpect(status().isForbidden());

        USUARIOS.responde(101L, "PUBLICADOR");
        pedirDashboard("sub-del-publicador").andExpect(status().isForbidden());

        USUARIOS.responde(102L, "ADMIN");
        pedirDashboard("sub-del-admin").andExpect(status().isOk());
    }

    // ------------------------------------------------------- errores

    @Test
    void siUsuariosServiceDiceQueNoReconoceElTokenRespondemos401() throws Exception {
        USUARIOS.respondeConEstado(401);

        pedirHistorial("sub-que-usuarios-no-conoce").andExpect(status().isUnauthorized());
    }

    // 503 y no 401: el token estaba bien, lo que fallo fue usuarios-service. Un 401 mandaria al
    // frontend a pedir un login nuevo, que no arregla una caida de atras
    @Test
    void siUsuariosServiceFallaRespondemos503(CapturedOutput salida) throws Exception {
        USUARIOS.respondeConEstado(500);

        mockMvc.perform(get("/api/partidas/ganadores")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenConSub("sub-con-500"))
                        .header("X-Request-Id", "traza-del-503"))
                .andExpect(status().isServiceUnavailable());

        // el motivo queda en el log, con el X-Request-Id de la peticion
        assertThat(salida.getOut())
                .contains("no se pudo resolver la identidad")
                .contains("traza-del-503");
    }

    @Test
    void siUsuariosServiceNoContestaATiempoRespondemos503() throws Exception {
        USUARIOS.responde(42L, "USUARIO").demora(4_000); // el timeout de lectura esta en 3 s

        pedirHistorial("sub-con-timeout").andExpect(status().isServiceUnavailable());
    }

    // y lo que NO puede pasar nunca: que una caida de usuarios-service deje entrar al token
    // creyendole al sub
    @Test
    void unaCaidaDeUsuariosServiceNuncaDejaEntrarPorElSub() throws Exception {
        USUARIOS.respondeConEstado(503);

        // un sub que SI es numerico, el caso en que caer al modo claims habria funcionado
        pedirHistorial("42").andExpect(status().isServiceUnavailable());
    }
}
