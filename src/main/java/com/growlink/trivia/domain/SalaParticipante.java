package com.growlink.trivia.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    // false cuando el jugador salio de una partida EN_CURSO (cerro la pestana, se cayo la red, o mando
    // "salir"). No se borra la fila mientras la partida sigue porque sus puntos ya cuentan para el
    // historial; en ESPERA en cambio la fila si se borra, ahi no hace falta esta columna.
    // columnDefinition con el DEFAULT (no solo nullable=false) es obligatorio: con ddl-auto=update,
    // Postgres hace "ALTER TABLE ... ADD COLUMN ... NOT NULL" sobre una tabla que YA tiene filas, y sin
    // un DEFAULT ahi mismo esa sentencia falla ("column contains null values"); con el DEFAULT, Postgres
    // rellena las filas existentes con true al vuelo. Probado a mano contra una base Postgres con datos.
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean activo = true;

    // true solo cuando quedo inactivo por un "salir" explicito; false si fue por una desconexion
    // (se le acabo el margen de gracia sin volver a mandar "unirse"). unirse() usa esto para decidir
    // si reconectarse adentro de una partida en curso lo reactiva o lo deja igual: una desconexion
    // se puede deshacer volviendo a entrar, pero un "me voy" explicito no. Mismo motivo que "activo"
    // para el columnDefinition con DEFAULT
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean salidaVoluntaria = false;

    // se actualiza cada vez que llega un "unirse" para este usuario y esta sala, incluida la
    // reconexion idempotente. Es lo que usa SalaService.salirPorDesconexion para saber, sin ningun
    // estado en memoria compartido entre replicas, si hubo una conexion mas nueva que la de la sesion
    // que se cayo (comparando con isAfter, sin importar en que replica haya pasado la reconexion).
    // DEFAULT now(): las filas que ya existian antes de esta columna quedan con la hora de la
    // migracion (no con null, que habria que tratar como "muy antigua" en cada comparacion), asi no
    // se las trata como desconectadas desde siempre apenas se despliega esto.
    //
    // Siempre se trunca a milisegundos al asignarla (ver registrarConexion): isAfter() se usa para
    // comparar el valor que vuelve de un SELECT (pasado por la precision real de la columna) contra
    // el que quedo en memoria justo despues de guardarlo, y timestamp(6) en Postgres no siempre trunca
    // para menos, a veces redondea hacia arriba. Sin truncar los dos lados a la misma precision antes
    // de comparar, ese redondeo podia hacer que el valor leido de la base pareciera "mas nuevo" que el
    // original aunque no hubiera pasado ninguna reconexion -- encontrado con una prueba real contra H2
    @Column(nullable = false, columnDefinition = "timestamp(6) with time zone default now()")
    private Instant ultimaConexion;

    protected SalaParticipante() {
        // JPA
    }

    public SalaParticipante(Long salaId, Long usuarioId, String nombre) {
        this.salaId = salaId;
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.unidoEn = Instant.now();
        this.ultimaConexion = this.unidoEn.truncatedTo(ChronoUnit.MILLIS);
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

    public Instant getUnidoEn() {
        return unidoEn;
    }

    public boolean isActivo() {
        return activo;
    }

    public boolean isSalidaVoluntaria() {
        return salidaVoluntaria;
    }

    public Instant getUltimaConexion() {
        return ultimaConexion;
    }

    public void marcarInactivo(boolean voluntaria) {
        this.activo = false;
        this.salidaVoluntaria = voluntaria;
    }

    public void reactivar() {
        this.activo = true;
        this.salidaVoluntaria = false;
    }

    public void registrarConexion() {
        this.ultimaConexion = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
