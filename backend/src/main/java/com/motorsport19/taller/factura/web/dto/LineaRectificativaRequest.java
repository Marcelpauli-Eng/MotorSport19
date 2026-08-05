package com.motorsport19.taller.factura.web.dto;

import com.motorsport19.taller.factura.domain.LineaAFacturar;
import com.motorsport19.taller.orden.domain.TipoLinea;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Linea de una factura rectificativa.
 *
 * @param cantidad puede ser NEGATIVA en una rectificativa por diferencias: es la
 *                 forma de restar lo que se facturo de mas
 */
public record LineaRectificativaRequest(

        @NotNull(message = "El tipo de linea es obligatorio")
        TipoLinea tipo,

        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 300, message = "La descripcion no puede superar los 300 caracteres")
        String descripcion,

        @Size(max = 50, message = "El SKU no puede superar los 50 caracteres")
        String piezaSku,

        @NotNull(message = "La cantidad es obligatoria")
        BigDecimal cantidad,

        @NotNull(message = "El precio unitario es obligatorio")
        BigDecimal precioUnitario,

        BigDecimal descuentoPct,

        @NotBlank(message = "El tipo de IVA es obligatorio")
        String tipoIva,

        @NotNull(message = "El porcentaje de IVA es obligatorio")
        BigDecimal porcentajeIva
) {

    public LineaAFacturar aDominio() {
        return new LineaAFacturar(tipo, descripcion, piezaSku, cantidad, precioUnitario,
                descuentoPct, tipoIva, porcentajeIva);
    }
}
