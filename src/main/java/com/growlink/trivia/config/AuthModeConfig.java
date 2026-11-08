package com.growlink.trivia.config;

import com.growlink.trivia.adapter.security.JwksKeyLocator;
import com.growlink.trivia.adapter.security.OidcTokenVerificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// El interruptor auth.mode, uno solo para las dos puertas de entrada del servicio:
//
//   legacy (por defecto) -> no se crea ningun bean de aqui. El CONNECT de STOMP no pide token y
//                           la identidad del jugador sigue viajando en el payload del mensaje;
//                           los endpoints REST siguen validando el JWT HS256 de usuarios-service
//                           con el secreto compartido. Nada de lo que ya funciona cambia.
//   oidc                 -> se crea OidcTokenVerificador, y lo usan LOS DOS caminos: el
//                           interceptor del canal de entrada de STOMP (StompAuthChannelInterceptor,
//                           que lo engancha WebSocketConfig) y TokenService, por donde pasan todos
//                           los endpoints REST que necesitan saber quien llama. Asi no queda una
//                           puerta validando el token del proveedor y la otra sin hacerlo.
//
// El modo se valida al arrancar: un valor mal escrito (un "OIDC " con espacio, un "oauth") tiene
// que tumbar el arranque, no quedarse callado cayendo a legacy, porque eso seria desplegar sin
// autenticacion creyendo que esta puesta.
@Configuration
public class AuthModeConfig {

    public static final String MODO_LEGACY = "legacy";
    public static final String MODO_OIDC = "oidc";

    private static final Logger log = LoggerFactory.getLogger(AuthModeConfig.class);

    public AuthModeConfig(@Value("${auth.mode:legacy}") String modo) {
        if (!MODO_LEGACY.equals(modo) && !MODO_OIDC.equals(modo)) {
            throw new IllegalStateException(
                    "auth.mode debe ser \"" + MODO_LEGACY + "\" u \"" + MODO_OIDC + "\", llego \"" + modo + "\"");
        }
        log.info("auth.mode={} ({})", modo, MODO_OIDC.equals(modo)
                ? "el CONNECT de STOMP y los endpoints REST exigen un JWT valido del proveedor"
                : "la identidad del jugador viaja en el mensaje y el REST valida el JWT de usuarios-service");
    }

    // La llave publica se lee del JWKS del emisor, no se configura a mano: asi una rotacion de
    // llaves del proveedor no obliga a redeplegar este servicio. Si no se configura jwks-uri se
    // asume la ruta estandar de OIDC bajo el issuer
    @Bean
    @ConditionalOnProperty(name = "auth.mode", havingValue = MODO_OIDC)
    public OidcTokenVerificador oidcTokenVerificador(
            @Value("${auth.oidc.issuer-uri:}") String issuerUri,
            @Value("${auth.oidc.audience:}") String audience,
            @Value("${auth.oidc.jwks-uri:}") String jwksUri,
            @Value("${auth.oidc.rol-claim:rol}") String rolClaim) {
        // en modo oidc estas dos no son opcionales: sin emisor cualquiera que tenga una llave
        // privada propia puede firmarse un token, y sin audiencia sirve el token que el mismo
        // emisor dio para otra aplicacion
        exigir(issuerUri, "auth.oidc.issuer-uri (AUTH_ISSUER_URI)");
        exigir(audience, "auth.oidc.audience (AUTH_AUDIENCE)");

        String uriDelJwks = (jwksUri == null || jwksUri.isBlank())
                ? issuerUri.replaceAll("/+$", "") + "/.well-known/jwks.json"
                : jwksUri.trim();
        log.info("modo oidc: emisor={} audiencia={} jwks={} claim del rol={}",
                issuerUri, audience, uriDelJwks, rolClaim);
        return new OidcTokenVerificador(issuerUri.trim(), audience.trim(), rolClaim,
                new JwksKeyLocator(uriDelJwks));
    }

    @Bean
    @ConditionalOnProperty(name = "auth.mode", havingValue = MODO_OIDC)
    public StompAuthChannelInterceptor stompAuthChannelInterceptor(OidcTokenVerificador verificador) {
        return new StompAuthChannelInterceptor(verificador);
    }

    private static void exigir(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("con auth.mode=" + MODO_OIDC + " hay que configurar " + nombre);
        }
    }
}
