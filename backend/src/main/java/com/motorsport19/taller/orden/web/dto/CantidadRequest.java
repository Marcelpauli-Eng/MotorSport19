package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CantidadRequest(

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero")
        BigDecimal cantidad
) {
}
