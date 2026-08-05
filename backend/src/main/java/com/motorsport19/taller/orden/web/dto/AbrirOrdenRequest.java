package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AbrirOrdenRequest(

        @NotNull(message = "Hay que indicar la moto")
        Long motoId,

        @NotBlank(message = "Hay que describir el problema que reporta el cliente")
        String problemaReportado,

        @NotNull(message = "El kilometraje de entrada es obligatorio")
        @Min(value = 0, message = "El kilometraje no puede ser negativo")
        Integer kmEntrada,

        LocalDate fechaEstimadaSalida,

        Long tecnicoId,

        String observaciones
) {
}
