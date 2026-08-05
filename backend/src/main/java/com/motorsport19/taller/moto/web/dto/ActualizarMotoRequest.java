package com.motorsport19.taller.moto.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Modificacion de los datos de la moto. El kilometraje no se toca aqui: tiene su
 * propio endpoint porque solo puede aumentar.
 */
public record ActualizarMotoRequest(

        @NotBlank(message = "La matricula es obligatoria")
        @Size(max = 15, message = "La matricula no puede superar los 15 caracteres")
        String matricula,

        @NotBlank(message = "La marca es obligatoria")
        @Size(max = 60, message = "La marca no puede superar los 60 caracteres")
        String marca,

        @NotBlank(message = "El modelo es obligatorio")
        @Size(max = 100, message = "El modelo no puede superar los 100 caracteres")
        String modelo,

        @Min(value = 1885, message = "El ano no es valido para una motocicleta")
        Integer anio,

        @Positive(message = "La cilindrada debe ser mayor que cero")
        Integer cilindrada,

        @Size(max = 50, message = "El color no puede superar los 50 caracteres")
        String color,

        @Size(max = 30, message = "El numero de bastidor no puede superar los 30 caracteres")
        String numeroBastidor,

        String observaciones
) {
}
