package com.motorsport19.taller.solicitud.web.dto;

import com.motorsport19.taller.orden.web.dto.LineaOTResponse;
import com.motorsport19.taller.solicitud.domain.EstadoSolicitud;
import com.motorsport19.taller.solicitud.domain.SolicitudWeb;

import java.math.BigDecimal;
import java.util.List;

/**
 * El presupuesto de una solicitud web, con los mismos nombres que el de una
 * orden: la pantalla de presupuesto es la misma para los dos.
 */
public record PresupuestoWebResponse(
        Long id,
        /** Lo que sale de titulo y en el PDF: «Presupuesto web 12». */
        String codigo,
        EstadoSolicitud estado,
        String estadoDescripcion,
        String clienteNombre,
        String clienteTelefono,
        String clienteEmail,
        String descripcionMoto,
        String matricula,
        String necesita,
        String idioma,
        BigDecimal tarifaHora,
        String tipoIva,
        boolean permiteEditarLineas,
        List<LineaOTResponse> lineas,
        BigDecimal horasManoDeObra,
        BigDecimal importeBruto,
        BigDecimal totalDescuento,
        BigDecimal baseImponible,
        BigDecimal totalIva,
        BigDecimal total,
        Long ordenTrabajoId,
        String ordenTrabajoCodigo
) {

    public static PresupuestoWebResponse de(SolicitudWeb s) {
        return new PresupuestoWebResponse(
                s.getId(), "Presupuesto web " + s.getId(), s.getEstado(), s.getEstado().getDescripcion(),
                s.getNombre(), s.getTelefono(), s.getEmail(), s.getMarca() + " " + s.getModelo(),
                s.getMatricula(), s.getNecesita(), s.getIdioma(), s.getTarifaHora(), s.getTipoIva(),
                s.permiteEditarLineas(),
                s.getLineas().stream().map(LineaOTResponse::de).toList(),
                s.horasManoDeObra(), s.importeBruto(), s.totalDescuento(), s.baseImponible(), s.totalIva(),
                s.total(),
                s.getOrdenTrabajo() == null ? null : s.getOrdenTrabajo().getId(),
                s.getOrdenTrabajo() == null ? null : s.getOrdenTrabajo().codigoVisible());
    }
}
