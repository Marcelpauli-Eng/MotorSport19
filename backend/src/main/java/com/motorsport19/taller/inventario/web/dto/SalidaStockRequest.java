package com.motorsport19.taller.inventario.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Salida de almacen no ligada a una orden de trabajo (uso interno, garantia,
 * merma). El motivo es obligatorio: una salida sin justificar es un agujero en
 * el inventario.
 *
 * <p>El consumo de piezas en una OT no pasa por aqui: lo genera automaticamente
 * el servicio de ordenes de trabajo al entrar en reparacion.
 */
public record SalidaStockRequest(

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.001", message = "La cantidad de una salida debe ser mayor que cero")
        BigDecimal cantidad,

        @NotBlank(message = "Toda salida de almacen debe indicar el motivo")
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo
) {
}
