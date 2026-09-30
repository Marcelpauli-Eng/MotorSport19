package com.motorsport19.taller.solicitud.repository;

import com.motorsport19.taller.solicitud.domain.FotoSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FotoSolicitudRepository extends JpaRepository<FotoSolicitud, Long> {

    Optional<FotoSolicitud> findBySolicitudIdAndOrden(Long solicitudId, short orden);
}
