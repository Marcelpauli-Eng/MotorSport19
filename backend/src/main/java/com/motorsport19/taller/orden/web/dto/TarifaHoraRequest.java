package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Precio de la hora pactado para una orden concreta. */
public record TarifaHoraRequest(
        @NotNull(message = "El precio de la hora es obligatorio")
        @Positive(message = "El precio de la hora tiene que ser mayor que cero")
        BigDecimal tarifaHora) {
}
