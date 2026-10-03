package com.growlink.trivia.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "metrica_evento", indexes = {
        @Index(name = "idx_metrica_tipo_momento", columnList = "tipo, momento")
})
public class MetricaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEvento tipo;

    @Column(nullable = false)
    private Long salaId;

    @Column(nullable = false)
    private Instant momento;

    // solo lo llena PRIMERA_RESPUESTA
    private Long latenciaMs;

    // solo lo llenan PARTIDA_FINALIZADA
    private Long duracionMs;
    private Integer participantes;

    protected MetricaEvento() {
        // JPA
    }

    public MetricaEvento(TipoEvento tipo, Long salaId, Long latenciaMs, Long duracionMs, Integer participantes) {
        this.tipo = tipo;
        this.salaId = salaId;
        this.momento = Instant.now();
        this.latenciaMs = latenciaMs;
        this.duracionMs = duracionMs;
        this.participantes = participantes;
    }

    public Long getId() {
        return id;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public Long getSalaId() {
        return salaId;
    }

    public Instant getMomento() {
        return momento;
    }

    public Long getLatenciaMs() {
        return latenciaMs;
    }

    public Long getDuracionMs() {
        return duracionMs;
    }

    public Integer getParticipantes() {
        return participantes;
    }
}
