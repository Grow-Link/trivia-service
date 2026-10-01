package com.growlink.trivia.application;

import com.growlink.trivia.domain.Categoria;

import java.util.Set;

// Puerto hacia cursos-service. La implementacion HTTP vive en adapter/client.
public interface CursosClient {

    // Categorias en las que el publicador tiene al menos un curso activo.
    // authorization es el header Authorization de la peticion (cursos-service lo exige).
    Set<Categoria> categoriasPublicadas(Long publicadorUsuarioId, String authorization);
}
