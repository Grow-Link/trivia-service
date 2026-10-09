package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.EstadoReto;
import com.growlink.trivia.domain.Reto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RetoRepository extends JpaRepository<Reto, Long> {

    List<Reto> findByRetadoUsuarioIdAndEstadoAndExpiraEnAfterOrderByCreadoEnDesc(Long retadoUsuarioId,
                                                                                 EstadoReto estado, Instant ahora);

    List<Reto> findTop10ByRetadorUsuarioIdOrderByCreadoEnDesc(Long retadorUsuarioId);

    boolean existsByRetadorUsuarioIdAndRetadoUsuarioIdAndEstadoAndExpiraEnAfter(Long retadorUsuarioId,
                                                                                Long retadoUsuarioId,
                                                                                EstadoReto estado, Instant ahora);

    // El mismo patron de siempre: un solo UPDATE con el estado esperado en el WHERE. Si el retado le da a
    // "aceptar" y "rechazar" casi a la vez (o dos pestañas), solo una de las dos gana. Devuelve 0 si ya no se podia.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Reto r SET r.estado = :nuevo WHERE r.id = :id AND r.retadoUsuarioId = :retadoUsuarioId "
            + "AND r.estado = com.growlink.trivia.domain.EstadoReto.PENDIENTE AND r.expiraEn > :ahora")
    int responder(@Param("id") Long id, @Param("retadoUsuarioId") Long retadoUsuarioId,
                  @Param("nuevo") EstadoReto nuevo, @Param("ahora") Instant ahora);
}
