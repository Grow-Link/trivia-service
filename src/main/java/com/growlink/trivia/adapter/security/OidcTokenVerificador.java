package com.growlink.trivia.adapter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;

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
//   sub       tiene que venir, pero YA NO se exige que sea un numero: el sub de Entra es un texto
//             opaco. Quien traduce el token a un usuarioId numerico y un rol es
//             ResolvedorDeIdentidad (del token mismo, o preguntandole a usuarios-service)
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
    private final ResolvedorDeIdentidad resolvedor;

    public OidcTokenVerificador(EmisorPermitido emisor, Set<String> audiencias,
                                ResolvedorDeIdentidad resolvedor, JwksKeyLocator keyLocator) {
        // parser SIN .unsecured(): asi jjwt rechaza por su cuenta los JWT con alg=none, que es el
        // otro ataque clasico (quitarle la firma al token y que el verificador lo acepte igual)
        this(emisor, audiencias, resolvedor, Jwts.parser().keyLocator(keyLocator).build());
    }

    // para las pruebas, que le pasan un parser ya armado con la llave publica del par RSA que
    // generan ellas mismas, sin tener que levantar un JWKS
    OidcTokenVerificador(EmisorPermitido emisor, Set<String> audiencias, ResolvedorDeIdentidad resolvedor,
                         JwtParser parser) {
        if (emisor == null) {
            throw new IllegalStateException("hay que decir que emisores se aceptan");
        }
        if (audiencias == null || audiencias.isEmpty()) {
            throw new IllegalStateException("hay que decir que audiencias se aceptan");
        }
        if (resolvedor == null) {
            throw new IllegalStateException("hay que decir de donde sale la identidad");
        }
        this.emisor = emisor;
        this.audiencias = Set.copyOf(audiencias);
        this.resolvedor = resolvedor;
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
        String jwt = authorization.substring(PREFIX.length()).trim();
        Claims claims = leer(jwt);
        exigirVencimiento(claims);
        emisor.exigir(claims);
        exigirAudiencia(claims);
        exigirSub(claims);
        // el token ya esta verificado; de aqui en adelante solo falta saber a quien pertenece
        return resolvedor.resolver(jwt, claims);
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

    // El sub tiene que venir siempre: es lo que identifica a la persona, y sin el no hay nada
    // que resolver. Lo que YA NO se exige aqui es que sea un numero: el sub de Entra es un texto
    // opaco, y traducirlo a un usuarioId es trabajo de ResolvedorDeIdentidad. El modo claims
    // sigue exigiendo que sea numerico, porque ahi el sub ES el id (ver IdentidadDesdeClaims)
    private void exigirSub(Claims claims) {
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new TokenInvalidoException("el token no trae sub, no se sabe que usuario es");
        }
    }
}
