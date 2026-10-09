package com.growlink.trivia.domain;

import jakarta.persistence.*;

import java.time.Instant;

// "Te reto a una trivia". Al retar se crea la sala de una vez (el retador es el host y queda esperando),
// el reto solo guarda quien invito a quien y a que sala. Aceptar lleva al retado a esa sala.
// El estado cambia con un UPDATE atomico (ver RetoRepository), asi aceptar y rechazar a la vez no se pisan.
@Entity
@Table(name = "reto", indexes = {
        @Index(name = "idx_reto_retado", columnList = "retadoUsuarioId"),
        @Index(name = "idx_reto_retador", columnList = "retadorUsuarioId")})
public class Reto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long retadorUsuarioId;

    @Column(nullable = false)
    private String retadorNombre;

    @Column(nullable = false)
    private Long retadoUsuarioId;

    @Column(nullable = false)
    private String retadoNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categoria categoria;

    @Column(nullable = false)
    private int numPreguntas;

    @Column(nullable = false)
    private int duracionSegundos;

    @Column(length = 140)
    private String mensaje;

    @Column(nullable = false)
    private String codigoSala;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReto estado;

    @Column(nullable = false)
    private Instant creadoEn;

    @Column(nullable = false)
    private Instant expiraEn;

    protected Reto() {
        // JPA
    }

    public Reto(Long retadorUsuarioId, String retadorNombre, Long retadoUsuarioId, String retadoNombre,
                SalaTrivia sala, String mensaje, Instant expiraEn) {
        this.retadorUsuarioId = retadorUsuarioId;
        this.retadorNombre = retadorNombre;
        this.retadoUsuarioId = retadoUsuarioId;
        this.retadoNombre = retadoNombre;
        this.categoria = sala.getCategoria();
        this.numPreguntas = sala.getNumPreguntas();
        this.duracionSegundos = sala.getDuracionSegundos();
        this.codigoSala = sala.getCodigo();
        this.mensaje = mensaje;
        this.estado = EstadoReto.PENDIENTE;
        this.creadoEn = Instant.now();
        this.expiraEn = expiraEn;
    }

    // el estado que ve la gente: un pendiente que ya vencio se muestra como expirado
    public EstadoReto estadoEfectivo(Instant ahora) {
        if (estado == EstadoReto.PENDIENTE && !expiraEn.isAfter(ahora)) {
            return EstadoReto.EXPIRADO;
        }
        return estado;
    }

    public Long getId() {
        return id;
    }

    public Long getRetadorUsuarioId() {
        return retadorUsuarioId;
    }

    public String getRetadorNombre() {
        return retadorNombre;
    }

    public Long getRetadoUsuarioId() {
        return retadoUsuarioId;
    }

    public String getRetadoNombre() {
        return retadoNombre;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public int getNumPreguntas() {
        return numPreguntas;
    }

    public int getDuracionSegundos() {
        return duracionSegundos;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getCodigoSala() {
        return codigoSala;
    }

    public EstadoReto getEstado() {
        return estado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }
}
