package com.motorsport19.taller.orden.repository;

import com.motorsport19.taller.orden.domain.LineaOT;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LineaOTRepository extends JpaRepository<LineaOT, Long> {

    /** Lineas de una OT con la pieza resuelta, en su orden de presentacion. */
    @Query("""
            SELECT l FROM LineaOT l
              LEFT JOIN FETCH l.pieza
             WHERE l.ordenTrabajo.id = :ordenId
             ORDER BY l.numeroLinea
            """)
    List<LineaOT> buscarPorOrden(@Param("ordenId") Long ordenId);
}
