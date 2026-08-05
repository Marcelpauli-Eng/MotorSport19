package com.motorsport19.taller.orden.repository;

import com.motorsport19.taller.orden.domain.ContadorOt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContadorOtRepository extends JpaRepository<ContadorOt, Integer> {

    /**
     * Carga el contador del ejercicio bloqueando su fila hasta el final de la
     * transaccion, de modo que dos altas simultaneas se serializan y no pueden
     * obtener el mismo numero de orden.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContadorOt c WHERE c.ejercicio = :ejercicio")
    Optional<ContadorOt> bloquearEjercicio(@Param("ejercicio") Integer ejercicio);
}
