package com.growlink.trivia.domain;

import jakarta.persistence.*;

import java.time.Instant;

// Una fila por partida terminada: es el "acta" de la competencia. Se guarda al finalizar para que
// la lista de ganadores no dependa de recalcular nada despues (y siga ahi aunque se limpien las salas).
// salaId es unico: aunque dos replicas intenten cerrar la misma partida, solo queda un acta.
@Entity
@Table(name = "partida_resultado", uniqueConstraints = @UniqueConstraint(columnNames = "salaId"))
public class PartidaResultado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long salaId;

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
    private int duracionPreguntaSegundos;

    @Column(nullable = false)
    private int totalJugadores;

    // null cuando nadie sumo puntos (no hubo ganador)
    private Long ganadorUsuarioId;

    private String ganadorNombre;

    @Column(nullable = false)
    private int puntosGanador;

    // true si varias personas quedaron con el puntaje mas alto
    @Column(nullable = false)
    private boolean empate;

    @Column(nullable = false)
    private long duracionTotalMs;

    @Column(nullable = false)
    private Instant finalizadaEn;

    protected PartidaResultado() {
        // JPA
    }

    public PartidaResultado(SalaTrivia sala, int totalJugadores, Long ganadorUsuarioId, String ganadorNombre,
                            int puntosGanador, boolean empate, long duracionTotalMs) {
        this.salaId = sala.getId();
        this.codigo = sala.getCodigo();
        this.categoria = sala.getCategoria();
        this.hostUsuarioId = sala.getHostUsuarioId();
        this.numPreguntas = sala.getNumPreguntas();
        this.duracionPreguntaSegundos = sala.getDuracionSegundos();
        this.totalJugadores = totalJugadores;
        this.ganadorUsuarioId = ganadorUsuarioId;
        this.ganadorNombre = ganadorNombre;
        this.puntosGanador = puntosGanador;
        this.empate = empate;
        this.duracionTotalMs = duracionTotalMs;
        this.finalizadaEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSalaId() {
        return salaId;
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

    public int getDuracionPreguntaSegundos() {
        return duracionPreguntaSegundos;
    }

    public int getTotalJugadores() {
        return totalJugadores;
    }

    public Long getGanadorUsuarioId() {
        return ganadorUsuarioId;
    }

    public String getGanadorNombre() {
        return ganadorNombre;
    }

    public int getPuntosGanador() {
        return puntosGanador;
    }

    public boolean isEmpate() {
        return empate;
    }

    public long getDuracionTotalMs() {
        return duracionTotalMs;
    }

    public Instant getFinalizadaEn() {
        return finalizadaEn;
    }
}
