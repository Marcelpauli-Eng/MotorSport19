package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Linea de pieza.
 *
 * <p>Anadirla al presupuesto NO saca material del almacen: eso ocurre al entrar
 * en reparacion. Lo que si hace es congelar el precio de catalogo en la linea.
 */
public record PiezaLineaRequest(

        @NotNull(message = "Hay que indicar la pieza")
        Long piezaId,

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero")
        BigDecimal cantidad,

        @DecimalMin(value = "0.0", message = "El descuento no puede ser negativo")
        @DecimalMax(value = "100.0", message = "El descuento no puede superar el 100%")
        BigDecimal descuentoPct
) {
}
