package com.growlink.trivia.config;

import org.slf4j.MDC;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;

// ATRIBUTO: observabilidad. Cada llamada que este servicio le hace a usuarios-service (sumar una trivia ganada,
// resolver la identidad con /api/usuarios/me) o a cursos-service lleva el X-Request-Id de lo que se esta atendiendo.
// Asi una partida que termina se puede seguir de un servicio al otro buscando un solo id en los logs.
//
// Se engancha en TODOS los RestClient que salen del RestClient.Builder de Spring Boot (UsuariosRestClient,
// CursosRestClient e IdentidadDesdeUsuariosService lo usan), asi que un cliente nuevo lo trae solo.
//
// El id sale del MDC, y siempre hay uno: en una peticion HTTP lo pone RequestIdFilter, en un mensaje del WebSocket
// RequestIdStompInterceptor y en el cierre por tiempo de una pregunta CierrePreguntaScheduler. Si una llamada ya trae
// su propio X-Request-Id, se respeta.
@Component
public class ReenvioDeRequestId implements RestClientCustomizer, ClientHttpRequestInterceptor {

    @Override
    public void customize(RestClient.Builder builder) {
        builder.requestInterceptor(this);
    }

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body,
                                        @NonNull ClientHttpRequestExecution execution) throws IOException {
        String id = MDC.get(RequestIdFilter.MDC_KEY);
        if (id != null && !request.getHeaders().containsKey(RequestIdFilter.HEADER)) {
            request.getHeaders().set(RequestIdFilter.HEADER, id);
        }
        return execution.execute(request, body);
    }
}
