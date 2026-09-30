package com.motorsport19.taller.solicitud.web.dto;

import com.motorsport19.taller.solicitud.domain.CanalPresupuesto;
import jakarta.validation.constraints.NotNull;

/** Por donde se le ha mandado el presupuesto. El importe es el total de sus lineas. */
public record EnvioPresupuestoRequest(
        @NotNull(message = "Falta por donde se manda: WhatsApp o email")
        CanalPresupuesto canal) {
}
