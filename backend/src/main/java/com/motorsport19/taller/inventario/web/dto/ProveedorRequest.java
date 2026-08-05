package com.motorsport19.taller.inventario.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta y modificacion de proveedor: los campos son los mismos en ambos casos. */
public record ProveedorRequest(

        @NotBlank(message = "El nombre del proveedor es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombre,

        @Size(max = 20, message = "El NIF no puede superar los 20 caracteres")
        String nif,

        @Size(max = 200, message = "La direccion no puede superar los 200 caracteres")
        String direccion,

        @Size(max = 10, message = "El codigo postal no puede superar los 10 caracteres")
        String codigoPostal,

        @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
        String ciudad,

        @Size(max = 100, message = "La provincia no puede superar los 100 caracteres")
        String provincia,

        @Size(max = 30, message = "El telefono no puede superar los 30 caracteres")
        String telefono,

        @Email(message = "El email no tiene un formato valido")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres")
        String email,

        String observaciones
) {
}
