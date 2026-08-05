package com.motorsport19.taller.orden.repository;

import com.motorsport19.taller.orden.domain.CambioEstadoOT;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Lectura del historial de estados.
 *
 * <p>No se expone ninguna operacion de modificacion: el historial es append-only
 * y la base de datos rechazaria el intento igualmente.
 */
public interface CambioEstadoOTRepository extends JpaRepository<CambioEstadoOT, Long> {

    @Query("""
            SELECT c FROM CambioEstadoOT c
              LEFT JOIN FETCH c.usuario
             WHERE c.ordenTrabajo.id = :ordenId
             ORDER BY c.fecha ASC, c.id ASC
            """)
    List<CambioEstadoOT> buscarPorOrden(@Param("ordenId") Long ordenId);
}
