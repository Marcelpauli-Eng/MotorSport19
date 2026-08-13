package com.motorsport19.taller.orden.web.dto;

import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.TipoLinea;

import java.math.BigDecimal;

/**
 * Linea de una orden de trabajo.
 *
 * @param precioUnitario  precio CONGELADO al crear la linea; no se recalcula desde
 *                        el catalogo aunque este haya cambiado despues
 * @param importeBruto    lo que valdria sin descuento, para poder enseñar el antes
 *                        y el despues
 * @param importeDescuento cuanto se le rebaja al cliente en euros; se calcula por
 *                        diferencia para que siempre cuadre con la base imponible
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
        BigDecimal importeBruto,
        BigDecimal importeDescuento,
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
                linea.importeBruto(),
                linea.importeDescuento(),
                linea.getBaseImponible(),
                linea.getCuotaIva(),
                linea.getTotal());
    }

    /**
     * La misma linea con el dinero fuera.
     *
     * <p>Se le sirve asi al tecnico: necesita saber que hay que hacer y cuantas
     * unidades monta, no a cuanto se lo cobra el taller al cliente. Los campos
     * viajan a nulo en vez de omitirse para que el contrato de la API no cambie
     * segun quien pregunte.
     */
    public LineaOTResponse sinImportes() {
        return new LineaOTResponse(
                id, numeroLinea, tipo, tipoDescripcion, descripcion, piezaId, piezaSku, cantidad,
                null, null, null, null, null, null, null, null, null);
    }
}
