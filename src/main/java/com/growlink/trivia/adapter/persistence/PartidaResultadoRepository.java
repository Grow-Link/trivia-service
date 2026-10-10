package com.growlink.trivia.adapter.persistence;

import com.growlink.trivia.domain.PartidaResultado;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PartidaResultadoRepository extends JpaRepository<PartidaResultado, Long> {

    List<PartidaResultado> findAllByOrderByFinalizadaEnDesc(Pageable limite);

    Optional<PartidaResultado> findByCodigo(String codigo);

    boolean existsBySalaId(Long salaId);

    // el salon de la fama: quien ha ganado mas partidas (a igualdad, el que tiene el mejor puntaje).
    // una victoria es cualquier puesto cuyo puntaje iguala al puntaje ganador de esa partida, asi
    // que en un empate todos los que empataron suman, no solo el que quedo guardado como
    // "ganadorUsuarioId" en PartidaResultado (ese campo es solo para mostrar un nombre en el resumen)
    @Query("SELECT pu.usuarioId, MAX(pu.nombre), COUNT(pu), MAX(p.puntosGanador) "
            + "FROM PartidaPuesto pu JOIN PartidaResultado p ON pu.partidaId = p.id "
            + "WHERE p.ganadorUsuarioId IS NOT NULL AND pu.puntos = p.puntosGanador "
            + "GROUP BY pu.usuarioId ORDER BY COUNT(pu) DESC, MAX(p.puntosGanador) DESC")
    List<Object[]> salonDeLaFama(Pageable limite);
}
