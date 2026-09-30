package com.motorsport19.taller.factura.repository;

import com.motorsport19.taller.factura.domain.FacturaAnterior;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FacturaAnteriorRepository extends JpaRepository<FacturaAnterior, Long> {

    @EntityGraph(attributePaths = {"moto", "lineas"})
    List<FacturaAnterior> findByClienteIdOrderByFechaDescNumeroDesc(Long clienteId);

    @EntityGraph(attributePaths = {"moto", "lineas"})
    List<FacturaAnterior> findByMotoIdOrderByFechaDescNumeroDesc(Long motoId);

    /** Con los mismos filtros que el libro de facturas (FacturaRepository.buscar). */
    @EntityGraph(attributePaths = {"moto"})
    @Query("""
            SELECT f FROM FacturaAnterior f
             WHERE f.fecha >= COALESCE(:desde, f.fecha)
               AND f.fecha <= COALESCE(:hasta, f.fecha)
               AND (:clienteId IS NULL OR f.clienteId = :clienteId)
               AND (:conIva IS NULL
                    OR (:conIva = TRUE  AND f.totalIva <> 0)
                    OR (:conIva = FALSE AND f.totalIva  = 0))
             ORDER BY f.fecha DESC, f.numero DESC
            """)
    List<FacturaAnterior> buscar(@Param("desde") LocalDate desde,
                                 @Param("hasta") LocalDate hasta,
                                 @Param("clienteId") Long clienteId,
                                 @Param("conIva") Boolean conIva);

    /** El PDF original, que la entidad no mapea para no cargarlo en cada listado. */
    @Query(value = "SELECT pdf FROM factura_anterior WHERE id = :id", nativeQuery = true)
    Optional<byte[]> pdfDe(@Param("id") Long id);
}
