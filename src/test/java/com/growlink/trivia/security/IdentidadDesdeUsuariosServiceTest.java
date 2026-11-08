package com.growlink.trivia.security;

import com.growlink.trivia.LlavesDePrueba;
import com.growlink.trivia.UsuariosServiceFalso;
import com.growlink.trivia.adapter.security.CacheDeIdentidades;
import com.growlink.trivia.adapter.security.EmisorPermitido;
import com.growlink.trivia.adapter.security.IdentidadDesdeUsuariosService;
import com.growlink.trivia.adapter.security.IdentidadNoDisponibleException;
import com.growlink.trivia.adapter.security.JwksKeyLocator;
import com.growlink.trivia.adapter.security.OidcTokenVerificador;
import com.growlink.trivia.adapter.security.TokenInvalidoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Set;

import static com.growlink.trivia.LlavesDePrueba.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El corazon de la HU: el sub de Entra es un texto opaco, asi que quien dice "este token es el
// usuario 42" es usuarios-service. Todo contra un usuarios-service simulado local
// (UsuariosServiceFalso) y las llaves RSA que genera la prueba: no se habla con Microsoft ni con
// un usuarios-service real.
class IdentidadDesdeUsuariosServiceTest {

    private UsuariosServiceFalso usuarios;

    @BeforeEach
    void levantarUsuarios() {
        usuarios = new UsuariosServiceFalso();
    }

    @AfterEach
    void bajarUsuarios() {
        usuarios.close();
    }

    private OidcTokenVerificador verificador(Duration duracionCache) {
        return verificador(duracionCache, usuarios.baseUrl());
    }

    private OidcTokenVerificador verificador(Duration duracionCache, String baseUrl) {
        var resolvedor = new IdentidadDesdeUsuariosService(RestClient.builder(), baseUrl,
                new CacheDeIdentidades(duracionCache, CacheDeIdentidades.TAMANO_MAXIMO_POR_DEFECTO));
        return new OidcTokenVerificador(EmisorPermitido.entraMultiTenant(Set.of()),
                Set.of(LlavesDePrueba.AUDIENCE), resolvedor, new JwksKeyLocator(LlavesDePrueba.jwksUri()));
    }

    // el sub tal como lo emite Entra: opaco, nada que ver con un id
    private String tokenDeEntraConSubOpaco() {
        return LlavesDePrueba.tokenDeEntraConSub("AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy782bbtaQ", TENANT_A);
    }

    // ------------------------------------------------------- el caso central

    @Test
    void elSubOpacoDeEntraSeTraduceAlUsuarioIdQueDiceUsuariosService() {
        usuarios.responde(42L, "USUARIO");

        assertThat(verificador(Duration.ofSeconds(60)).identidadDe("Bearer " + tokenDeEntraConSubOpaco()))
                .isEqualTo(new com.growlink.trivia.adapter.security.Identidad(42L, "USUARIO"));
    }

    // usuarios-service responde sobre el token de la persona, asi que hay que reenviarlo tal cual
    @Test
    void seLeReenviaElMismoTokenAUsuariosService() {
        usuarios.responde(42L, "USUARIO");
        String token = tokenDeEntraConSubOpaco();

        verificador(Duration.ofSeconds(60)).identidadDe("Bearer " + token);

        assertThat(usuarios.ultimoAuthorization()).isEqualTo("Bearer " + token);
    }

    @Test
    void losTresRolesLleganBien() {
        for (String rol : new String[]{"USUARIO", "PUBLICADOR", "ADMIN"}) {
            usuarios.responde(42L, rol);
            // un verificador nuevo por vuelta, para que el cache no devuelva el rol anterior
            assertThat(verificador(Duration.ofSeconds(60))
                    .identidadDe("Bearer " + tokenDeEntraConSubOpaco()).rol())
                    .isEqualTo(rol);
        }
    }

    // ------------------------------------------------------- cache

    @Test
    void dosPeticionesConElMismoTokenSoloPreguntanUnaVez() {
        usuarios.responde(42L, "USUARIO");
        OidcTokenVerificador verificador = verificador(Duration.ofSeconds(60));
        String authorization = "Bearer " + tokenDeEntraConSubOpaco();

        verificador.identidadDe(authorization);
        verificador.identidadDe(authorization);
        verificador.identidadDe(authorization);

        assertThat(usuarios.llamadas()).isEqualTo(1);
    }

    // tokens distintos son identidades distintas: el cache no puede confundirlos
    @Test
    void dosTokensDistintosPreguntanPorSeparado() {
        usuarios.responde(42L, "USUARIO");
        OidcTokenVerificador verificador = verificador(Duration.ofSeconds(60));

        verificador.identidadDe("Bearer " + LlavesDePrueba.tokenDeEntraConSub("sub-de-ana", TENANT_A));
        verificador.identidadDe("Bearer " + LlavesDePrueba.tokenDeEntraConSub("sub-de-beto", TENANT_A));

        assertThat(usuarios.llamadas()).isEqualTo(2);
    }

    @Test
    void elCacheVenceYSeVuelveAPreguntar() throws Exception {
        usuarios.responde(42L, "USUARIO");
        OidcTokenVerificador verificador = verificador(Duration.ofMillis(300));
        String authorization = "Bearer " + tokenDeEntraConSubOpaco();

        verificador.identidadDe(authorization);
        assertThat(usuarios.llamadas()).isEqualTo(1);

        Thread.sleep(400);
        verificador.identidadDe(authorization);
        assertThat(usuarios.llamadas()).isEqualTo(2);
    }

    // El exp manda sobre el TTL: si no, un token vencido seguiria entrando por el cache hasta que
    // se cumpliera el TTL, que es justo lo que el exp viene a evitar
    @Test
    void elCacheNuncaViveMasAllaDelExpDelToken() {
        usuarios.responde(42L, "USUARIO");
        // TTL de una hora, pero al token le queda un segundo
        CacheDeIdentidades cache = new CacheDeIdentidades(Duration.ofHours(1), 10);
        String token = LlavesDePrueba.tokenDeEntraQueVenceEn(1, "sub-opaco", TENANT_A);

        cache.guardar(token, new com.growlink.trivia.adapter.security.Identidad(42L, "USUARIO"),
                java.time.Instant.now().plusSeconds(1));

        // vigente ahora
        assertThat(cache.buscar(token)).isPresent();
        // y ya no cuando pase el exp
        await(1100);
        assertThat(cache.buscar(token)).isEmpty();
    }

    @Test
    void unTokenYaVencidoNiSeCachea() {
        CacheDeIdentidades cache = new CacheDeIdentidades(Duration.ofHours(1), 10);

        cache.guardar("token-cualquiera", new com.growlink.trivia.adapter.security.Identidad(42L, "USUARIO"),
                java.time.Instant.now().minusSeconds(1));

        assertThat(cache.buscar("token-cualquiera")).isEmpty();
        assertThat(cache.tamano()).isZero();
    }

    // sin tope, cada token distinto dejaria una entrada para siempre
    @Test
    void elCacheNoCreceSinLimite() {
        CacheDeIdentidades cache = new CacheDeIdentidades(Duration.ofHours(1), 3);

        for (int i = 0; i < 20; i++) {
            cache.guardar("token-" + i, new com.growlink.trivia.adapter.security.Identidad((long) i, "USUARIO"),
                    java.time.Instant.now().plusSeconds(600));
        }

        assertThat(cache.tamano()).isEqualTo(3);
    }

    // la clave es el hash y no el token: lo que se guarda en memoria no sirve para suplantar
    @Test
    void laClaveDelCacheEsElHashYNoElToken() {
        String token = tokenDeEntraConSubOpaco();

        String clave = CacheDeIdentidades.clave(token);

        assertThat(clave).doesNotContain(token).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(CacheDeIdentidades.clave(token)).isEqualTo(clave);
        assertThat(CacheDeIdentidades.clave(token + "x")).isNotEqualTo(clave);
    }

    // ------------------------------------------------------- errores

    // 401 de usuarios-service es el unico caso en que el problema es del token
    @Test
    void siUsuariosServiceDiceQueNoReconoceElTokenEsTokenInvalido() {
        usuarios.respondeConEstado(401);

        assertThatThrownBy(() -> verificador(Duration.ofSeconds(60))
                .identidadDe("Bearer " + tokenDeEntraConSubOpaco()))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("no reconoce el token");
    }

    @Test
    void un500NoAceptaElTokenYNoCaeAlSub() {
        usuarios.respondeConEstado(500);

        assertThatThrownBy(() -> verificador(Duration.ofSeconds(60))
                .identidadDe("Bearer " + tokenDeEntraConSubOpaco()))
                .isInstanceOf(IdentidadNoDisponibleException.class)
                .hasMessageContaining("500");
    }

    @Test
    void siUsuariosServiceEstaCaidoNoSeAceptaElToken() {
        usuarios.close(); // el puerto deja de aceptar

        assertThatThrownBy(() -> verificador(Duration.ofSeconds(60))
                .identidadDe("Bearer " + tokenDeEntraConSubOpaco()))
                .isInstanceOf(IdentidadNoDisponibleException.class);
    }

    // el timeout de lectura esta en 3 s; el falso demora 4
    @Test
    void siUsuariosServiceNoContestaATiempoNoSeAceptaElToken() {
        usuarios.responde(42L, "USUARIO").demora(4_000);

        assertThatThrownBy(() -> verificador(Duration.ofSeconds(60))
                .identidadDe("Bearer " + tokenDeEntraConSubOpaco()))
                .isInstanceOf(IdentidadNoDisponibleException.class);
    }

    @Test
    void unaRespuestaSinUsuarioIdNoSirve() {
        usuarios.respondeCuerpo("{\"rol\":\"USUARIO\"}");

        assertThatThrownBy(() -> verificador(Duration.ofSeconds(60))
                .identidadDe("Bearer " + tokenDeEntraConSubOpaco()))
                .isInstanceOf(IdentidadNoDisponibleException.class)
                .hasMessageContaining("sin usuarioId");
    }

    // ------------------------------------------------------- resiliencia

    // Lo que hace que una caida de usuarios-service no tumbe una partida en curso: mientras la
    // entrada siga vigente, los que ya entraron siguen entrando
    @Test
    void conValorEnCacheSigueFuncionandoAunqueUsuariosServiceSeCaiga() {
        usuarios.responde(42L, "ADMIN");
        OidcTokenVerificador verificador = verificador(Duration.ofSeconds(60));
        String authorization = "Bearer " + tokenDeEntraConSubOpaco();

        assertThat(verificador.identidadDe(authorization).usuarioId()).isEqualTo(42L);

        usuarios.close();

        assertThat(verificador.identidadDe(authorization).usuarioId()).isEqualTo(42L);
        assertThat(verificador.identidadDe(authorization).rol()).isEqualTo("ADMIN");
    }

    // pero un token que no estaba en el cache no entra de arrimado
    @Test
    void conUsuariosServiceCaidoUnTokenNuevoNoEntraPorElCacheDeOtro() {
        usuarios.responde(42L, "USUARIO");
        OidcTokenVerificador verificador = verificador(Duration.ofSeconds(60));

        verificador.identidadDe("Bearer " + LlavesDePrueba.tokenDeEntraConSub("sub-de-ana", TENANT_A));
        usuarios.close();

        assertThatThrownBy(() -> verificador
                .identidadDe("Bearer " + LlavesDePrueba.tokenDeEntraConSub("sub-de-beto", TENANT_A)))
                .isInstanceOf(IdentidadNoDisponibleException.class);
    }

    // el token igual se verifica ANTES de preguntar: un token invalido no gasta una llamada
    @Test
    void unTokenInvalidoNiLlegaAPreguntarle() {
        usuarios.responde(42L, "USUARIO");

        assertThatThrownBy(() -> verificador(Duration.ofSeconds(60))
                .identidadDe("Bearer " + LlavesDePrueba.tokenVencido(42)))
                .isInstanceOf(TokenInvalidoException.class);

        assertThat(usuarios.llamadas()).isZero();
    }

    private static void await(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
