package com.motorsport19.taller.factura.repository;

import com.motorsport19.taller.factura.domain.SerieFactura;
import com.motorsport19.taller.factura.domain.TipoFactura;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SerieFacturaRepository extends JpaRepository<SerieFactura, Long> {

    /**
     * Carga la serie bloqueando su fila hasta el final de la transaccion.
     *
     * <p>Es lo que serializa las emisiones concurrentes de la misma serie: sin el
     * bloqueo, dos facturas podrian leer el mismo "ultimo numero" y pelearse por
     * el siguiente. Con el, la segunda espera a que la primera termine.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SerieFactura s WHERE s.id = :id")
    Optional<SerieFactura> bloquear(@Param("id") Long id);

    @Query("""
            SELECT s FROM SerieFactura s
             WHERE s.ejercicio = :ejercicio AND s.tipo = :tipo AND s.activa = TRUE
             ORDER BY s.codigo
            """)
    List<SerieFactura> buscarActivas(@Param("ejercicio") Integer ejercicio,
                                     @Param("tipo") TipoFactura tipo);

    List<SerieFactura> findByActivaTrueOrderByEjercicioDescCodigoAsc();

    /** Todas, incluidas las cerradas: es lo que se mantiene desde Ajustes. */
    List<SerieFactura> findAllByOrderByEjercicioDescCodigoAsc();

    /** Para avisar de un duplicado antes de que lo haga el UNIQUE de la tabla. */
    @Query("SELECT s FROM SerieFactura s WHERE s.codigo = :codigo AND s.ejercicio = :ejercicio")
    Optional<SerieFactura> buscarPorCodigoYEjercicio(@Param("codigo") String codigo,
                                                     @Param("ejercicio") Integer ejercicio);
}
