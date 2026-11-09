package com.growlink.trivia.adapter.security;

import com.growlink.trivia.application.NoAutenticadoException;
import com.growlink.trivia.application.NoAutorizadoException;
import com.growlink.trivia.application.UsuariosServiceNoDisponibleException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

// Por aqui pasan TODOS los endpoints REST que necesitan saber quien hace la peticion
// (/api/retos, /api/partidas, /api/preguntas, /api/metricas). trivia-service no emite tokens,
// solo los valida.
//
// Tiene dos modos, el mismo interruptor auth.mode que el WebSocket (ver AuthModeConfig), para que
// no quede una puerta mas floja que la otra:
//
//   legacy (por defecto) -> valida la firma HS256 del JWT que emite usuarios-service con el
//                           secreto compartido y saca el userId del sub. Exactamente lo que
//                           hacia antes, sin un solo cambio de comportamiento.
//   oidc                 -> delega en OidcTokenVerificador, el mismo que usa el CONNECT de STOMP:
//                           firma RS256 contra el JWKS del emisor, kid, exp, iss y aud.
//
// En los dos modos un token que no pasa sale como NoAutenticadoException, que GlobalExceptionHandler
// ya traduce a 401. No se usa Spring Security para no meterse con el WebSocket ni con /api/salas.
@Component
public class TokenService {

    public static final String ROL_ADMIN = "ADMIN";

    private static final String PREFIX = "Bearer ";
    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    public record Sesion(Long usuarioId, String rol) {
    }

    private final SecretKey key;

    // presente solo con auth.mode=oidc; vacio en legacy, y entonces nada de esto se ejecuta
    private final Optional<OidcTokenVerificador> oidc;

    public TokenService(@Value("${growlink.jwt.secret}") String secret, Optional<OidcTokenVerificador> oidc) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.oidc = oidc;
    }

    // recibe el header Authorization completo ("Bearer ...")
    public Long usuarioIdDe(String authorization) {
        return sesionDe(authorization).usuarioId();
    }

    public Sesion sesionDe(String authorization) {
        return oidc.isPresent() ? sesionOidc(authorization, oidc.get()) : sesionLegacy(authorization);
    }

    // El rechazo queda en el log con el motivo exacto (y con el X-Request-Id de la peticion, que
    // RequestIdFilter ya dejo en el MDC), porque al cliente solo le vuelve un 401 pelado: sin esta
    // linea, un token mal configurado en un ambiente es imposible de diagnosticar
    private Sesion sesionOidc(String authorization, OidcTokenVerificador verificador) {
        try {
            Identidad identidad = verificador.identidadDe(authorization);
            return new Sesion(identidad.usuarioId(), identidad.rol());
        } catch (TokenInvalidoException e) {
            log.warn("peticion REST rechazada con 401: {}", e.getMessage());
            throw new NoAutenticadoException();
        } catch (IdentidadNoDisponibleException e) {
            // el token esta bien, lo que fallo es usuarios-service: 503 y no 401. Mandar un 401
            // aqui haria que el frontend pida un login nuevo por una caida de un servicio de
            // atras, que no arregla nada, y de paso esconderia la caida
            log.error("peticion REST rechazada con 503, no se pudo resolver la identidad: {}", e.getMessage());
            throw new UsuariosServiceNoDisponibleException();
        }
    }

    // el camino de siempre, intacto: HS256 con el secreto que comparte con usuarios-service
    private Sesion sesionLegacy(String authorization) {
        if (authorization == null || !authorization.startsWith(PREFIX)) {
            throw new NoAutenticadoException();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(authorization.substring(PREFIX.length()))
                    .getPayload();
            return new Sesion(Long.valueOf(claims.getSubject()), claims.get("rol", String.class));
        } catch (Exception e) {
            throw new NoAutenticadoException();
        }
    }

    // el rol viene firmado dentro del token, nadie lo puede cambiar a mano. En modo oidc sale del
    // claim que diga auth.oidc.rol-claim ("rol" por defecto, el mismo nombre que usa el modo legacy)
    public void exigirAdmin(String authorization) {
        if (!ROL_ADMIN.equals(sesionDe(authorization).rol())) {
            throw new NoAutorizadoException();
        }
    }
}
