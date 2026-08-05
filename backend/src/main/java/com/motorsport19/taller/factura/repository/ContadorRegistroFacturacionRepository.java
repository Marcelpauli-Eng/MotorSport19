package com.motorsport19.taller.factura.repository;

import com.motorsport19.taller.factura.domain.ContadorRegistroFacturacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ContadorRegistroFacturacionRepository
        extends JpaRepository<ContadorRegistroFacturacion, Integer> {

    /**
     * Bloquea el contador global.
     *
     * <p>Serializa TODAS las emisiones, sean de la serie que sean: la cadena de
     * huellas es unica, y dos facturas no pueden encadenar a la vez con la misma
     * huella anterior.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContadorRegistroFacturacion c WHERE c.id = 1")
    Optional<ContadorRegistroFacturacion> bloquear();
}
