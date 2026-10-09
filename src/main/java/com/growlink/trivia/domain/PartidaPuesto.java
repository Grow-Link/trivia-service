package com.growlink.trivia.domain;

import jakarta.persistence.*;

// Un renglon de la tabla final de una partida: en que puesto quedo cada jugador, con cuantos puntos y aciertos.
@Entity
@Table(name = "partida_puesto", indexes = {
        @Index(name = "idx_partida_puesto_partida", columnList = "partidaId"),
        @Index(name = "idx_partida_puesto_usuario", columnList = "usuarioId")})
public class PartidaPuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long partidaId;

    @Column(nullable = false)
    private int posicion;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private int puntos;

    @Column(nullable = false)
    private int aciertos;

    protected PartidaPuesto() {
        // JPA
    }

    public PartidaPuesto(Long partidaId, int posicion, Long usuarioId, String nombre, int puntos, int aciertos) {
        this.partidaId = partidaId;
        this.posicion = posicion;
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.puntos = puntos;
        this.aciertos = aciertos;
    }

    public Long getId() {
        return id;
    }

    public Long getPartidaId() {
        return partidaId;
    }

    public int getPosicion() {
        return posicion;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getNombre() {
        return nombre;
    }

    public int getPuntos() {
        return puntos;
    }

    public int getAciertos() {
        return aciertos;
    }
}
