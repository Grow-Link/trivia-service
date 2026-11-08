package com.growlink.trivia.security;

import com.growlink.trivia.LlavesDePrueba;
import com.growlink.trivia.adapter.security.JwksKeyLocator;
import com.growlink.trivia.adapter.security.OidcTokenVerificador;
import com.growlink.trivia.adapter.security.TokenInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Los puntos de decision del interceptor, uno por uno y sin levantar el contexto de Spring: el
// interceptor no hace mas que llamar a este verificador y, segun lo que pase, dejar entrar la
// conexion o tumbarla. El par de llaves RSA lo genera la prueba (ver LlavesDePrueba), que tambien
// publica el JWKS en un servidor local, asi que esto recorre el camino de verdad: bajar la llave
// publica del emisor y verificar la firma con ella.
class OidcTokenVerificadorTest {

    // uno nuevo por prueba a proposito: JwksKeyLocator cachea las llaves y limita cada cuanto
    // recarga, y con un cache compartido el resultado de una prueba dependeria de en que orden
    // corrieron las demas
    private OidcTokenVerificador verificador() {
        return new OidcTokenVerificador(LlavesDePrueba.issuer(), LlavesDePrueba.AUDIENCE, "rol",
                new JwksKeyLocator(LlavesDePrueba.jwksUri()));
    }

    @Test
    void unTokenValidoDevuelveElUsuarioIdDelSub() {
        assertThat(verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenValido(42))).isEqualTo(42L);
    }

    @Test
    void sinTokenNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe(null))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("no trae token");
    }

    @Test
    void unTokenSinElPrefijoBearerNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe(LlavesDePrueba.tokenValido(42)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("Bearer");
    }

    @Test
    void unTokenVencidoNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenVencido(42)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("vencido");
    }

    // la firma solo dice "esto lo firmo alguien con esa llave privada", no "esto lo firmo quien yo
    // espero": sin revisar el emisor entraria un token valido de otro tenant o de otro proveedor
    @Test
    void unTokenDeOtroEmisorNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenDeOtroEmisor(42)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("emisor");
    }

    // sin revisar la audiencia, el token que el mismo emisor le dio al usuario para CUALQUIER otra
    // aplicacion serviria para entrar a las salas de trivia
    @Test
    void unTokenParaOtraAudienciaNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenParaOtraAudiencia(42)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("audiencia");
    }

    // mismo emisor, misma audiencia, mismo kid, sin vencer... pero firmado con otra llave privada
    @Test
    void unTokenFirmadoPorOtroNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenFirmadoPorUnImpostor(42)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unTokenInventadoNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer esto.no.es-un-jwt"))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unTokenSinSubNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenSinSub()))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("sub");
    }

    // en este sistema el sub es el id numerico del usuario; un sub con otra forma significa que el
    // token viene de un emisor que no comparte el mismo padron de usuarios
    @Test
    void unSubQueNoEsUnIdNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenConSubQueNoEsUnId()))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("no es un id de usuario");
    }

    // el mismo verificador atiende muchos CONNECT: el JWKS se baja una vez y las llaves se
    // reusan del cache, no una llamada HTTP al emisor por cada conexion
    @Test
    void elJwksSeCacheaEntreConexiones() {
        OidcTokenVerificador unoSolo = verificador();

        assertThat(unoSolo.usuarioIdDe("Bearer " + LlavesDePrueba.tokenValido(1))).isEqualTo(1L);
        assertThat(unoSolo.usuarioIdDe("Bearer " + LlavesDePrueba.tokenValido(2))).isEqualTo(2L);
        assertThat(unoSolo.usuarioIdDe("Bearer " + LlavesDePrueba.tokenValido(3))).isEqualTo(3L);
    }

    @Test
    void siElJwksNoRespondeElTokenNoPasa() {
        OidcTokenVerificador sinEmisor = new OidcTokenVerificador(LlavesDePrueba.issuer(), LlavesDePrueba.AUDIENCE,
                "rol", new JwksKeyLocator("http://127.0.0.1:1/.well-known/jwks.json"));

        assertThatThrownBy(() -> sinEmisor.usuarioIdDe("Bearer " + LlavesDePrueba.tokenValido(42)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    // ---- algoritmo: aqui solo se acepta RS256 ----

    // El ataque de quitarle la firma al token: header con alg=none, los claims que el atacante
    // quiera y una tercera parte vacia. Un verificador que acepte JWT sin proteger se lo come
    @Test
    void unTokenConAlgNoneNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenConAlgNone(42)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unTokenFirmadoConHs256NoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenConHs256(42)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("RS256");
    }

    // La confusion de algoritmo: el atacante usa la llave PUBLICA del emisor (que cualquiera baja
    // del JWKS) como secreto de HMAC y se firma un token con el sub que quiera. Si el verificador
    // no fija el algoritmo, valida ese token contra la misma llave y lo da por bueno
    @Test
    void laConfusionDeAlgoritmoConLaLlavePublicaComoSecretoNoPasa() {
        String tokenDelAtacante = LlavesDePrueba.tokenConHs256FirmadoConLaLlavePublica(42);

        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + tokenDelAtacante))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("RS256");
    }

    // ---- kid y exp ----

    @Test
    void unTokenSinKidNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenSinKid(42)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("kid");
    }

    @Test
    void unTokenConUnKidQueElEmisorNoPublicaNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenConUnKidQueNoExiste(42)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("kid");
    }

    // jjwt solo compara exp si el claim viene: un token sin exp no vencería nunca, asi que se exige
    @Test
    void unTokenSinExpNoPasa() {
        assertThatThrownBy(() -> verificador().usuarioIdDe("Bearer " + LlavesDePrueba.tokenSinVencimiento(42)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("exp");
    }

    // ---- el rol, que es lo que mira exigirAdmin ----

    @Test
    void elRolSaleDelClaimDelToken() {
        assertThat(verificador().identidadDe("Bearer " + LlavesDePrueba.tokenValidoConRol(7, "ADMIN")))
                .isEqualTo(new OidcTokenVerificador.Identidad(7L, "ADMIN"));
    }

    // varios proveedores publican el rol como lista en vez de como texto suelto
    @Test
    void elRolTambienSeLeeCuandoVieneComoLista() {
        assertThat(verificador()
                .identidadDe("Bearer " + LlavesDePrueba.tokenValidoConRoles(7, List.of("USUARIO", "ADMIN")))
                .rol())
                .isEqualTo("ADMIN");
    }

    // un token sin el claim del rol sigue siendo valido (sirve para los endpoints que solo piden
    // sesion), simplemente no alcanza para el dashboard de admin
    @Test
    void unTokenSinRolSigueSiendoValidoPeroSinRol() {
        assertThat(verificador().identidadDe("Bearer " + LlavesDePrueba.tokenValido(7)))
                .isEqualTo(new OidcTokenVerificador.Identidad(7L, null));
    }
}
