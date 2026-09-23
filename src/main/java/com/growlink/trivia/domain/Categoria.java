package com.growlink.trivia.domain;

// Son las mismas categorias que usan los intereses y los cursos en usuarios-service
// como son servicios distintos, cada uno tiene su propia copia del enum
// si algun dia agregan una categoria nueva hay que agregarla en los dos lados
public enum Categoria {
    BACKEND,
    FRONTEND,
    BASES_DE_DATOS,
    DEVOPS,
    SEGURIDAD,
    FUNDAMENTOS,
    PYTHON
}
