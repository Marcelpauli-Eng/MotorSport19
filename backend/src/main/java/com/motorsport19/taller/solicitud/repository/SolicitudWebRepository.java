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
    @EntityGraph(attributePaths = {"cita", "atendidaPor"})
    List<SolicitudWeb> findTop200ByEstadoOrderByRecibidaEnDesc(EstadoSolicitud estado);

    long countByEstado(EstadoSolicitud estado);
}
