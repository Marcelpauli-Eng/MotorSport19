package com.motorsport19.taller.orden.repository;

import com.motorsport19.taller.orden.domain.EstadoOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrdenTrabajoRepository extends JpaRepository<OrdenTrabajo, Long> {

    /**
     * Carga la OT con moto, cliente y tecnico resueltos.
     *
     * <p>Las lineas y el historial NO se traen aqui: cargar tres colecciones a la
     * vez multiplicaria las filas del resultado. Se piden por separado cuando
     * hacen falta.
     */
    @Query("""
            SELECT o FROM OrdenTrabajo o
              JOIN FETCH o.moto m
              JOIN FETCH o.cliente
              LEFT JOIN FETCH o.tecnico
             WHERE o.id = :id
            """)
    Optional<OrdenTrabajo> buscarConDetalle(@Param("id") Long id);

    @Query("""
            SELECT o FROM OrdenTrabajo o
              JOIN FETCH o.moto
              JOIN FETCH o.cliente
              LEFT JOIN FETCH o.tecnico
             WHERE o.codigo = :codigo
            """)
    Optional<OrdenTrabajo> buscarPorCodigo(@Param("codigo") String codigo);

    /** Tablero del taller: ordenes abiertas, con filtros opcionales. */
    @Query(value = """
            SELECT o FROM OrdenTrabajo o
              JOIN FETCH o.moto
              JOIN FETCH o.cliente
              LEFT JOIN FETCH o.tecnico
             WHERE (:estado     IS NULL OR o.estado = :estado)
               AND (:tecnicoId  IS NULL OR o.tecnico.id = :tecnicoId)
               AND (:clienteId  IS NULL OR o.cliente.id = :clienteId)
               AND (:motoId     IS NULL OR o.moto.id = :motoId)
               AND (:soloAbiertas = FALSE OR o.estado NOT IN
                    (com.motorsport19.taller.orden.domain.EstadoOT.ENTREGADA,
                     com.motorsport19.taller.orden.domain.EstadoOT.RECHAZADA))
            """,
            countQuery = """
            SELECT COUNT(o) FROM OrdenTrabajo o
             WHERE (:estado     IS NULL OR o.estado = :estado)
               AND (:tecnicoId  IS NULL OR o.tecnico.id = :tecnicoId)
               AND (:clienteId  IS NULL OR o.cliente.id = :clienteId)
               AND (:motoId     IS NULL OR o.moto.id = :motoId)
               AND (:soloAbiertas = FALSE OR o.estado NOT IN
                    (com.motorsport19.taller.orden.domain.EstadoOT.ENTREGADA,
                     com.motorsport19.taller.orden.domain.EstadoOT.RECHAZADA))
            """)
    Page<OrdenTrabajo> buscar(@Param("estado") EstadoOT estado,
                              @Param("tecnicoId") Long tecnicoId,
                              @Param("clienteId") Long clienteId,
                              @Param("motoId") Long motoId,
                              @Param("soloAbiertas") boolean soloAbiertas,
                              Pageable pageable);

    /** Historial completo de una moto, de la intervencion mas reciente a la mas antigua. */
    @Query("""
            SELECT o FROM OrdenTrabajo o
              LEFT JOIN FETCH o.tecnico
             WHERE o.moto.id = :motoId
             ORDER BY o.fechaEntrada DESC, o.id DESC
            """)
    List<OrdenTrabajo> historialDeMoto(@Param("motoId") Long motoId);

    /** Cuenta cuantas ordenes tiene una moto sin cerrar. */
    @Query("""
            SELECT COUNT(o) FROM OrdenTrabajo o
             WHERE o.moto.id = :motoId
               AND o.estado NOT IN
                   (com.motorsport19.taller.orden.domain.EstadoOT.ENTREGADA,
                    com.motorsport19.taller.orden.domain.EstadoOT.RECHAZADA)
            """)
    long contarAbiertasDeMoto(@Param("motoId") Long motoId);
}
