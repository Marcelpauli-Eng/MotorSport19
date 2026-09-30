package com.motorsport19.taller.solicitud.repository;

import com.motorsport19.taller.solicitud.domain.EstadoSolicitud;
import com.motorsport19.taller.solicitud.domain.SolicitudWeb;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudWebRepository extends JpaRepository<SolicitudWeb, Long> {

    Optional<SolicitudWeb> findByReferencia(String referencia);

    /**
     * La bandeja, de la mas nueva a la mas vieja.
     *
     * <p>Con tope: las pendientes de un taller son unas pocas, y de las cerradas
     * solo interesan las ultimas.
     */
    @EntityGraph(attributePaths = {"cita", "atendidaPor", "presupuestadaPor", "ordenTrabajo"})
    List<SolicitudWeb> findTop200ByEstadoOrderByRecibidaEnDesc(EstadoSolicitud estado);

    /**
     * Una solicitud con lo que enseña su ficha ya cargado. La respuesta se arma
     * fuera de la transacción (open-in-view está apagado) y sin esto la cita o
     * quien la atendió llegarían como proxies sin sesión.
     */
    @EntityGraph(attributePaths = {"cita", "atendidaPor", "presupuestadaPor", "ordenTrabajo"})
    Optional<SolicitudWeb> findConRelacionesById(Long id);

    long countByEstado(EstadoSolicitud estado);
}
