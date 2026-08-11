package com.motorsport19.taller.agenda.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Alta o modificacion de una cita.
 *
 * <p>{@code motoId} es opcional: media agenda se coge por telefono de gente que
 * llama por primera vez. Cuando no hay moto, el dominio exige nombre y telefono
 * de quien la trae; aqui no se valida esa alternativa porque expresarla con
 * anotaciones queda peor que el mensaje que da la regla de negocio.
 */
public record GuardarCitaRequest(
        @NotNull(message = "La cita necesita fecha y hora")
        Instant fechaHora,

        @NotNull(message = "La duracion estimada es obligatoria")
        @Positive(message = "La duracion estimada tiene que ser mayor que cero")
        @DecimalMax(value = "24", message = "Una cita no puede durar mas de 24 horas")
        BigDecimal duracionEstimada,

        Long motoId,

        /** Cliente ya dado de alta cuando su moto todavia no tiene ficha. */
        Long clienteId,

        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String contactoNombre,

        @Size(max = 30, message = "El telefono no puede superar los 30 caracteres")
        String contactoTelefono,

        @Size(max = 150, message = "La descripcion de la moto no puede superar los 150 caracteres")
        String descripcionMoto,

        @NotBlank(message = "Hay que apuntar a que viene la moto")
        String motivo,

        Long tecnicoId,

        String observaciones) {
}
