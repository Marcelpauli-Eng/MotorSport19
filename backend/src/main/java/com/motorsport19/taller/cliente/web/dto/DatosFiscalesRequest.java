package com.motorsport19.taller.cliente.web.dto;

import com.motorsport19.taller.cliente.domain.TipoDocumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos fiscales completos. Todos los campos son obligatorios porque una factura
 * los necesita todos: una direccion a medias no sirve.
 */
public record DatosFiscalesRequest(

        TipoDocumento tipoDocumento,

        @NotBlank(message = "El documento fiscal es obligatorio")
        @Size(max = 20, message = "El documento no puede superar los 20 caracteres")
        String documento,

        @NotBlank(message = "La direccion fiscal es obligatoria")
        @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
        String direccion,

        @NotBlank(message = "El codigo postal es obligatorio")
        @Size(max = 10, message = "El codigo postal no puede superar los 10 caracteres")
        String codigoPostal,

        @NotBlank(message = "La ciudad es obligatoria")
        @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
        String ciudad,

        @NotBlank(message = "La provincia es obligatoria")
        @Size(max = 100, message = "La provincia no puede superar los 100 caracteres")
        String provincia,

        @Size(max = 60, message = "El pais no puede superar los 60 caracteres")
        String pais
) {
}
