package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Devolucion al almacen de piezas que una linea ya habia consumido. */
public record DevolucionLineaRequest(

        @NotNull(message = "La cantidad a devolver es obligatoria")
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero")
        BigDecimal cantidad,

        @NotBlank(message = "Hay que indicar el motivo de la devolucion")
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo
) {
}
