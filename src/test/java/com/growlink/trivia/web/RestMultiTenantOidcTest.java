package com.growlink.trivia.web;

import com.growlink.trivia.LlavesDePrueba;
import com.growlink.trivia.application.UsuariosClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.growlink.trivia.LlavesDePrueba.TENANT_A;
import static com.growlink.trivia.LlavesDePrueba.TENANT_B;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Lo mismo que EmisorMultiTenantTest pero por el camino completo: las propiedades de verdad
// (auth.multi-tenant, la audiencia como lista), el bean que arma AuthModeConfig y un endpoint REST
// real. Es lo que atrapa un error de cableado que las unitarias no ven, porque ellas construyen el
// verificador a mano.
//
// A proposito NO se configura auth.oidc.issuer-uri: con multi-tenant=true deja de ser obligatorio,
// y que el contexto arranque sin el es parte de lo que se esta probando.
//
// El jwks-uri si se configura, apuntando al JWKS local de LlavesDePrueba: sin eso el valor por
// defecto seria el "common" de Microsoft y la prueba saldria a internet.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestMultiTenantOidcTest {

    private static final String CLIENT_ID = "4c1a9f70-1111-2222-3333-abcdefabcdef";

    @DynamicPropertySource
    static void modoOidcMultiTenant(DynamicPropertyRegistry registro) {
        registro.add("auth.mode", () -> "oidc");
        // identity-source=claims conserva el comportamiento anterior (el usuarioId sale del sub),
        // que es justo lo que estas pruebas cubren, y evita tener que levantar usuarios-service
        registro.add("auth.identity-source", () -> "claims");
        registro.add("auth.multi-tenant", () -> "true");
        // las dos formas con que Entra publica el aud, separadas por comas
        registro.add("auth.oidc.audience", () -> "api://" + CLIENT_ID + "," + CLIENT_ID);
        registro.add("auth.oidc.jwks-uri", LlavesDePrueba::jwksUri);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuariosClient usuariosClient;

    @Test
    void entranLosUsuariosDeCualquierDirectorio() throws Exception {
        conToken(LlavesDePrueba.tokenDeEntraConAudiencia(301, TENANT_A, "api://" + CLIENT_ID))
                .andExpect(status().isOk());
        conToken(LlavesDePrueba.tokenDeEntraConAudiencia(302, TENANT_B, "api://" + CLIENT_ID))
                .andExpect(status().isOk());
    }

    // la audiencia como lista, por el camino de la propiedad: cualquiera de las dos formas sirve
    @Test
    void laAudienciaSirveEnLasDosFormasQueUsaEntra() throws Exception {
        conToken(LlavesDePrueba.tokenDeEntraConAudiencia(303, TENANT_A, "api://" + CLIENT_ID))
                .andExpect(status().isOk());
        conToken(LlavesDePrueba.tokenDeEntraConAudiencia(303, TENANT_A, CLIENT_ID))
                .andExpect(status().isOk());
    }

    @Test
    void unaAudienciaDeOtraAplicacionNoEntra() throws Exception {
        conToken(LlavesDePrueba.tokenDeEntraConAudiencia(304, TENANT_A, "api://otra-aplicacion"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTidQueNoCuadraConElIssNoEntra() throws Exception {
        conToken(LlavesDePrueba.tokenDeEntraConTidDeOtroTenant(305, TENANT_A, TENANT_B))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unEmisorQueSoloSeParaceAlDeMicrosoftNoEntra() throws Exception {
        String iss = "https://login.microsoftonline.com.evil.com/" + TENANT_A + "/v2.0";

        conToken(LlavesDePrueba.tokenConEseIssuer(306, iss, TENANT_A)).andExpect(status().isUnauthorized());
    }

    // el token del emisor local de pruebas no tiene forma de emisor de Entra: en multi-tenant no entra
    @Test
    void unTokenQueNoVieneDeEntraNoEntra() throws Exception {
        conToken(LlavesDePrueba.tokenValido(307)).andExpect(status().isUnauthorized());
    }

    private ResultActions conToken(String token) throws Exception {
        return mockMvc.perform(get("/api/partidas/ganadores")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
