package com.motorsport19.taller.factura.web.dto;

import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.TipoFactura;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila del libro de facturas.
 *
 * <p>No incluye lineas ni desglose a proposito: en un listado no se necesitan, y
 * cargarlos obligaria a una consulta extra por cada fila.
 */
public record FacturaResumenResponse(
        Long id,
        Long numeroRegistro,
        String numeroCompleto,
        TipoFactura tipo,
        LocalDate fechaEmision,
        String receptorNombre,
        String receptorNif,
        BigDecimal baseImponible,
        BigDecimal totalIva,
        BigDecimal total,
        String codigoOt,
        String matricula,
        String rectificaA,
        String huella
) {

    public static FacturaResumenResponse de(Factura f) {
        return new FacturaResumenResponse(
                f.getId(),
                f.getNumeroRegistro(),
                f.getNumeroCompleto(),
                f.getTipo(),
                f.getFechaEmision(),
                f.getDatosReceptor().getNombre(),
                f.getDatosReceptor().getNif(),
                f.getBaseImponible(),
                f.getTotalIva(),
                f.getTotal(),
                f.getCodigoOt(),
                f.getMatricula(),
                f.getFacturaRectificada() == null ? null : f.getFacturaRectificada().getNumeroCompleto(),
                f.getHuella());
    }
}
