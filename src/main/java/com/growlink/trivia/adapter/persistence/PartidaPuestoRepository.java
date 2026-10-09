package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.PartidaPuesto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartidaPuestoRepository extends JpaRepository<PartidaPuesto, Long> {

    List<PartidaPuesto> findByPartidaIdOrderByPosicion(Long partidaId);

    List<PartidaPuesto> findByPartidaIdInOrderByPosicion(List<Long> partidaIds);

    List<PartidaPuesto> findByUsuarioIdOrderByPartidaIdDesc(Long usuarioId, Pageable limite);
}
