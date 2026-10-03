package com.growlink.trivia.adapter.client;

import com.growlink.trivia.application.UsuariosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

// llamada de servicio a servicio, no lleva el token de ningun usuario
// se identifica con una llave interna que solo conocen los servicios
@Component
public class UsuariosRestClient implements UsuariosClient {

    private static final Logger log = LoggerFactory.getLogger(UsuariosRestClient.class);

    private final RestClient restClient;
    private final String llaveInterna;

    public UsuariosRestClient(RestClient.Builder builder,
                              @Value("${usuarios.base-url:http://localhost:8080}") String baseUrl,
                              @Value("${growlink.internal-key}") String llaveInterna) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.llaveInterna = llaveInterna;
    }

    @Override
    public void registrarVictoria(Long usuarioId) {
        try {
            restClient.post()
                    .uri("/api/interno/trivias-ganadas/{id}", usuarioId)
                    .header("X-Internal-Key", llaveInterna)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("No se pudo sumar la trivia ganada del usuario {}: {}", usuarioId, e.getMessage());
        }
    }
}
