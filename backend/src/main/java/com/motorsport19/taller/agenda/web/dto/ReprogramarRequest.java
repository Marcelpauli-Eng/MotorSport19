package com.motorsport19.taller.agenda.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Nueva fecha y hora de una cita. Es lo que mas cambia: el cliente llama para moverla. */
public record ReprogramarRequest(
        @NotNull(message = "La nueva fecha y hora son obligatorias")
        Instant fechaHora) {
}
