package com.motorsport19.taller.inventario.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Ajuste tras inventario fisico.
 *
 * @param cantidad cantidad CON SIGNO: positiva si aparecen unidades, negativa si
 *                 faltan. Es tambien la unica forma de corregir un movimiento
 *                 equivocado, porque el libro es inmutable.
 * @param motivo   obligatorio: un descuadre sin explicacion no sirve de nada
 *                 dentro de seis meses
 */
public record AjusteStockRequest(

        @NotNull(message = "La cantidad es obligatoria")
        BigDecimal cantidad,

        @NotBlank(message = "Todo ajuste de inventario debe indicar el motivo")
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo
) {
}
