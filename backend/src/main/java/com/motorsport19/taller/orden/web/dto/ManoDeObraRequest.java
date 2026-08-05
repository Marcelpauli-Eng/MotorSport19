package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Linea de horas de taller. Se valoran a la tarifa congelada de la OT. */
public record ManoDeObraRequest(

        @NotBlank(message = "La descripcion del trabajo es obligatoria")
        @Size(max = 300, message = "La descripcion no puede superar los 300 caracteres")
        String descripcion,

        @NotNull(message = "Las horas son obligatorias")
        @DecimalMin(value = "0.001", message = "Las horas deben ser mayores que cero")
        BigDecimal horas,

        @DecimalMin(value = "0.0", message = "El descuento no puede ser negativo")
        @DecimalMax(value = "100.0", message = "El descuento no puede superar el 100%")
        BigDecimal descuentoPct,

        @Size(max = 20, message = "El tipo de IVA no puede superar los 20 caracteres")
        String tipoIva
) {
}
