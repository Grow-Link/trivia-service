package com.growlink.trivia.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// es la copia de una pregunta del banco, ya asignada a una sala en un orden
// se guarda copiada (no solo la referencia) para que si el banco cambia
// despues, la sala que ya empezo no se vea afectada
@Entity
@Table(name = "sala_pregunta")
public class SalaPregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long salaId;

    @Column(nullable = false)
    private int indice;

    @Column(nullable = false, length = 500)
    private String texto;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sala_pregunta_opcion", joinColumns = @JoinColumn(name = "sala_pregunta_id"))
    @Column(name = "opcion")
    @OrderColumn(name = "posicion")
    private List<String> opciones = new ArrayList<>();

    @Column(nullable = false)
    private int respuestaCorrecta;

    // se pone cuando de verdad se manda la pregunta, no cuando se crea la fila
    // el tiempo restante de cada cliente se calcula desde aqui, no desde su reloj
    private Instant enviadaEn;

    // el primero en responder bien esta pregunta, puesto con un UPDATE atomico
    private Long primerAcertanteUsuarioId;

    protected SalaPregunta() {
        // JPA
    }

    public SalaPregunta(Long salaId, int indice, String texto, List<String> opciones, int respuestaCorrecta) {
        this.salaId = salaId;
        this.indice = indice;
        this.texto = texto;
        this.opciones = new ArrayList<>(opciones);
        this.respuestaCorrecta = respuestaCorrecta;
    }

    public Long getId() {
        return id;
    }

    public Long getSalaId() {
        return salaId;
    }

    public int getIndice() {
        return indice;
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

    public Instant getEnviadaEn() {
        return enviadaEn;
    }

    public void marcarEnviada() {
        this.enviadaEn = Instant.now();
    }

    public Long getPrimerAcertanteUsuarioId() {
        return primerAcertanteUsuarioId;
    }
}
