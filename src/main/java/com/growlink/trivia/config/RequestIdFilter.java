package com.growlink.trivia.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// Un identificador por peticion para poder seguirla completa en los logs, incluso cuando pasa
// por varios servicios (usuarios-service, cursos-service) o la atienden replicas distintas.
//
// Si el cliente (o el gateway, o el servicio que nos llama) ya manda X-Request-Id, se respeta
// ese valor para que la traza sea la misma de punta a punta; si no viene, se genera un UUID.
// En los dos casos vuelve en la respuesta, para que quien llamo sepa con que id reclamar.
//
// Va primero en la cadena de filtros porque el valor tiene que estar en el MDC antes de que
// cualquier otra cosa escriba un log (ver el patron logging.pattern.level en application.yml).
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    // la misma llave que nombra el patron de log, y la que usa StompAuthChannelInterceptor
    // para dejar el CONNECT del WebSocket en la misma traza
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = normalizar(request.getHeader(HEADER));
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // el hilo vuelve al pool de Tomcat y lo reusa otra peticion: si no se limpia,
            // la siguiente peticion loguearia con el id de esta
            MDC.remove(MDC_KEY);
        }
    }

    // un header en blanco cuenta como que no vino. Tampoco se confia en su contenido tal cual:
    // va a terminar en los logs, y un valor con saltos de linea podria inventar lineas falsas
    private static String normalizar(String header) {
        if (header == null || header.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String limpio = header.trim().replaceAll("[^A-Za-z0-9_.:-]", "");
        if (limpio.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return limpio.length() > 120 ? limpio.substring(0, 120) : limpio;
    }
}
