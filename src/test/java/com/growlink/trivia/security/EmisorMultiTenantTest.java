package com.growlink.trivia.security;

import com.growlink.trivia.LlavesDePrueba;
import com.growlink.trivia.adapter.security.EmisorPermitido;
import com.growlink.trivia.adapter.security.IdentidadDesdeClaims;
import com.growlink.trivia.adapter.security.JwksKeyLocator;
import com.growlink.trivia.adapter.security.OidcTokenVerificador;
import com.growlink.trivia.adapter.security.TokenInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.growlink.trivia.LlavesDePrueba.TENANT_A;
import static com.growlink.trivia.LlavesDePrueba.TENANT_B;
import static com.growlink.trivia.LlavesDePrueba.TENANT_CUENTAS_PERSONALES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// La app se registra en Entra ID como multi-tenant (cuentas de cualquier directorio y cuentas
// personales de Microsoft), asi que el iss del token trae el tenant de QUIEN inicia sesion. Estas
// pruebas son las que sostienen que "aceptar cualquier tenant" siga siendo seguro:
//
//   - solo el emisor v2 de Entra, con el patron completo y anclado
//   - el tid tiene que cuadrar con el GUID que viene DENTRO del iss
//   - y, si se configura, solo los tenants de la lista
//
// Todo con el par de llaves RSA que genera la prueba y el JWKS local de LlavesDePrueba: no se
// habla con Microsoft en ningun momento.
class EmisorMultiTenantTest {

    // uno nuevo por prueba: JwksKeyLocator cachea las llaves, y con un cache compartido el
    // resultado de una prueba dependeria del orden en que corrieron las demas
    private OidcTokenVerificador multiTenant(Set<String> tenantsPermitidos) {
        return new OidcTokenVerificador(EmisorPermitido.entraMultiTenant(tenantsPermitidos),
                Set.of(LlavesDePrueba.AUDIENCE), new IdentidadDesdeClaims("rol"), new JwksKeyLocator(LlavesDePrueba.jwksUri()));
    }

    private OidcTokenVerificador multiTenant() {
        return multiTenant(Set.of());
    }

    // ------------------------------------------------------- cualquier tenant

    @Test
    void unTokenDelTenantAEsAceptado() {
        assertThat(multiTenant().usuarioIdDe("Bearer " + LlavesDePrueba.tokenDeEntra(11, TENANT_A)))
                .isEqualTo(11L);
    }

    @Test
    void unTokenDelTenantBTambienEsAceptado() {
        assertThat(multiTenant().usuarioIdDe("Bearer " + LlavesDePrueba.tokenDeEntra(22, TENANT_B)))
                .isEqualTo(22L);
    }

    // las cuentas personales de Microsoft no son un caso aparte: Entra las emite con su propio
    // tenant, que cae por el mismo patron
    @Test
    void unTokenDeUnaCuentaPersonalDeMicrosoftEsAceptado() {
        assertThat(multiTenant()
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenDeEntra(33, TENANT_CUENTAS_PERSONALES)))
                .isEqualTo(33L);
    }

    // ------------------------------------------------------- el tid

    // Si no se exigiera el tid, aceptar "cualquier tenant" seria creerle al iss a secas. Con el,
    // el token tiene que ser internamente consistente
    @Test
    void unTidDistintoAlDelIssNoPasa() {
        String token = LlavesDePrueba.tokenDeEntraConTidDeOtroTenant(44, TENANT_A, TENANT_B);

        assertThatThrownBy(() -> multiTenant().usuarioIdDe("Bearer " + token))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("tid");
    }

    @Test
    void unTokenSinTidNoPasa() {
        String token = LlavesDePrueba.tokenDeEntraSinTid(44, TENANT_A);

        assertThatThrownBy(() -> multiTenant().usuarioIdDe("Bearer " + token))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("tid");
    }

    // ------------------------------------------------------- emisores que se parecen

    // el patron esta anclado justamente para esto: un dominio que empieza igual que el de
    // Microsoft pero sigue con otra cosa
    @Test
    void unHostParecidoAlDeMicrosoftNoPasa() {
        String iss = "https://login.microsoftonline.com.evil.com/" + TENANT_A + "/v2.0";

        assertThatThrownBy(() -> multiTenant()
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenConEseIssuer(55, iss, TENANT_A)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("emisor");
    }

    @Test
    void unSubdominioDeOtroDominioNoPasa() {
        String iss = "https://login.microsoftonline.com.attacker.test/" + TENANT_A + "/v2.0";

        assertThatThrownBy(() -> multiTenant()
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenConEseIssuer(55, iss, TENANT_A)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void elMismoEmisorPorHttpNoPasa() {
        String iss = "http://login.microsoftonline.com/" + TENANT_A + "/v2.0";

        assertThatThrownBy(() -> multiTenant()
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenConEseIssuer(55, iss, TENANT_A)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    // el emisor v1 de Entra (sts.windows.net) y el v2 sin /v2.0 no son lo que se valida aqui
    @Test
    void unEmisorSinV20NoPasa() {
        String iss = "https://login.microsoftonline.com/" + TENANT_A;

        assertThatThrownBy(() -> multiTenant()
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenConEseIssuer(55, iss, TENANT_A)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unEmisorConBasuraPegadaAlFinalNoPasa() {
        String iss = "https://login.microsoftonline.com/" + TENANT_A + "/v2.0/../../otro";

        assertThatThrownBy(() -> multiTenant()
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenConEseIssuer(55, iss, TENANT_A)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    // el patron pide un GUID, no "common" ni "organizations": esos valen para pedir el token,
    // pero el iss del token que vuelve siempre trae el directorio concreto
    @Test
    void unEmisorConCommonEnVezDelTenantNoPasa() {
        String iss = "https://login.microsoftonline.com/common/v2.0";

        assertThatThrownBy(() -> multiTenant()
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenConEseIssuer(55, iss, TENANT_A)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unTenantQueNoEsUnGuidNoPasa() {
        String iss = "https://login.microsoftonline.com/no-soy-un-guid/v2.0";

        assertThatThrownBy(() -> multiTenant()
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenConEseIssuer(55, iss, "no-soy-un-guid")))
                .isInstanceOf(TokenInvalidoException.class);
    }

    // ------------------------------------------------------- lista de tenants permitidos

    @Test
    void conListaDeTenantsElDeLaListaEntra() {
        assertThat(multiTenant(Set.of(TENANT_A))
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenDeEntra(66, TENANT_A)))
                .isEqualTo(66L);
    }

    @Test
    void conListaDeTenantsElQueNoEstaEnLaListaNoEntra() {
        assertThatThrownBy(() -> multiTenant(Set.of(TENANT_A))
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenDeEntra(66, TENANT_B)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("allowed-tenants");
    }

    @Test
    void laListaConVariosTenantsAceptaCualquieraDeEllos() {
        OidcTokenVerificador verificador = multiTenant(Set.of(TENANT_A, TENANT_B));

        assertThat(verificador.usuarioIdDe("Bearer " + LlavesDePrueba.tokenDeEntra(1, TENANT_A))).isEqualTo(1L);
        assertThat(verificador.usuarioIdDe("Bearer " + LlavesDePrueba.tokenDeEntra(2, TENANT_B))).isEqualTo(2L);
    }

    // el GUID se compara sin distinguir mayusculas, que es como se comparan los GUID
    @Test
    void laListaNoDistingueMayusculas() {
        assertThat(multiTenant(Set.of(TENANT_A.toUpperCase()))
                .usuarioIdDe("Bearer " + LlavesDePrueba.tokenDeEntra(77, TENANT_A)))
                .isEqualTo(77L);
    }

    // un GUID mal escrito en la configuracion tiene que tumbar el arranque, no quedarse callado
    // rechazando en silencio a ese directorio
    @Test
    void unaListaConAlgoQueNoEsUnGuidNoArranca() {
        assertThatThrownBy(() -> EmisorPermitido.entraMultiTenant(Set.of("no-es-un-guid")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_ALLOWED_TENANTS");
    }

    // ------------------------------------------------------- audiencia como lista

    // Entra pone "api://<client-id>" o el "<client-id>" pelado segun como quede registrada la
    // app; con las dos configuradas, cualquiera de las dos formas entra
    @Test
    void laAudienciaPasaSiCoincideConCualquieraDeLaLista() {
        String clientId = "4c1a9f70-0000-0000-0000-abcdefabcdef";
        OidcTokenVerificador verificador = new OidcTokenVerificador(
                EmisorPermitido.entraMultiTenant(Set.of()),
                Set.of("api://" + clientId, clientId),
                new IdentidadDesdeClaims("rol"), new JwksKeyLocator(LlavesDePrueba.jwksUri()));

        assertThat(verificador.usuarioIdDe("Bearer "
                + LlavesDePrueba.tokenDeEntraConAudiencia(88, TENANT_A, "api://" + clientId))).isEqualTo(88L);
        assertThat(verificador.usuarioIdDe("Bearer "
                + LlavesDePrueba.tokenDeEntraConAudiencia(88, TENANT_A, clientId))).isEqualTo(88L);
    }

    @Test
    void unaAudienciaQueNoEstaEnLaListaNoPasa() {
        OidcTokenVerificador verificador = new OidcTokenVerificador(
                EmisorPermitido.entraMultiTenant(Set.of()),
                Set.of("api://uno", "api://otro"),
                new IdentidadDesdeClaims("rol"), new JwksKeyLocator(LlavesDePrueba.jwksUri()));

        assertThatThrownBy(() -> verificador.usuarioIdDe("Bearer "
                + LlavesDePrueba.tokenDeEntraConAudiencia(88, TENANT_A, "api://tercero")))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("audiencia");
    }

    // dejar la lista de audiencias vacia seria aceptar tokens emitidos para cualquier otra
    // aplicacion del mismo proveedor, asi que no se permite armar el verificador asi
    @Test
    void noSePuedeArmarElVerificadorSinAudiencias() {
        assertThatThrownBy(() -> new OidcTokenVerificador(EmisorPermitido.entraMultiTenant(Set.of()),
                Set.of(), new IdentidadDesdeClaims("rol"), new JwksKeyLocator(LlavesDePrueba.jwksUri())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audiencias");
    }

    // ------------------------------------------------------- single-tenant, el modo de siempre

    // Con multi-tenant=false nada de lo anterior aplica: el iss se compara tal cual contra el
    // configurado, sin tid y sin patrones
    @Test
    void enModoSingleTenantElEmisorFijoSigueSiendoLoQueManda() {
        OidcTokenVerificador singleTenant = new OidcTokenVerificador(
                EmisorPermitido.fijo(LlavesDePrueba.issuer()),
                Set.of(LlavesDePrueba.AUDIENCE), new IdentidadDesdeClaims("rol"), new JwksKeyLocator(LlavesDePrueba.jwksUri()));

        // el token del emisor configurado entra, y no le hace falta traer tid
        assertThat(singleTenant.usuarioIdDe("Bearer " + LlavesDePrueba.tokenValido(99))).isEqualTo(99L);

        // y un token de Entra, perfectamente valido para el modo multi-tenant, aqui no
        assertThatThrownBy(() -> singleTenant.usuarioIdDe("Bearer " + LlavesDePrueba.tokenDeEntra(99, TENANT_A)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("emisor");
    }

    @Test
    void enModoSingleTenantUnTokenSinIssNoPasa() {
        assertThatThrownBy(() -> EmisorPermitido.fijo("   "))
                .isInstanceOf(IllegalStateException.class);
    }
}
