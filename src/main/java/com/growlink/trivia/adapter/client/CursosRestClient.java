package com.growlink.trivia.adapter.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.growlink.trivia.application.CursosClient;
import com.growlink.trivia.application.CursosServiceNoDisponibleException;
import com.growlink.trivia.domain.Categoria;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Llama a GET /api/cursos?publicadorUsuarioId=X de cursos-service reenviando
// el header Authorization que llego a trivia (cursos-service exige autenticacion).
// La URL se puede cambiar con cursos.base-url; por defecto es el puerto local de cursos.
@Component
public class CursosRestClient implements CursosClient {

    private final RestClient restClient;

    public CursosRestClient(RestClient.Builder builder,
                            @Value("${cursos.base-url:http://localhost:8086}") String baseUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public Set<Categoria> categoriasPublicadas(Long publicadorUsuarioId, String authorization) {
        List<CursoDto> cursos;
        try {
            var request = restClient.get().uri("/api/cursos?publicadorUsuarioId={id}", publicadorUsuarioId);
            if (authorization != null) {
                request = request.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            cursos = request.retrieve().body(new ParameterizedTypeReference<List<CursoDto>>() {
            });
        } catch (RestClientException e) {
            throw new CursosServiceNoDisponibleException(e);
        }

        if (cursos == null) {
            return Set.of();
        }

        Set<String> nombres = cursos.stream()
                .filter(CursoDto::activo)
                .map(CursoDto::categoria)
                .collect(Collectors.toSet());

        return Arrays.stream(Categoria.values())
                .filter(c -> nombres.contains(c.name()))
                .collect(Collectors.toSet());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CursoDto(String categoria, boolean activo) {
    }
}
