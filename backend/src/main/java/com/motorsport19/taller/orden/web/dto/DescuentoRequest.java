package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Descuento en tanto por ciento.
 *
 * <p>Vale tanto para una linea suelta como para el descuento general de la
 * orden, que es el mismo dato aplicado a todas.
 */
public record DescuentoRequest(
        @NotNull(message = "Hay que indicar el descuento")
        @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
        @DecimalMax(value = "100.00", message = "El descuento no puede pasar del 100 %")
        BigDecimal descuentoPct,

        /**
         * Confirma bajar tambien las lineas que llevaban mas descuento.
         *
         * <p>Se manda solo cuando el usuario ya ha visto el aviso de que lineas
         * pierden descuento y ha dicho que si. Sin el, esa operacion se para.
         */
        Boolean forzar
) {
    public boolean forzado() {
        return Boolean.TRUE.equals(forzar);
    }
}
