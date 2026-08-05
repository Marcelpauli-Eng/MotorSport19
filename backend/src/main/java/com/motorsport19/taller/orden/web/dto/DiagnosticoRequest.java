package com.motorsport19.taller.orden.web.dto;

import jakarta.validation.constraints.NotBlank;

public record DiagnosticoRequest(

        @NotBlank(message = "El diagnostico no puede quedar vacio")
        String diagnostico
) {
}
