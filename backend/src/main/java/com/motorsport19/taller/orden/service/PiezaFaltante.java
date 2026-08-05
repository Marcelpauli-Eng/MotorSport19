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
        return "%s (faltan %s de %s)".formatted(sku, cantidad(faltan), cantidad(necesarias));
    }

    /**
     * Cantidad sin ceros decimales sobrantes.
     *
     * <p>Las cantidades se guardan con tres decimales, asi que una unidad sale
     * como "1.000". En un texto en espanol eso se lee como MIL, que es
     * exactamente lo contrario de lo que pasa. Aqui queda en "1".
     */
    private static String cantidad(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}
