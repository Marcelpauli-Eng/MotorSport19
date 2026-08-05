package com.motorsport19.taller.moto.web.dto;

import jakarta.validation.constraints.NotNull;

public record CambioPropietarioRequest(

        @NotNull(message = "Hay que indicar el nuevo propietario")
        Long nuevoClienteId
) {
}
