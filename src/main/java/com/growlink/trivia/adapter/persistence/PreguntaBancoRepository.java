package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.PreguntaBanco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreguntaBancoRepository extends JpaRepository<PreguntaBanco, Long> {

    List<PreguntaBanco> findByCategoria(Categoria categoria);
}
