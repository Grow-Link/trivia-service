package com.growlink.trivia.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "sala_trivia", uniqueConstraints = @UniqueConstraint(columnNames = "codigo"))
public class SalaTrivia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categoria categoria;

    @Column(nullable = false)
    private Long hostUsuarioId;

    @Column(nullable = false)
    private int numPreguntas;

    @Column(nullable = false)
    private int duracionSegundos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSala estado;

    @Column(nullable = false)
    private Instant creadaEn;

    // codigo de la sala de revancha, si ya se propuso una (HU bono). null mientras nadie la pide
    private String revanchaCodigo;

    protected SalaTrivia() {
        // JPA
    }

    public SalaTrivia(String codigo, Categoria categoria, Long hostUsuarioId, int numPreguntas, int duracionSegundos) {
        this.codigo = codigo;
        this.categoria = categoria;
        this.hostUsuarioId = hostUsuarioId;
        this.numPreguntas = numPreguntas;
        this.duracionSegundos = duracionSegundos;
        this.estado = EstadoSala.ESPERANDO;
        this.creadaEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public Long getHostUsuarioId() {
        return hostUsuarioId;
    }

    public int getNumPreguntas() {
        return numPreguntas;
    }

    public int getDuracionSegundos() {
        return duracionSegundos;
    }

    public EstadoSala getEstado() {
        return estado;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public String getRevanchaCodigo() {
        return revanchaCodigo;
    }

    public void fijarRevanchaCodigo(String codigo) {
        this.revanchaCodigo = codigo;
    }
}
