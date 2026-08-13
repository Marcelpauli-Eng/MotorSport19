package com.motorsport19.taller.factura.repository;

import com.motorsport19.taller.factura.domain.EventoFactura;
import com.motorsport19.taller.factura.domain.TipoEventoFactura;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Bitacora de facturacion. Solo lectura y alta: no se edita ni se borra.
 */
public interface EventoFacturaRepository extends JpaRepository<EventoFactura, Long> {

    @Query("""
            SELECT e FROM EventoFactura e
              LEFT JOIN FETCH e.usuario
              LEFT JOIN FETCH e.factura
             WHERE e.factura.id = :facturaId
             ORDER BY e.fecha ASC, e.id ASC
            """)
    List<EventoFactura> buscarPorFactura(@Param("facturaId") Long facturaId);

    /**
     * Registro de eventos, filtrable por tipo y por fechas.
     *
     * <p>Las fechas van con {@code COALESCE} contra la propia columna —que es
     * NOT NULL— porque PostgreSQL no deduce el tipo de un parametro temporal que
     * solo aparece dentro de un {@code IS NULL} y tumba la consulta entera.
     */
    @Query(value = """
            SELECT e FROM EventoFactura e
              LEFT JOIN FETCH e.usuario
              LEFT JOIN FETCH e.factura
             WHERE (:tipo  IS NULL OR e.tipoEvento = :tipo)
               AND e.fecha >= COALESCE(:desde, e.fecha)
               AND e.fecha <= COALESCE(:hasta, e.fecha)
             ORDER BY e.fecha DESC, e.id DESC
            """,
            countQuery = """
            SELECT COUNT(e) FROM EventoFactura e
             WHERE (:tipo  IS NULL OR e.tipoEvento = :tipo)
               AND e.fecha >= COALESCE(:desde, e.fecha)
               AND e.fecha <= COALESCE(:hasta, e.fecha)
            """)
    Page<EventoFactura> buscar(@Param("tipo") TipoEventoFactura tipo,
                               @Param("desde") Instant desde,
                               @Param("hasta") Instant hasta,
                               Pageable pageable);
}
