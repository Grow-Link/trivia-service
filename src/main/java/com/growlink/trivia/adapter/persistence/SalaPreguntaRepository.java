package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.SalaPregunta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalaPreguntaRepository extends JpaRepository<SalaPregunta, Long> {

    Optional<SalaPregunta> findBySalaIdAndIndice(Long salaId, int indice);

    List<SalaPregunta> findBySalaIdOrderByIndice(Long salaId);

    // esto toma el lock de la fila hasta que termine la transaccion
    // asi, si dos respuestas de la misma pregunta llegan casi juntas, la
    // segunda espera a que la primera termine antes de contar cuantos
    // respondieron, y no se duplica el avance de pregunta
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM SalaPregunta p WHERE p.id = :id")
    Optional<SalaPregunta> buscarConLockPorId(@Param("id") Long id);

    // mismo truco de siempre, un solo UPDATE con la condicion en el WHERE
    // solo la primera respuesta correcta que llegue gana esta fila
    @Modifying
    @Query("UPDATE SalaPregunta p SET p.primerAcertanteUsuarioId = :usuarioId " +
           "WHERE p.id = :id AND p.primerAcertanteUsuarioId IS NULL")
    int intentarMarcarPrimerAcertante(@Param("id") Long id, @Param("usuarioId") Long usuarioId);
}
