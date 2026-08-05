package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.Size;

/**
 * @param aprobadoPor quien acepta el presupuesto por parte del cliente. Si no se
 *                    indica, se registra el nombre del titular de la ficha.
 */
public record AprobacionRequest(

        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String aprobadoPor
) {
}
