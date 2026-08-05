package com.motorsport19.taller.inventario.repository;

import com.motorsport19.taller.inventario.domain.MovimientoStock;
import com.motorsport19.taller.inventario.domain.TipoMovimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Acceso al libro de movimientos.
 *
 * <p>Deliberadamente no se exponen operaciones de modificacion ni de borrado: el
 * libro es append-only y la base de datos rechazaria el intento de todas formas.
 */
public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {

    // Las consultas traen pieza y usuario con JOIN FETCH porque la respuesta los
    // muestra y la sesion ya esta cerrada cuando se serializa (open-in-view esta
    // desactivado). De paso se evita el N+1 en listados largos.

    @Query(value = """
            SELECT m FROM MovimientoStock m
              JOIN FETCH m.pieza
              LEFT JOIN FETCH m.usuario
             WHERE m.pieza.id = :piezaId
             ORDER BY m.fecha DESC, m.id DESC
            """,
            countQuery = "SELECT COUNT(m) FROM MovimientoStock m WHERE m.pieza.id = :piezaId")
    Page<MovimientoStock> buscarPorPieza(@Param("piezaId") Long piezaId, Pageable pageable);

    @Query(value = """
            SELECT m FROM MovimientoStock m
              JOIN FETCH m.pieza
              LEFT JOIN FETCH m.usuario
             WHERE (:piezaId IS NULL OR m.pieza.id = :piezaId)
               AND (:tipo    IS NULL OR m.tipo = :tipo)
               AND (:desde   IS NULL OR m.fecha >= :desde)
               AND (:hasta   IS NULL OR m.fecha <= :hasta)
             ORDER BY m.fecha DESC, m.id DESC
            """,
            countQuery = """
            SELECT COUNT(m) FROM MovimientoStock m
             WHERE (:piezaId IS NULL OR m.pieza.id = :piezaId)
               AND (:tipo    IS NULL OR m.tipo = :tipo)
               AND (:desde   IS NULL OR m.fecha >= :desde)
               AND (:hasta   IS NULL OR m.fecha <= :hasta)
            """)
    Page<MovimientoStock> buscar(@Param("piezaId") Long piezaId,
                                 @Param("tipo") TipoMovimiento tipo,
                                 @Param("desde") Instant desde,
                                 @Param("hasta") Instant hasta,
                                 Pageable pageable);

    /** Carga un movimiento con pieza y usuario resueltos, para devolverlo en la respuesta. */
    @Query("""
            SELECT m FROM MovimientoStock m
              JOIN FETCH m.pieza
              LEFT JOIN FETCH m.usuario
             WHERE m.id = :id
            """)
    java.util.Optional<MovimientoStock> buscarConDetalle(@Param("id") Long id);

    List<MovimientoStock> findByOrdenTrabajoIdOrderByFechaAsc(Long ordenTrabajoId);

    /**
     * Suma de todos los movimientos de una pieza.
     *
     * <p>Debe coincidir siempre con {@code pieza.stock_actual}. Se usa en las
     * comprobaciones de integridad: si difieren, algo ha escrito el stock por un
     * camino que no deberia existir.
     */
    @Query("SELECT COALESCE(SUM(m.cantidad), 0) FROM MovimientoStock m WHERE m.pieza.id = :piezaId")
    BigDecimal sumarCantidades(@Param("piezaId") Long piezaId);

    /**
     * Unidades netas que una linea de OT ha sacado del almacen.
     *
     * <p>Se calcula sumando los movimientos de la linea y cambiando el signo: las
     * salidas son negativas y las devoluciones positivas, asi que el resultado es
     * lo que la linea tiene ahora mismo consumido. No se guarda en la linea a
     * proposito, igual que el stock: se deriva del libro de movimientos, que es
     * la unica fuente de verdad.
     */
    @Query("""
            SELECT COALESCE(-SUM(m.cantidad), 0)
              FROM MovimientoStock m
             WHERE m.lineaOt.id = :lineaId
            """)
    BigDecimal consumoNetoDeLinea(@Param("lineaId") Long lineaId);
}
