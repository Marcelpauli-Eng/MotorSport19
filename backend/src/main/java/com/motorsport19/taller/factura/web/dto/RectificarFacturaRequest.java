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
 * @param tipoRectificativa POR_SUSTITUCION si las lineas sustituyen integramente
 *                          a las de la original; POR_DIFERENCIAS si recogen solo
 *                          el ajuste
 * @param lineas            lineas corregidas. Pueden ir vacias solo en una
 *                          rectificativa POR_DIFERENCIAS, y entonces se genera el
 *                          negativo exacto de la original (anulacion completa)
 */
public record RectificarFacturaRequest(

        @NotNull(message = "Hay que indicar la serie de rectificativas")
        Long serieId,

        @NotNull(message = "Hay que indicar si la rectificativa es por sustitucion o por diferencias")
        TipoRectificativa tipoRectificativa,

        @NotBlank(message = "Hay que explicar el motivo de la rectificacion")
        String motivo,

        @Valid
        List<LineaRectificativaRequest> lineas,

        LocalDate fechaEmision
) {
}
