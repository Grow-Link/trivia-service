package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.RespuestaSala;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RespuestaSalaRepository extends JpaRepository<RespuestaSala, Long> {

    long countBySalaPreguntaId(Long salaPreguntaId);

    List<RespuestaSala> findBySalaPreguntaIdIn(List<Long> salaPreguntaIds);
}
