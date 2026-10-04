package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.MetricaEvento;
import com.growlink.trivia.domain.TipoEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface MetricaEventoRepository extends JpaRepository<MetricaEvento, Long> {

    long countByTipo(TipoEvento tipo);

    long countBySalaIdAndTipo(Long salaId, TipoEvento tipo);

    long countByTipoAndMomentoGreaterThanEqual(TipoEvento tipo, Instant desde);

    // null si todavia no hay eventos de ese tipo
    @Query("SELECT AVG(e.latenciaMs) FROM MetricaEvento e WHERE e.tipo = :tipo AND e.momento >= :desde")
    Double latenciaPromedio(@Param("tipo") TipoEvento tipo, @Param("desde") Instant desde);

    @Query("SELECT AVG(e.duracionMs) FROM MetricaEvento e WHERE e.tipo = :tipo AND e.momento >= :desde")
    Double duracionPromedio(@Param("tipo") TipoEvento tipo, @Param("desde") Instant desde);
}
