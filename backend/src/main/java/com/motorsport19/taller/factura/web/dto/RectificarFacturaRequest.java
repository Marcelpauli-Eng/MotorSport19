package com.motorsport19.taller.factura.web.dto;

import com.motorsport19.taller.factura.domain.TipoRectificativa;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Emision de una factura rectificativa.
 *
 * @param tipoRectificativa solo se admite POR_DIFERENCIAS: una por sustitucion
 *                          se contaria dos veces en los informes
 * @param lineas            lo que cambia, en negativo lo que se quita. Vacias, se
 *                          anula lo que la factura vale hoy
 */
public record RectificarFacturaRequest(

        @NotNull(message = "Hay que indicar la serie de rectificativas")
        Long serieId,

        @NotNull(message = "Hay que indicar el tipo de rectificativa: POR_DIFERENCIAS")
        TipoRectificativa tipoRectificativa,

        @NotBlank(message = "Hay que explicar el motivo de la rectificacion")
        String motivo,

        @Valid
        List<LineaRectificativaRequest> lineas,

        LocalDate fechaEmision
) {
}
