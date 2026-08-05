package com.motorsport19.taller.configuracion.repository;

import com.motorsport19.taller.configuracion.domain.TipoIva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoIvaRepository extends JpaRepository<TipoIva, String> {

    List<TipoIva> findByActivoTrueOrderByPorcentajeDesc();
}
