package com.growlink.trivia.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.RequestPath;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// ATRIBUTO: seguridad y observabilidad. /actuator/prometheus solo le contesta a quien trae el token del scrape en la
// cabecera X-Metricas-Token (el mismo METRICAS_TOKEN en los tres servicios y en la configuracion de Prometheus, ver
// ADR-0009 en el repo infra). Antes quedaba publico, y el nginx del front reenvia /api-trivia/** a este servicio:
// cualquiera podia leer el mapa interno (rutas, volumen de trafico, memoria) desde el navegador. En Azure, ademas, la
// URL propia del servicio es publica.
//
// Este servicio no tiene una cadena de Spring Security, asi que la puerta es este filtro. Lo delicado es reconocer
// la ruta igual que la reconoce Spring MVC: si el filtro comparara el texto tal cual, "/actuator/prometheus;x=1" o
// "/actuator/prometh%65us" pasarian de largo y el endpoint igual contestaria. Por eso se usa el mismo analizador de
// rutas que el mapeo de actuator (PathPattern sobre la ruta ya interpretada), y con "/**" para cubrir tambien lo que
// venga despues de la ruta.
//
// Va en una cabecera propia y no en Authorization a proposito: en modo oidc Authorization lleva el token de
// Microsoft, y los otros dos servicios lo interpretan como tal.
//
// Sin METRICAS_TOKEN el endpoint queda CERRADO (siempre 401), no abierto: olvidar la variable no puede publicar las
// metricas. Un token de menos de 16 caracteres no deja arrancar, igual que un secreto corto del JWT. Va despues de
// RequestIdFilter e InstanciaHeaderFilter, para que el 401 tambien lleve X-Request-Id y X-Instancia.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class TokenDeMetricasFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Metricas-Token";
    static final int LARGO_MINIMO = 16;

    private static final Logger log = LoggerFactory.getLogger(TokenDeMetricasFilter.class);
    private static final PathPattern RUTA_DE_METRICAS = PathPatternParser.defaultInstance.parse("/actuator/prometheus/**");

    // null = no hay token configurado: nadie entra
    private final byte[] token;

    public TokenDeMetricasFilter(@Value("${observabilidad.metricas-token:}") String token) {
        String limpio = token == null ? "" : token.trim();
        if (!limpio.isEmpty() && limpio.length() < LARGO_MINIMO) {
            throw new IllegalStateException("observabilidad.metricas-token (METRICAS_TOKEN) tiene que tener al menos "
                    + LARGO_MINIMO + " caracteres");
        }
        this.token = limpio.isEmpty() ? null : limpio.getBytes(StandardCharsets.UTF_8);
        if (this.token == null) {
            log.info("METRICAS_TOKEN vacio: /actuator/prometheus queda cerrado (responde 401 a todos)");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        RequestPath ruta = RequestPath.parse(request.getRequestURI(), request.getContextPath());
        return !RUTA_DE_METRICAS.matches(ruta.pathWithinApplication());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (valido(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        // el id de la peticion sale solo en la linea (patron del log); el token recibido nunca se escribe
        log.warn("Scrape de metricas rechazado en {} {}: {}", request.getMethod(), request.getRequestURI(),
                request.getHeader(CABECERA) == null ? "no trae " + CABECERA : "el token no es el del scrape");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // misma forma que el resto de los errores del servicio (ver GlobalExceptionHandler.ErrorBody)
        response.getWriter().write("{\"message\":\"Las metricas son solo para el scrape de Prometheus.\"}");
    }

    /** true si la peticion trae el token del scrape. Se compara en tiempo constante, para no ir adivinandolo. */
    boolean valido(HttpServletRequest request) {
        if (token == null) {
            return false;
        }
        String recibido = request.getHeader(CABECERA);
        return recibido != null && MessageDigest.isEqual(token, recibido.trim().getBytes(StandardCharsets.UTF_8));
    }
}
