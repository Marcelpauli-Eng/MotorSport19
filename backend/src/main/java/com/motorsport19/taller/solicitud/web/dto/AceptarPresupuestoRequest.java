package com.motorsport19.taller.solicitud.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Lo que hace falta para abrir la orden cuando el cliente acepta: la moto ya
 * dada de alta y su kilometraje. El problema es lo que pidio en la web.
 */
public record AceptarPresupuestoRequest(
        @NotNull(message = "Hay que indicar la moto")
        Long motoId,

        @NotNull(message = "El kilometraje de entrada es obligatorio")
        @Min(value = 0, message = "El kilometraje no puede ser negativo")
        Integer kmEntrada,

        LocalDate fechaEstimadaSalida,
        Long tecnicoId,
        String observaciones) {
}
