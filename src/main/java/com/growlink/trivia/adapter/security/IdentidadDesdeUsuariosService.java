package com.growlink.trivia.adapter.security;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

// auth.identity-source=usuarios-service: le pregunta a usuarios-service quien es el dueno del
// token, con GET /api/usuarios/me y el MISMO token reenviado en el Authorization.
//
// Por que hace falta: el sub de Microsoft Entra es un texto opaco
// ("AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy782bbtaQ"), no un id. Este servicio necesita un usuarioId
// NUMERICO, porque es lo que guarda en sus tablas (SalaParticipante, RespuestaSala,
// PartidaPuesto). Ese numero y el rol viven en usuarios-service, que es el unico que sabe
// traducir "este sub de Microsoft" a "el usuario 42". Antes de esto el servicio exigia que el sub
// fuera numerico y, con tokens reales de Entra, habria rechazado absolutamente todos.
//
// Se usa el RestClient que ya usa el proyecto (ver UsuariosRestClient) con timeouts cortos: esta
// llamada esta en el camino de CADA peticion autenticada, asi que si usuarios-service se pone
// lento no puede arrastrar a trivia con el. El cache es lo que evita pagarla cada vez.
//
// A diferencia de UsuariosRestClient, aqui NO va la llave interna: /api/usuarios/me responde
// sobre el token de la persona, no es una llamada de servicio a servicio.
public class IdentidadDesdeUsuariosService implements ResolvedorDeIdentidad {

    private static final Logger log = LoggerFactory.getLogger(IdentidadDesdeUsuariosService.class);

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(2);
    private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(3);

    static final String RUTA_ME = "/api/usuarios/me";

    // El contrato con usuarios-service, en un solo lugar. Los nombres son los de su
    // MeResponse (usuarioId, rol, rolElegido, correo, nombre); aqui solo se leen los dos que
    // este servicio necesita, y Jackson ignora el resto. Si alla cambian un nombre, se ajusta
    // aqui y en ningun otro lado.
    record MeResponse(Long usuarioId, String rol) {
    }

    private final RestClient restClient;
    private final CacheDeIdentidades cache;

    public IdentidadDesdeUsuariosService(RestClient.Builder builder, String baseUrl, CacheDeIdentidades cache) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_CONEXION);
        requestFactory.setReadTimeout(TIMEOUT_LECTURA);
        this.restClient = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.cache = cache;
    }

    @Override
    public Identidad resolver(String jwt, Claims claims) {
        Optional<Identidad> cacheada = cache.buscar(jwt);
        if (cacheada.isPresent()) {
            // esto es tambien lo que mantiene el servicio en pie si usuarios-service se cae:
            // mientras la entrada siga vigente, nadie se queda afuera
            return cacheada.get();
        }

        Identidad identidad = preguntar(jwt);
        Instant exp = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
        cache.guardar(jwt, identidad, exp);
        return identidad;
    }

    private Identidad preguntar(String jwt) {
        MeResponse me;
        try {
            me = restClient.get()
                    .uri(RUTA_ME)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                    .exchange((peticion, respuesta) -> {
                        HttpStatusCode estado = respuesta.getStatusCode();
                        // 401/403: usuarios-service no reconoce el token. Es el unico caso en que
                        // el problema es del token y no nuestro, asi que vuelve como 401
                        if (estado.value() == 401 || estado.value() == 403) {
                            throw new TokenInvalidoException(
                                    "usuarios-service no reconoce el token (" + estado.value() + ")");
                        }
                        if (!estado.is2xxSuccessful()) {
                            throw new IdentidadNoDisponibleException(
                                    "usuarios-service respondio " + estado.value() + " en " + RUTA_ME);
                        }
                        return respuesta.bodyTo(MeResponse.class);
                    });
        } catch (TokenInvalidoException | IdentidadNoDisponibleException e) {
            throw e;
        } catch (Exception e) {
            // no contesta, DNS, conexion rechazada, timeout... Todo esto es "no sabemos quien es",
            // nunca "pase igual": caer al sub del token aqui seria deshacer el proposito de la HU
            throw new IdentidadNoDisponibleException(
                    "no se pudo consultar " + RUTA_ME + " en usuarios-service: " + e.getMessage(), e);
        }

        if (me == null || me.usuarioId() == null) {
            throw new IdentidadNoDisponibleException(
                    "usuarios-service contesto " + RUTA_ME + " sin usuarioId");
        }
        log.debug("usuarios-service resolvio el token como el usuario {} con rol {}", me.usuarioId(), me.rol());
        return new Identidad(me.usuarioId(), me.rol());
    }
}
