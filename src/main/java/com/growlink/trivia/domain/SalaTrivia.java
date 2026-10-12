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

    // HU salir-de-sala agrego CANCELADA a este enum. Hibernate le pone a las columnas @Enumerated(STRING)
    // un check constraint automatico con los valores que tenia el enum cuando CREO la columna, y
    // ddl-auto=update nunca lo actualiza si la columna ya existia, asi que cualquier base de antes de
    // esta rama se habria quedado con un check que no incluye CANCELADA. ChecksDeEnumObsoletos (en
    // config) lo quita solo, una vez, al arrancar contra Postgres -- no hace falta ningun paso manual
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

    // HU salir-de-sala: si el host se va mientras la sala sigue en ESPERA, el puesto pasa
    // al participante mas antiguo que quede
    public void fijarHostUsuarioId(Long hostUsuarioId) {
        this.hostUsuarioId = hostUsuarioId;
    }

    // la sala quedo vacia en ESPERA: nadie puede unirse ni iniciarla, y el dashboard deja de contarla
    public void marcarCancelada() {
        this.estado = EstadoSala.CANCELADA;
    }
}
