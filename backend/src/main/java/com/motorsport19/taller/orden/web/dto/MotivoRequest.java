package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo comun de las operaciones que exigen justificacion (rechazo, bloqueo). */
public record MotivoRequest(

        @NotBlank(message = "Hay que indicar el motivo")
        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo
) {
}
