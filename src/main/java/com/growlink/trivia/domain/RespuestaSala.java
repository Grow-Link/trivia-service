package com.growlink.trivia.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "respuesta_sala", uniqueConstraints = @UniqueConstraint(columnNames = {"salaPreguntaId", "usuarioId"}))
public class RespuestaSala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long salaPreguntaId;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private int opcionElegida;

    @Column(nullable = false)
    private boolean esCorrecta;

    @Column(nullable = false)
    private int puntos;

    @Column(nullable = false)
    private Instant respondidaEn;

    protected RespuestaSala() {
        // JPA
    }

    public RespuestaSala(Long salaPreguntaId, Long usuarioId, int opcionElegida, boolean esCorrecta, int puntos) {
        this.salaPreguntaId = salaPreguntaId;
        this.usuarioId = usuarioId;
        this.opcionElegida = opcionElegida;
        this.esCorrecta = esCorrecta;
        this.puntos = puntos;
        this.respondidaEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSalaPreguntaId() {
        return salaPreguntaId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public boolean isEsCorrecta() {
        return esCorrecta;
    }

    public int getPuntos() {
        return puntos;
    }
}
