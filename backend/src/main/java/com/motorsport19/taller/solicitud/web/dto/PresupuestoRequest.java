package com.motorsport19.taller.solicitud.web.dto;

import com.motorsport19.taller.solicitud.domain.CanalPresupuesto;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** El presupuesto que se le manda al cliente: cuanto, que incluye y por donde. */
public record PresupuestoRequest(
        @NotNull(message = "Falta el importe del presupuesto")
        @Positive(message = "El importe tiene que ser mayor que cero")
        @Digits(integer = 8, fraction = 2, message = "Importe no valido")
        BigDecimal importe,

        @Size(max = 3000, message = "El detalle no puede superar los 3000 caracteres")
        String detalle,

        @NotNull(message = "Falta por donde se manda: WhatsApp o email")
        CanalPresupuesto canal) {
}
