package com.motorsport19.taller.factura.web.dto;

import com.motorsport19.taller.factura.domain.TipoFactura;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de una serie de facturacion.
 *
 * @param codigo    prefijo visible en el numero de factura (A, R, F...)
 * @param ejercicio año al que pertenece la numeracion
 * @param tipo      ORDINARIA para las facturas normales, RECTIFICATIVA para los
 *                  abonos; una serie no admite los dos
 */
public record CrearSerieRequest(
        @NotBlank(message = "El codigo de la serie es obligatorio")
        @Size(max = 10, message = "El codigo no puede pasar de 10 caracteres")
        String codigo,

        @NotNull(message = "El ejercicio es obligatorio")
        @Min(value = 2000, message = "El ejercicio no es un año valido")
        @Max(value = 2200, message = "El ejercicio no es un año valido")
        Integer ejercicio,

        @Size(max = 150, message = "La descripcion no puede pasar de 150 caracteres")
        String descripcion,

        @NotNull(message = "Hay que decir si la serie es ordinaria o rectificativa")
        TipoFactura tipo,

        /**
         * Serie reservada a facturas simplificadas.
         *
         * <p>Van en su propia serie para que el libro quede ordenado y la
         * gestoria las distinga. Solo vale en series ordinarias.
         */
        boolean simplificada
) {
}
