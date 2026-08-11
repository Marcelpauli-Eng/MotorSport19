package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Precio cerrado para una linea de mano de obra.
 *
 * <p>Admite el cero: un trabajo se puede dejar sin cobrar (una garantia, un
 * detalle a un cliente de siempre) y aun asi conviene que salga en el
 * presupuesto, para que el cliente vea lo que se le ha hecho.
 */
public record PrecioLineaRequest(
        @NotNull(message = "El precio es obligatorio")
        @PositiveOrZero(message = "El precio no puede ser negativo")
        BigDecimal precioUnitario) {
}
