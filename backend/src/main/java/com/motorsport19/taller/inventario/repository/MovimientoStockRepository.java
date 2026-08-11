package com.motorsport19.taller.inventario.repository;

import com.motorsport19.taller.inventario.domain.MovimientoStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface MovimientoStockRepository
        extends JpaRepository<MovimientoStock, Long>, JpaSpecificationExecutor<MovimientoStock> {

    // Las consultas traen pieza, usuario, orden y moto con JOIN FETCH porque la
    // respuesta los muestra y la sesion ya esta cerrada cuando se serializa
    // (open-in-view esta desactivado). De paso se evita el N+1 en listados
    // largos. Todas son asociaciones a-uno, asi que no multiplican filas y la
    // paginacion sigue siendo correcta.
    //
    // La orden y su moto se traen para que una salida diga a que reparacion y a
    // que moto se fue el material: sin eso el libro dice que salieron dos
    // pastillas pero no para quien, que es lo primero que se pregunta cuando un
    // recuento no cuadra.

    @Query(value = """
            SELECT m FROM MovimientoStock m
              JOIN FETCH m.pieza
              LEFT JOIN FETCH m.usuario
              LEFT JOIN FETCH m.ordenTrabajo o
              LEFT JOIN FETCH o.moto
             WHERE m.pieza.id = :piezaId
             ORDER BY m.fecha DESC, m.id DESC
            """,
            countQuery = "SELECT COUNT(m) FROM MovimientoStock m WHERE m.pieza.id = :piezaId")
    Page<MovimientoStock> buscarPorPieza(@Param("piezaId") Long piezaId, Pageable pageable);

    /**
     * Libro de movimientos con filtros opcionales.
     *
     * <p><b>Por que Specification y no una @Query como el resto del repositorio.</b>
     * El filtro tiene cuatro criterios opcionales, y escribirlos como
     * {@code (:parametro IS NULL OR campo = :parametro)} no funciona contra
     * PostgreSQL: cuando el parametro llega nulo el driver lo manda sin tipo y la
     * base responde «could not determine data type of parameter». El CAST
     * explicito solo salva el caso de texto —que es por lo que en
     * {@link PiezaRepository} sirve—, porque {@code cast(bytea as bigint)} ni
     * siquiera existe. Con Specification cada criterio entra en el WHERE solo si
     * viene informado, asi que nunca hay un parametro nulo que tipar. De regalo,
     * el SQL queda sin los {@code OR} que impedian usar los indices.
     *
     * <p>El {@code @EntityGraph} hace el trabajo del JOIN FETCH: trae pieza,
     * usuario, orden y moto en la misma consulta, que es lo que necesita la
     * respuesta con {@code open-in-view} desactivado.
     */
    @Override
    @EntityGraph(attributePaths = {"pieza", "usuario", "ordenTrabajo", "ordenTrabajo.moto"})
    Page<MovimientoStock> findAll(Specification<MovimientoStock> filtro, Pageable pageable);

    /** Carga un movimiento con todo resuelto, para devolverlo en la respuesta. */
    @Query("""
            SELECT m FROM MovimientoStock m
              JOIN FETCH m.pieza
              LEFT JOIN FETCH m.usuario
              LEFT JOIN FETCH m.ordenTrabajo o
              LEFT JOIN FETCH o.moto
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
