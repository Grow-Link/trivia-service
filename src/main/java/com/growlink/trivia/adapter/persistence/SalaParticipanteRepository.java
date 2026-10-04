package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.SalaParticipante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SalaParticipanteRepository extends JpaRepository<SalaParticipante, Long> {

    List<SalaParticipante> findBySalaId(Long salaId);

    boolean existsBySalaIdAndUsuarioId(Long salaId, Long usuarioId);

    long countBySalaId(Long salaId);

    // para el dashboard: jugadores que estan ahora en salas que no han terminado
    @Query("SELECT COUNT(p) FROM SalaParticipante p WHERE p.salaId IN " +
           "(SELECT s.id FROM SalaTrivia s WHERE s.estado IN ('ESPERANDO', 'EN_CURSO') AND s.creadaEn >= :desde)")
    long contarEnSalasActivas(@Param("desde") Instant desde);
}
