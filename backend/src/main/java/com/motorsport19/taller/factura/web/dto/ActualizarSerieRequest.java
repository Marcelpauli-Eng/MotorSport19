package com.motorsport19.taller.factura.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mantenimiento de una serie ya abierta.
 *
 * <p>Solo la descripcion y si sigue abierta. El codigo, el ejercicio y el tipo
 * van impresos en el numero de cada factura ya emitida y no se tocan.
 *
 * @param activa false cierra la serie para nuevas facturas; las ya emitidas se
 *               quedan donde estan
 */
public record ActualizarSerieRequest(
        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 150, message = "La descripcion no puede pasar de 150 caracteres")
        String descripcion,

        boolean activa
) {
}
