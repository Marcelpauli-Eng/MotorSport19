package com.motorsport19.taller.orden.service;

import java.math.BigDecimal;

/**
 * Pieza que no se pudo servir al intentar entrar en reparacion.
 *
 * @param sku         referencia de almacen
 * @param descripcion descripcion de la pieza
 * @param necesarias  unidades que pide la linea de la OT
 * @param disponibles unidades que habia en almacen
 * @param faltan      diferencia; es lo que hay que pedir al proveedor
 */
public record PiezaFaltante(
        Long piezaId,
        String sku,
        String descripcion,
        BigDecimal necesarias,
        BigDecimal disponibles,
        BigDecimal faltan
) {

    public String resumen() {
        return "%s (faltan %s de %s)".formatted(sku, faltan.toPlainString(), necesarias.toPlainString());
    }
}
