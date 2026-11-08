package com.growlink.trivia.security;

import com.growlink.trivia.adapter.security.IdentidadDesdeClaims;
import com.growlink.trivia.adapter.security.IdentidadDesdeUsuariosService;
import com.growlink.trivia.config.AuthModeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El fail-fast de la configuracion. Se prueba llamando a AuthModeConfig directamente y no
// levantando un contexto de Spring que falla: es mas rapido y el mensaje de error se lee derecho.
//
// Por que importa que tumbe el arranque y no se arregle solo: si un auth.identity-source mal
// escrito cayera callado a "claims", el servicio quedaria aceptando el sub del token mientras uno
// cree que esta preguntandole a usuarios-service. Y si la URL faltante se tomara de un valor por
// defecto, apuntaria a un localhost que en produccion no existe.
class ConfiguracionDeIdentidadTest {

    private final AuthModeConfig config = new AuthModeConfig("oidc");

    @Test
    void elValorPorDefectoEsPreguntarleAUsuariosService() {
        var resolvedor = config.resolvedorDeIdentidad(RestClient.builder(),
                AuthModeConfig.IDENTIDAD_USUARIOS_SERVICE, "http://usuarios:8080", 60, "rol");

        assertThat(resolvedor).isInstanceOf(IdentidadDesdeUsuariosService.class);
    }

    @Test
    void elModoClaimsConservaElComportamientoAnterior() {
        var resolvedor = config.resolvedorDeIdentidad(RestClient.builder(),
                AuthModeConfig.IDENTIDAD_CLAIMS, "", 60, "rol");

        assertThat(resolvedor).isInstanceOf(IdentidadDesdeClaims.class);
    }

    // el modo claims no necesita la URL, y por eso no se le exige
    @Test
    void elModoClaimsNoExigeLaUrlDeUsuariosService() {
        assertThat(config.resolvedorDeIdentidad(RestClient.builder(),
                AuthModeConfig.IDENTIDAD_CLAIMS, "", 60, "rol")).isNotNull();
    }

    @Test
    void unaFuenteDeIdentidadInvalidaTumbaElArranque() {
        assertThatThrownBy(() -> config.resolvedorDeIdentidad(RestClient.builder(),
                "lo-que-se-me-ocurra", "http://usuarios:8080", 60, "rol"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.identity-source");
    }

    @Test
    void sinLaUrlDeUsuariosServiceNoArranca() {
        assertThatThrownBy(() -> config.resolvedorDeIdentidad(RestClient.builder(),
                AuthModeConfig.IDENTIDAD_USUARIOS_SERVICE, "   ", 60, "rol"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("USUARIOS_SERVICE_URL");
    }

    // el interruptor de siempre, que ya existia: un auth.mode mal escrito tampoco arranca
    @Test
    void unAuthModeInvalidoTumbaElArranque() {
        assertThatThrownBy(() -> new AuthModeConfig("oauth"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.mode");
    }
}
