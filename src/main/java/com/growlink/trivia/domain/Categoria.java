package com.growlink.trivia.domain;

// Son las mismas categorias que usan los intereses y los cursos en usuarios-service
// como son servicios distintos, cada uno tiene su propia copia del enum
// si algun dia agregan una categoria nueva hay que agregarla en los dos lados
public enum Categoria {
    INGENIERIA_SISTEMAS,
    INGENIERIA_CIVIL,
    INGENIERIA_INDUSTRIAL,
    INGENIERIA_ELECTRONICA,
    INGENIERIA_MECANICA,
    INGENIERIA_AMBIENTAL,
    MATEMATICAS,
    ADMINISTRACION_EMPRESAS,
    IDIOMAS,
    DERECHO
}
