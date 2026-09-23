package com.growlink.trivia.domain;

import jakarta.persistence.*;

import java.time.Instant;

// Guardamos el nombre aqui mismo (no solo el id) para no tener que llamar a
// usuarios-service cada vez que se arma la lista de participantes
@Entity
@Table(name = "sala_participante", uniqueConstraints = @UniqueConstraint(columnNames = {"salaId", "usuarioId"}))
public class SalaParticipante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long salaId;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private Instant unidoEn;

    protected SalaParticipante() {
        // JPA
    }

    public SalaParticipante(Long salaId, Long usuarioId, String nombre) {
        this.salaId = salaId;
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.unidoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSalaId() {
        return salaId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getNombre() {
        return nombre;
    }
}
