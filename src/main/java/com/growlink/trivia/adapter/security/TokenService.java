package com.growlink.trivia.adapter.security;

import com.growlink.trivia.application.NoAutenticadoException;
import com.growlink.trivia.application.NoAutorizadoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

// trivia-service no emite tokens, solo valida la firma del JWT que emite
// usuarios-service (mismo secreto compartido) y saca el userId del sub.
// No se usa Spring Security para no meterse con el WebSocket ni con /api/salas,
// solo lo usan los endpoints que necesitan saber quien hace la peticion.
@Component
public class TokenService {

    private static final String PREFIX = "Bearer ";

    public record Sesion(Long usuarioId, String rol) {
    }

    private final SecretKey key;

    public TokenService(@Value("${growlink.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // recibe el header Authorization completo ("Bearer ...")
    public Long usuarioIdDe(String authorization) {
        return sesionDe(authorization).usuarioId();
    }

    public Sesion sesionDe(String authorization) {
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

    // el rol viene firmado dentro del token, nadie lo puede cambiar a mano
    public void exigirAdmin(String authorization) {
        if (!"ADMIN".equals(sesionDe(authorization).rol())) {
            throw new NoAutorizadoException();
        }
    }
}
