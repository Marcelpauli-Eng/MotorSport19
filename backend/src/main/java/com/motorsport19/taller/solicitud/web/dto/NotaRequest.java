package com.motorsport19.taller.solicitud.web.dto;

import jakarta.validation.constraints.Size;

/** Lo que apunta quien cierra la solicitud. Opcional. */
public record NotaRequest(
        @Size(max = 500, message = "La nota no puede superar los 500 caracteres")
        String nota) {
}
