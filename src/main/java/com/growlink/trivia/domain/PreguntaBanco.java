package com.growlink.trivia.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

// esto es el banco de preguntas de donde se sacan las de cada sala
// por ahora solo hay preguntas sembradas, lo de que el publicador agregue
// las suyas es HU-23 y todavia no esta hecho
@Entity
@Table(name = "pregunta_banco")
public class PreguntaBanco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 500)
    private String texto;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pregunta_banco_opcion", joinColumns = @JoinColumn(name = "pregunta_id"))
    @Column(name = "opcion")
    @OrderColumn(name = "posicion")
    private List<String> opciones = new ArrayList<>();

    @Column(nullable = false)
    private int respuestaCorrecta;

    protected PreguntaBanco() {
        // JPA
    }

    public PreguntaBanco(Categoria categoria, String texto, List<String> opciones, int respuestaCorrecta) {
        this.categoria = categoria;
        this.texto = texto;
        this.opciones = new ArrayList<>(opciones);
        this.respuestaCorrecta = respuestaCorrecta;
    }

    public Long getId() {
        return id;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public String getTexto() {
        return texto;
    }

    public List<String> getOpciones() {
        return opciones;
    }

    public int getRespuestaCorrecta() {
        return respuestaCorrecta;
    }
}
