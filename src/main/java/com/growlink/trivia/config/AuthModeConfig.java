package com.growlink.trivia.config;

import com.growlink.trivia.adapter.security.CacheDeIdentidades;
import com.growlink.trivia.adapter.security.EmisorPermitido;
import com.growlink.trivia.adapter.security.IdentidadDesdeClaims;
import com.growlink.trivia.adapter.security.IdentidadDesdeUsuariosService;
import com.growlink.trivia.adapter.security.JwksKeyLocator;
import com.growlink.trivia.adapter.security.OidcTokenVerificador;
import com.growlink.trivia.adapter.security.ResolvedorDeIdentidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

    // de donde sale el usuarioId numerico y el rol, ver ResolvedorDeIdentidad
    public static final String IDENTIDAD_USUARIOS_SERVICE = "usuarios-service";
    public static final String IDENTIDAD_CLAIMS = "claims";

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

    // JWKS de Entra para una app multi-tenant: el endpoint "common" publica las llaves con las
    // que firma para todos los directorios, asi que sirve para cualquier tenant
    static final String JWKS_ENTRA_COMMON = "https://login.microsoftonline.com/common/discovery/v2.0/keys";

    // La llave publica se lee del JWKS del emisor, no se configura a mano: asi una rotacion de
    // llaves del proveedor no obliga a redeplegar este servicio.
    //
    // Que cambia con auth.multi-tenant=true:
    //   - el emisor ya no es uno fijo (ver EmisorPermitido), asi que issuer-uri deja de ser
    //     obligatorio y se ignora: con la app registrada para cuentas de cualquier directorio, el
    //     iss trae el tenant de quien inicia sesion
    //   - el jwks-uri por defecto pasa a ser el "common" de Entra, porque no hay un solo tenant
    //     del que bajar las llaves
    // Con false (el valor por defecto) todo queda igual que antes: emisor fijo obligatorio y el
    // jwks estandar de OIDC colgado de ese emisor.
    @Bean
    @ConditionalOnProperty(name = "auth.mode", havingValue = MODO_OIDC)
    public OidcTokenVerificador oidcTokenVerificador(
            @Value("${auth.multi-tenant:false}") boolean multiTenant,
            @Value("${auth.oidc.issuer-uri:}") String issuerUri,
            @Value("${auth.oidc.audience:}") String audience,
            @Value("${auth.oidc.allowed-tenants:}") String tenantsPermitidos,
            @Value("${auth.oidc.jwks-uri:}") String jwksUri,
            @Value("${auth.oidc.rol-claim:rol}") String rolClaim,
            ResolvedorDeIdentidad resolvedor) {
        // la audiencia no es opcional en ningun modo: sin ella sirve el token que el mismo
        // emisor le dio al usuario para cualquier otra aplicacion
        exigir(audience, "auth.oidc.audience (AUTH_AUDIENCE)");
        Set<String> audiencias = lista(audience);

        EmisorPermitido emisor;
        String jwksPorDefecto;
        if (multiTenant) {
            if (!tenantsPermitidos.isBlank()) {
                log.info("modo oidc multi-tenant limitado a los tenants {}", tenantsPermitidos);
            }
            // si viene un GUID mal escrito, esto tumba el arranque; mejor eso que descubrirlo
            // cuando un usuario de ese directorio no pueda entrar
            emisor = EmisorPermitido.entraMultiTenant(lista(tenantsPermitidos));
            jwksPorDefecto = JWKS_ENTRA_COMMON;
        } else {
            // sin emisor fijo cualquiera que tenga una llave privada propia puede firmarse un token
            exigir(issuerUri, "auth.oidc.issuer-uri (AUTH_ISSUER_URI)");
            emisor = EmisorPermitido.fijo(issuerUri);
            jwksPorDefecto = issuerUri.trim().replaceAll("/+$", "") + "/.well-known/jwks.json";
        }

        String uriDelJwks = (jwksUri == null || jwksUri.isBlank()) ? jwksPorDefecto : jwksUri.trim();
        log.info("modo oidc: multi-tenant={} emisor={} audiencias={} jwks={}",
                multiTenant,
                multiTenant ? "cualquier tenant de Entra v2" + (emisor.tenantsPermitidos().isEmpty()
                        ? "" : " dentro de " + emisor.tenantsPermitidos()) : issuerUri,
                audiencias, uriDelJwks);
        return new OidcTokenVerificador(emisor, audiencias, resolvedor, new JwksKeyLocator(uriDelJwks));
    }

    // De donde sale la identidad del que llama, una vez el token ya esta verificado.
    //
    // Por defecto usuarios-service, porque es el unico modo que funciona con tokens reales de
    // Entra: su sub es un texto opaco y el usuarioId numerico que este servicio guarda en sus
    // tablas vive alla. El modo claims se conserva para desarrollo y pruebas sin usuarios-service
    // arriba, y solo sirve cuando el emisor comparte el padron de usuarios de GrowLink.
    //
    // Un valor que no sea ninguno de los dos tumba el arranque: caer callado a claims seria
    // quedar aceptando el sub del token creyendo que se esta preguntando a usuarios-service.
    @Bean
    @ConditionalOnProperty(name = "auth.mode", havingValue = MODO_OIDC)
    public ResolvedorDeIdentidad resolvedorDeIdentidad(
            RestClient.Builder builder,
            @Value("${auth.identity-source:" + IDENTIDAD_USUARIOS_SERVICE + "}") String fuente,
            @Value("${auth.usuarios-service-url:}") String usuariosServiceUrl,
            @Value("${auth.identity-cache-seconds:60}") long cacheSegundos,
            @Value("${auth.oidc.rol-claim:rol}") String rolClaim) {
        if (IDENTIDAD_CLAIMS.equals(fuente)) {
            log.warn("auth.identity-source=claims: la identidad sale del token y NO de usuarios-service."
                    + " Esto solo funciona si el sub del token es el usuarioId numerico, que no es el"
                    + " caso de los tokens de Microsoft Entra");
            return new IdentidadDesdeClaims(rolClaim);
        }
        if (!IDENTIDAD_USUARIOS_SERVICE.equals(fuente)) {
            throw new IllegalStateException("auth.identity-source debe ser \"" + IDENTIDAD_USUARIOS_SERVICE
                    + "\" o \"" + IDENTIDAD_CLAIMS + "\", llego \"" + fuente + "\"");
        }
        exigir(usuariosServiceUrl, "auth.usuarios-service-url (USUARIOS_SERVICE_URL)");

        log.info("la identidad se resuelve contra {} (cache de {} s, maximo {} entradas)",
                usuariosServiceUrl, cacheSegundos, CacheDeIdentidades.TAMANO_MAXIMO_POR_DEFECTO);
        return new IdentidadDesdeUsuariosService(builder, usuariosServiceUrl.trim(),
                new CacheDeIdentidades(Duration.ofSeconds(cacheSegundos),
                        CacheDeIdentidades.TAMANO_MAXIMO_POR_DEFECTO));
    }

    // "a, b , c" -> [a, b, c], sin vacios. Lo usan la audiencia y la lista de tenants
    private static Set<String> lista(String valor) {
        if (valor == null || valor.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(valor.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
