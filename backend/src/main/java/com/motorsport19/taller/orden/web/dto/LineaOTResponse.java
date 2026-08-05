package com.motorsport19.taller.orden.web.dto;

import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.TipoLinea;

import java.math.BigDecimal;

/**
 * Linea de una orden de trabajo.
 *
 * @param precioUnitario precio CONGELADO al crear la linea; no se recalcula desde
 *                       el catalogo aunque este haya cambiado despues
 */
public record LineaOTResponse(
        Long id,
        Integer numeroLinea,
        TipoLinea tipo,
        String tipoDescripcion,
        String descripcion,
        Long piezaId,
        String piezaSku,
        BigDecimal cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuentoPct,
        String tipoIva,
        BigDecimal porcentajeIva,
        BigDecimal baseImponible,
        BigDecimal cuotaIva,
        BigDecimal total
) {

    public static LineaOTResponse de(LineaOT linea) {
        return new LineaOTResponse(
                linea.getId(),
                linea.getNumeroLinea(),
                linea.getTipo(),
                linea.getTipo().getDescripcion(),
                linea.getDescripcion(),
                linea.getPieza() == null ? null : linea.getPieza().getId(),
                linea.skuPieza(),
                linea.getCantidad(),
                linea.getPrecioUnitario(),
                linea.getDescuentoPct(),
                linea.getTipoIva(),
                linea.getPorcentajeIva(),
                linea.getBaseImponible(),
                linea.getCuotaIva(),
                linea.getTotal());
    }
}
