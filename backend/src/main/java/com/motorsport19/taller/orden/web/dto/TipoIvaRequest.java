package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Tipo de IVA que se impone a toda la orden.
 *
 * <p>Es el codigo del catalogo —GENERAL, REDUCIDO, SUPERREDUCIDO, EXENTO—, no
 * el porcentaje. El porcentaje lo pone el servidor leyendolo del catalogo: si
 * lo eligiera quien llama, cualquiera podria facturar al 3 %.
 */
public record TipoIvaRequest(
        @NotBlank(message = "Hay que indicar el tipo de IVA")
        String tipoIva
) {
}
