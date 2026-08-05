package com.motorsport19.taller.factura.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Emision de la factura de una orden de trabajo.
 *
 * @param fechaEmision opcional; si no se indica se usa la fecha de hoy
 */
public record EmitirFacturaRequest(

        @NotNull(message = "Hay que indicar la orden de trabajo a facturar")
        Long ordenTrabajoId,

        @NotNull(message = "Hay que indicar la serie de facturacion")
        Long serieId,

        LocalDate fechaEmision
) {
}
