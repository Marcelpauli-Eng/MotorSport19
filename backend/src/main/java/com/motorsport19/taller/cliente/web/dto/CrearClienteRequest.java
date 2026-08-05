package com.motorsport19.taller.cliente.web.dto;

import com.motorsport19.taller.cliente.domain.TipoDocumento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de cliente. Solo el nombre es obligatorio: la ficha puede completarse
 * despues, aunque no se podra facturar hasta tener los datos fiscales.
 */
public record CrearClienteRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Size(max = 150, message = "Los apellidos no pueden superar los 150 caracteres")
        String apellidos,

        @Size(max = 30, message = "El telefono no puede superar los 30 caracteres")
        String telefono,

        @Email(message = "El email no tiene un formato valido")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres")
        String email,

        TipoDocumento tipoDocumento,

        @Size(max = 20, message = "El documento no puede superar los 20 caracteres")
        String documento,

        @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
        String direccion,

        @Size(max = 10, message = "El codigo postal no puede superar los 10 caracteres")
        String codigoPostal,

        @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
        String ciudad,

        @Size(max = 100, message = "La provincia no puede superar los 100 caracteres")
        String provincia,

        @Size(max = 60, message = "El pais no puede superar los 60 caracteres")
        String pais
) {
}
