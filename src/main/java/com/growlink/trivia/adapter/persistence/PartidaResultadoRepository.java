package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.PartidaResultado;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PartidaResultadoRepository extends JpaRepository<PartidaResultado, Long> {

    List<PartidaResultado> findAllByOrderByFinalizadaEnDesc(Pageable limite);

    Optional<PartidaResultado> findByCodigo(String codigo);

    boolean existsBySalaId(Long salaId);

    // el salon de la fama: quien ha ganado mas partidas (a igualdad, el que tiene el mejor puntaje)
    @Query("SELECT p.ganadorUsuarioId, MAX(p.ganadorNombre), COUNT(p), MAX(p.puntosGanador) "
            + "FROM PartidaResultado p WHERE p.ganadorUsuarioId IS NOT NULL "
            + "GROUP BY p.ganadorUsuarioId ORDER BY COUNT(p) DESC, MAX(p.puntosGanador) DESC")
    List<Object[]> salonDeLaFama(Pageable limite);
}
