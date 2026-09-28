package com.motorsport19.taller.factura.repository;

import com.motorsport19.taller.factura.domain.FacturaAnterior;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FacturaAnteriorRepository extends JpaRepository<FacturaAnterior, Long> {

    @EntityGraph(attributePaths = {"moto", "lineas"})
    List<FacturaAnterior> findByClienteIdOrderByFechaDescNumeroDesc(Long clienteId);

    @EntityGraph(attributePaths = {"moto", "lineas"})
    List<FacturaAnterior> findByMotoIdOrderByFechaDescNumeroDesc(Long motoId);

    /** El PDF original, que la entidad no mapea para no cargarlo en cada listado. */
    @Query(value = "SELECT pdf FROM factura_anterior WHERE id = :id", nativeQuery = true)
    Optional<byte[]> pdfDe(@Param("id") Long id);
}
