package com.motorsport19.taller.orden.web.dto;

import com.motorsport19.taller.orden.domain.EstadoOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;

import java.time.Instant;
import java.time.LocalDate;

/** Fila del tablero del taller. */
public record OrdenTrabajoResumenResponse(
        Long id,
        String codigo,
        EstadoOT estado,
        String estadoDescripcion,
        String matricula,
        String descripcionMoto,
        String clienteNombre,
        String tecnicoNombre,
        Instant fechaEntrada,
        LocalDate fechaEstimadaSalida,
        Instant fechaRealSalida,
        String problemaReportado
) {

    public static OrdenTrabajoResumenResponse de(OrdenTrabajo orden) {
        return new OrdenTrabajoResumenResponse(
                orden.getId(),
                orden.codigoVisible(),
                orden.getEstado(),
                orden.getEstado().getDescripcion(),
                orden.getMoto().getMatricula(),
                orden.getMoto().descripcion(),
                orden.getCliente().nombreCompleto(),
                orden.getTecnico() == null ? null : orden.getTecnico().getNombreCompleto(),
                orden.getFechaEntrada(),
                orden.getFechaEstimadaSalida(),
                orden.getFechaRealSalida(),
                orden.getProblemaReportado());
    }
}
