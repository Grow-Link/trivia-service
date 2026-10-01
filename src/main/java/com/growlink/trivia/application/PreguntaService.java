package com.growlink.trivia.application;

import com.growlink.trivia.adapter.persistence.PreguntaBancoRepository;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.PreguntaBanco;
import org.springframework.stereotype.Service;

import java.util.List;

// HU-23: el publicador agrega preguntas al banco, solo en categorias donde
// tiene cursos publicados (se consulta a cursos-service).
@Service
public class PreguntaService {

    private final PreguntaBancoRepository preguntaRepository;
    private final CursosClient cursosClient;

    public PreguntaService(PreguntaBancoRepository preguntaRepository, CursosClient cursosClient) {
        this.preguntaRepository = preguntaRepository;
        this.cursosClient = cursosClient;
    }

    public PreguntaBanco crear(Categoria categoria, String texto, List<String> opciones,
                               int respuestaCorrecta, Long publicadorUsuarioId, String authorization) {
        if (!cursosClient.categoriasPublicadas(publicadorUsuarioId, authorization).contains(categoria)) {
            throw new CategoriaNoPermitidaException(categoria);
        }
        return preguntaRepository.save(new PreguntaBanco(categoria, texto, opciones, respuestaCorrecta));
    }
}
