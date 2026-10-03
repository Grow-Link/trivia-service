package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.MetricaEvento;
import com.growlink.trivia.domain.TipoEvento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricaEventoRepository extends JpaRepository<MetricaEvento, Long> {

    long countByTipo(TipoEvento tipo);

    long countBySalaIdAndTipo(Long salaId, TipoEvento tipo);
}
