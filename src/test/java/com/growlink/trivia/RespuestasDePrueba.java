package com.growlink.trivia;

import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.domain.SalaPregunta;
import com.growlink.trivia.domain.SalaTrivia;

// Las preguntas de la sala se eligen al azar del banco, asi que las pruebas no pueden suponer cual opcion es la
// correcta: la leen de la pregunta que de verdad quedo en la sala.
public final class RespuestasDePrueba {

    private RespuestasDePrueba() {
    }

    public static int correcta(SalaPreguntaRepository repo, SalaTrivia sala, int indice) {
        return pregunta(repo, sala, indice).getRespuestaCorrecta();
    }

    public static int incorrecta(SalaPreguntaRepository repo, SalaTrivia sala, int indice) {
        SalaPregunta p = pregunta(repo, sala, indice);
        return (p.getRespuestaCorrecta() + 1) % p.getOpciones().size();
    }

    private static SalaPregunta pregunta(SalaPreguntaRepository repo, SalaTrivia sala, int indice) {
        return repo.findBySalaIdAndIndice(sala.getId(), indice).orElseThrow();
    }
}
