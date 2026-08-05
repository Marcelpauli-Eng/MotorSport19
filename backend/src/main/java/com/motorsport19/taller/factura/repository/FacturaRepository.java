package com.motorsport19.taller.factura.repository;

import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.TipoFactura;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Acceso al registro de facturacion.
 *
 * <p>No se expone ninguna operacion de modificacion ni de borrado: las facturas
 * son inmutables y la base de datos rechazaria el intento igualmente. Lo unico
 * que se hace aqui es insertar y leer.
 */
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    @Query("""
            SELECT f FROM Factura f
              JOIN FETCH f.serie
              LEFT JOIN FETCH f.facturaRectificada
             WHERE f.id = :id
            """)
    Optional<Factura> buscarConDetalle(@Param("id") Long id);

    @Query("""
            SELECT f FROM Factura f
              JOIN FETCH f.serie
              LEFT JOIN FETCH f.facturaRectificada
             WHERE f.numeroCompleto = :numeroCompleto
            """)
    Optional<Factura> buscarPorNumeroCompleto(@Param("numeroCompleto") String numeroCompleto);

    /**
     * Ultima factura de la cadena.
     *
     * <p>Su huella es la que debe declarar como "anterior" la siguiente factura
     * que se emita.
     */
    @Query("SELECT f FROM Factura f ORDER BY f.numeroRegistro DESC LIMIT 1")
    Optional<Factura> buscarUltimaDeLaCadena();

    /** Facturas ordinarias ya emitidas para una orden de trabajo. */
    @Query("""
            SELECT f FROM Factura f
             WHERE f.ordenTrabajo.id = :ordenId
               AND f.tipo = com.motorsport19.taller.factura.domain.TipoFactura.ORDINARIA
            """)
    List<Factura> buscarOrdinariasDeOrden(@Param("ordenId") Long ordenId);

    /** Rectificativas emitidas sobre una factura. */
    @Query("SELECT f FROM Factura f WHERE f.facturaRectificada.id = :facturaId ORDER BY f.numeroRegistro")
    List<Factura> buscarRectificativasDe(@Param("facturaId") Long facturaId);

    /**
     * Recorre el registro completo en orden de cadena.
     *
     * <p>Se devuelve como {@code Stream} para poder verificar o exportar libros de
     * miles de facturas sin cargarlas todas en memoria a la vez.
     */
    @Query("SELECT f FROM Factura f ORDER BY f.numeroRegistro ASC")
    Stream<Factura> recorrerCadena();

    @Query(value = """
            SELECT f FROM Factura f
              JOIN FETCH f.serie
              LEFT JOIN FETCH f.facturaRectificada
             WHERE (:tipo   IS NULL OR f.tipo = :tipo)
               AND (:desde  IS NULL OR f.fechaEmision >= :desde)
               AND (:hasta  IS NULL OR f.fechaEmision <= :hasta)
               AND (:receptorId IS NULL OR f.receptor.id = :receptorId)
             ORDER BY f.numeroRegistro DESC
            """,
            countQuery = """
            SELECT COUNT(f) FROM Factura f
             WHERE (:tipo   IS NULL OR f.tipo = :tipo)
               AND (:desde  IS NULL OR f.fechaEmision >= :desde)
               AND (:hasta  IS NULL OR f.fechaEmision <= :hasta)
               AND (:receptorId IS NULL OR f.receptor.id = :receptorId)
            """)
    Page<Factura> buscar(@Param("tipo") TipoFactura tipo,
                         @Param("desde") LocalDate desde,
                         @Param("hasta") LocalDate hasta,
                         @Param("receptorId") Long receptorId,
                         Pageable pageable);

    /** Facturas de un rango de fechas, en orden de cadena, para exportar. */
    @Query("""
            SELECT f FROM Factura f
             WHERE (:desde IS NULL OR f.fechaEmision >= :desde)
               AND (:hasta IS NULL OR f.fechaEmision <= :hasta)
             ORDER BY f.numeroRegistro ASC
            """)
    List<Factura> buscarParaExportar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    long countByTipo(TipoFactura tipo);
}
