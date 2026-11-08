package com.growlink.trivia.adapter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;

import java.util.Collection;
import java.util.Date;
import java.util.Set;

// Verificacion del JWT del proveedor de OIDC cuando auth.mode=oidc. Lo usan los DOS caminos de
// entrada, para que no haya una puerta mas floja que la otra:
//   - el CONNECT del WebSocket (StompAuthChannelInterceptor)
//   - los endpoints REST, a traves de TokenService
//
// Que se revisa, y por que cada cosa:
//   firma     con la llave publica que el emisor publica en su JWKS, y SOLO con RS256 (ver
//             JwksKeyLocator): aceptar otros algoritmos abre la puerta a la confusion de
//             algoritmo, que es el ataque clasico contra los verificadores de JWT
//   kid       el token tiene que decir con cual de las llaves del emisor se firmo (JwksKeyLocator)
//   exp       y tiene que traerlo: un token sin exp no vence NUNCA, y uno robado serviria para siempre
//   iss       la firma solo dice "esto lo firmo alguien con esa llave privada", no "lo firmo quien
//             yo espero": sin iss entraria un token valido de otro proveedor. Quien decide si el
//             emisor sirve es EmisorPermitido, porque con la app registrada como multi-tenant en
//             Entra el iss trae el tenant de quien inicia sesion y no se puede comparar contra
//             uno fijo (ahi entra tambien el tid)
//   aud       sin aud serviria el access token que el MISMO emisor le dio al usuario para
//             cualquier otra aplicacion. Se acepta una lista porque Entra pone "api://<client-id>"
//             o el "<client-id>" pelado segun como quede configurada la app
//   sub       la identidad del usuario, y de ningun otro lado
//
// Es aparte de TokenService a proposito: TokenService sabe validar los tokens HS256 que emite
// usuarios-service con un secreto compartido (el modo legacy), y esta clase los RS256 de un
// emisor externo, que firma con su llave privada y de quien aqui solo se conoce la publica.
public class OidcTokenVerificador {

    private static final String PREFIX = "Bearer ";
    public static final String ROL_CLAIM_POR_DEFECTO = "rol";

    private final JwtParser parser;
    private final EmisorPermitido emisor;
    private final Set<String> audiencias;
    private final String rolClaim;

    // lo mismo que TokenService.Sesion, pero resuelto desde el token del proveedor
    public record Identidad(Long usuarioId, String rol) {
    }

    public OidcTokenVerificador(EmisorPermitido emisor, Set<String> audiencias, String rolClaim,
                                JwksKeyLocator keyLocator) {
        // parser SIN .unsecured(): asi jjwt rechaza por su cuenta los JWT con alg=none, que es el
        // otro ataque clasico (quitarle la firma al token y que el verificador lo acepte igual)
        this(emisor, audiencias, rolClaim, Jwts.parser().keyLocator(keyLocator).build());
    }

    // para las pruebas, que le pasan un parser ya armado con la llave publica del par RSA que
    // generan ellas mismas, sin tener que levantar un JWKS
    OidcTokenVerificador(EmisorPermitido emisor, Set<String> audiencias, String rolClaim, JwtParser parser) {
        if (emisor == null) {
            throw new IllegalStateException("hay que decir que emisores se aceptan");
        }
        if (audiencias == null || audiencias.isEmpty()) {
            throw new IllegalStateException("hay que decir que audiencias se aceptan");
        }
        this.emisor = emisor;
        this.audiencias = Set.copyOf(audiencias);
        this.rolClaim = (rolClaim == null || rolClaim.isBlank()) ? ROL_CLAIM_POR_DEFECTO : rolClaim.trim();
        this.parser = parser;
    }

    // recibe el header Authorization completo ("Bearer ..."), igual que TokenService
    public Long usuarioIdDe(String authorization) {
        return identidadDe(authorization).usuarioId();
    }

    // Cualquier problema sale como TokenInvalidoException, con el motivo exacto en el mensaje para
    // que quede en el log del servidor. Quien llama decide que hacer con eso: el interceptor de
    // STOMP tumba la conexion, TokenService lo convierte en 401
    public Identidad identidadDe(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new TokenInvalidoException("la peticion no trae token");
        }
        if (!authorization.startsWith(PREFIX)) {
            throw new TokenInvalidoException("el token no viene como \"Bearer <jwt>\"");
        }
        Claims claims = leer(authorization.substring(PREFIX.length()).trim());
        exigirVencimiento(claims);
        emisor.exigir(claims);
        exigirAudiencia(claims);
        return new Identidad(usuarioIdDeSub(claims), rolDe(claims));
    }

    // jjwt ya rechaza por su cuenta el alg=none, la firma que no cuadra y el token vencido; los
    // casos se separan solo para que el log diga cual de todos fue
    private Claims leer(String jwt) {
        try {
            return parser.parseSignedClaims(jwt).getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenInvalidoException("el token esta vencido", e);
        } catch (TokenInvalidoException e) {
            throw e; // la tira JwksKeyLocator (kid o algoritmo), ya trae su propio motivo
        } catch (Exception e) {
            throw new TokenInvalidoException("el token no es un JWS firmado y valido: " + e.getMessage(), e);
        }
    }

    // jjwt solo compara exp si el claim viene; un token sin exp no vence nunca, asi que aqui se
    // exige. Un emisor de OIDC siempre lo pone, de modo que esto no rechaza nada legitimo
    private void exigirVencimiento(Claims claims) {
        Date exp = claims.getExpiration();
        if (exp == null) {
            throw new TokenInvalidoException("el token no trae exp, no vencería nunca");
        }
    }

    // Basta con que el aud del token coincida con UNA de las audiencias configuradas: Entra
    // publica "api://<client-id>" o el "<client-id>" pelado segun como quede registrada la app, y
    // tener las dos puestas evita que un cambio en el portal rompa el login. Lo que no se hace es
    // dejar la lista vacia: eso si seria aceptar tokens emitidos para cualquier otra aplicacion
    private void exigirAudiencia(Claims claims) {
        Set<String> delToken = claims.getAudience();
        if (delToken == null || delToken.stream().noneMatch(audiencias::contains)) {
            throw new TokenInvalidoException(
                    "el token no tiene ninguna de las audiencias " + audiencias + ", trae " + delToken);
        }
    }

    // En este sistema el sub es el id numerico del usuario (el mismo que emite usuarios-service),
    // asi que un sub con otra forma significa que el token viene de un emisor que no comparte el
    // mismo padron de usuarios: mejor rechazarlo en la puerta que dejarlo entrar y reventar despues
    private Long usuarioIdDeSub(Claims claims) {
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new TokenInvalidoException("el token no trae sub, no se sabe que usuario es");
        }
        try {
            return Long.valueOf(sub.trim());
        } catch (NumberFormatException e) {
            throw new TokenInvalidoException("el sub del token (" + sub + ") no es un id de usuario", e);
        }
    }

    // El rol es lo que mira TokenService.exigirAdmin para /api/metricas/dashboard. El nombre del
    // claim se configura (auth.oidc.rol-claim) porque cada proveedor lo llama distinto; por
    // defecto es "rol", el mismo que ya usa usuarios-service, para que el modo oidc y el legacy
    // decidan igual. Se aceptan las dos formas con que los proveedores lo publican: un texto
    // suelto o una lista de roles.
    //
    // Si el claim no viene, el rol queda null y exigirAdmin responde 403: un token sin rol no es
    // un token invalido (los endpoints que solo piden sesion siguen funcionando), simplemente no
    // alcanza para el dashboard de admin.
    private String rolDe(Claims claims) {
        Object valor = claims.get(rolClaim);
        if (valor == null) {
            return null;
        }
        if (valor instanceof Collection<?> roles) {
            return roles.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .filter(TokenService.ROL_ADMIN::equals)
                    .findFirst()
                    .orElseGet(() -> roles.isEmpty() ? null : String.valueOf(roles.iterator().next()));
        }
        return valor.toString();
    }
}
