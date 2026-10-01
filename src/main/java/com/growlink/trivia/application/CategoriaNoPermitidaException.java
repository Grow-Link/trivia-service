package com.growlink.trivia.application;

import com.growlink.trivia.domain.Categoria;

public class CategoriaNoPermitidaException extends RuntimeException {
    public CategoriaNoPermitidaException(Categoria categoria) {
        super("Solo puedes crear preguntas en categorias donde tengas cursos publicados, y no tienes en " + categoria);
    }
}
