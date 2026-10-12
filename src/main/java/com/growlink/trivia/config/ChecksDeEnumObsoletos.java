package com.growlink.trivia.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

// Hibernate le pone a cada columna @Enumerated(STRING) un check constraint con los valores que tenia
// el enum en el momento en que CREO esa columna, pero ddl-auto=update nunca lo actualiza si la columna
// ya existia (solo lo vuelve a generar completo si crea la tabla desde cero). EstadoSala gano el valor
// CANCELADA con HU salir-de-sala, asi que cualquier base que ya tuviera sala_trivia de antes de esta
// rama se quedo con el check viejo, que no lo incluye: guardar una sala cancelada truena con una
// violacion de constraint (verificado a mano contra Postgres).
//
// Los ApplicationRunner corren despues de que termina el refresh del contexto, y el esquema de
// Hibernate (create/update) se aplica DURANTE ese refresh (al construir el EntityManagerFactory), asi
// que para cuando esto se ejecuta Hibernate ya termino su parte. No hace falta volver a crear el check
// con el valor nuevo: la forma y el tipo de la columna "estado" no cambiaron, solo con quitar el
// constraint viejo alcanza.
//
// Solo corre contra Postgres (nunca contra H2, el motor que usan los tests) y es idempotente: el DROP
// lleva IF EXISTS tanto para la tabla como para el constraint, asi que no importa si ya se habia
// corrido antes (otro despliegue, otra replica) ni si la tabla todavia no existe. Si dos replicas lo
// ejecutan al mismo tiempo, Postgres serializa las dos sentencias con el lock de la tabla; la que
// llegue segunda simplemente no encuentra nada que borrar.
@Component
public class ChecksDeEnumObsoletos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChecksDeEnumObsoletos.class);

    private final DataSource dataSource;

    public ChecksDeEnumObsoletos(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conexion = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(conexion.getMetaData().getDatabaseProductName())) {
                return; // nada que hacer en H2 (perfil de test) ni en ningun otro motor
            }
            try (Statement statement = conexion.createStatement()) {
                statement.execute(
                        "ALTER TABLE IF EXISTS sala_trivia DROP CONSTRAINT IF EXISTS sala_trivia_estado_check");
            }
        } catch (SQLException e) {
            // esto es una limpieza de arranque, no algo que deba tumbar el servicio: si falla (otra
            // replica tiene la tabla bloqueada en ese instante, problemas de permisos, lo que sea),
            // simplemente se vuelve a intentar en el proximo arranque
            log.warn("No se pudo quitar el check constraint viejo de sala_trivia.estado", e);
        }
    }
}
