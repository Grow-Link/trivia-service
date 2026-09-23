package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.SalaParticipante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaParticipanteRepository extends JpaRepository<SalaParticipante, Long> {

    List<SalaParticipante> findBySalaId(Long salaId);

    boolean existsBySalaIdAndUsuarioId(Long salaId, Long usuarioId);

    long countBySalaId(Long salaId);
}
