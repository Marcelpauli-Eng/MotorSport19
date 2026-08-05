package com.motorsport19.taller.cliente.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarContactoRequest(

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

        String observaciones
) {
}
