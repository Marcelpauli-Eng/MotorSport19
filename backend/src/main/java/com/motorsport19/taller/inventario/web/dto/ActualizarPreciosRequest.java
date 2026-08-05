package com.motorsport19.taller.inventario.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Cambio de precios de catalogo. No afecta a ninguna OT abierta: sus lineas
 * conservan el precio congelado con el que se anadieron.
 */
public record ActualizarPreciosRequest(

        @NotNull(message = "El precio de coste es obligatorio")
        @DecimalMin(value = "0.0", message = "El precio de coste no puede ser negativo")
        BigDecimal precioCoste,

        @NotNull(message = "El precio de venta es obligatorio")
        @DecimalMin(value = "0.0", message = "El precio de venta no puede ser negativo")
        BigDecimal precioVenta
) {
}
