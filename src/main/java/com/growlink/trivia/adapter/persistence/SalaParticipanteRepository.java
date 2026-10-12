package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.SalaParticipante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SalaParticipanteRepository extends JpaRepository<SalaParticipante, Long> {

    List<SalaParticipante> findBySalaId(Long salaId);

    Optional<SalaParticipante> findBySalaIdAndUsuarioId(Long salaId, Long usuarioId);

    // el participante mas antiguo de la sala: a quien se le pasa el host si el host actual se va
    Optional<SalaParticipante> findFirstBySalaIdOrderByUnidoEnAsc(Long salaId);

    boolean existsBySalaIdAndUsuarioId(Long salaId, Long usuarioId);

    long countBySalaId(Long salaId);

    // igual que countBySalaId pero sin contar a quien ya salio de una partida EN_CURSO
    long countBySalaIdAndActivoTrue(Long salaId);

    // para el dashboard: jugadores que estan ahora en salas que no han terminado, sin contar a quien ya salio
    @Query("SELECT COUNT(p) FROM SalaParticipante p WHERE p.activo = true AND p.salaId IN " +
           "(SELECT s.id FROM SalaTrivia s WHERE s.estado IN ('ESPERANDO', 'EN_CURSO') AND s.creadaEn >= :desde)")
    long contarEnSalasActivas(@Param("desde") Instant desde);
}
