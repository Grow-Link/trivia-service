package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.EstadoSala;
import com.growlink.trivia.domain.SalaTrivia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SalaTriviaRepository extends JpaRepository<SalaTrivia, Long> {

    Optional<SalaTrivia> findByCodigo(String codigo);

    // para el dashboard: salas de un estado creadas despues de cierta hora
    long countByEstadoAndCreadaEnGreaterThanEqual(EstadoSala estado, Instant desde);

    // Igual que hicimos con los cupos de las oportunidades, esto es un solo
    // UPDATE con el estado esperado en el WHERE, no leer y despues escribir
    // asi si dos personas le dan a "iniciar" casi al mismo tiempo, solo una
    // de las dos peticiones de verdad arranca la partida
    @Modifying
    @Query("UPDATE SalaTrivia s SET s.estado = 'EN_CURSO' WHERE s.id = :id AND s.estado = 'ESPERANDO'")
    int intentarIniciar(@Param("id") Long id);

    // mismo patron para el cierre, asi no se finaliza la sala dos veces
    @Modifying
    @Query("UPDATE SalaTrivia s SET s.estado = 'FINALIZADA' WHERE s.id = :id AND s.estado = 'EN_CURSO'")
    int intentarFinalizar(@Param("id") Long id);
}
