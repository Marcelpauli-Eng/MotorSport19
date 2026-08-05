package com.motorsport19.taller.orden.web.dto;

import com.motorsport19.taller.orden.domain.CambioEstadoOT;
import com.motorsport19.taller.orden.domain.EstadoOT;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Ficha completa de una orden de trabajo.
 *
 * @param estadosPosibles a que estados puede saltar desde donde esta. El frontend
 *                        lo usa para pintar solo los botones que tienen sentido,
 *                        en vez de dejar al usuario probar y recibir un error.
 * @param facturable      si esta en un estado desde el que se puede emitir factura
 */
public record OrdenTrabajoResponse(
        Long id,
        String codigo,
        Integer ejercicio,
        Integer numero,
        EstadoOT estado,
        String estadoDescripcion,
        Set<EstadoOT> estadosPosibles,
        boolean facturable,
        boolean permiteEditarLineas,

        Long motoId,
        String matricula,
        String descripcionMoto,
        Long clienteId,
        String clienteNombre,

        Instant fechaEntrada,
        LocalDate fechaEstimadaSalida,
        Instant fechaRealSalida,
        Integer kmEntrada,

        String problemaReportado,
        String diagnostico,
        Long tecnicoId,
        String tecnicoNombre,

        BigDecimal tarifaHora,
        Instant fechaPresupuesto,
        Instant fechaAprobacion,
        String aprobadoPor,
        String motivoRechazo,
        String observaciones,

        BigDecimal baseImponible,
        BigDecimal totalIva,
        BigDecimal total,
        BigDecimal horasManoDeObra,

        List<LineaOTResponse> lineas,
        List<CambioEstadoResponse> historial
) {

    public static OrdenTrabajoResponse de(OrdenTrabajo orden, List<LineaOT> lineas,
                                          List<CambioEstadoOT> historial) {
        BigDecimal base = sumar(lineas, LineaOT::getBaseImponible);
        BigDecimal iva = sumar(lineas, LineaOT::getCuotaIva);
        BigDecimal total = sumar(lineas, LineaOT::getTotal);
        BigDecimal horas = lineas.stream()
                .filter(l -> !l.esDePieza())
                .map(LineaOT::getCantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrdenTrabajoResponse(
                orden.getId(),
                orden.codigoVisible(),
                orden.getEjercicio(),
                orden.getNumero(),
                orden.getEstado(),
                orden.getEstado().getDescripcion(),
                orden.getEstado().siguientesPosibles(),
                orden.puedeFacturarse(),
                orden.getEstado().permiteEditarLineas(),

                orden.getMoto().getId(),
                orden.getMoto().getMatricula(),
                orden.getMoto().descripcion(),
                orden.getCliente().getId(),
                orden.getCliente().nombreCompleto(),

                orden.getFechaEntrada(),
                orden.getFechaEstimadaSalida(),
                orden.getFechaRealSalida(),
                orden.getKmEntrada(),

                orden.getProblemaReportado(),
                orden.getDiagnostico(),
                orden.getTecnico() == null ? null : orden.getTecnico().getId(),
                orden.getTecnico() == null ? null : orden.getTecnico().getNombreCompleto(),

                orden.getTarifaHora(),
                orden.getFechaPresupuesto(),
                orden.getFechaAprobacion(),
                orden.getAprobadoPor(),
                orden.getMotivoRechazo(),
                orden.getObservaciones(),

                base, iva, total, horas,

                lineas.stream().map(LineaOTResponse::de).toList(),
                historial.stream().map(CambioEstadoResponse::de).toList());
    }

    private static BigDecimal sumar(List<LineaOT> lineas,
                                    java.util.function.Function<LineaOT, BigDecimal> campo) {
        return lineas.stream()
                .map(campo)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
