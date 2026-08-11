package com.motorsport19.taller.agenda.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * La moto ha llegado: se abre su orden de trabajo.
 *
 * @param motoId            solo hace falta si la cita se cogio sin moto en el
 *                          sistema; entonces se da de alta y se indica aqui
 * @param problemaReportado si se deja vacio se usa el motivo de la cita, que es
 *                          lo normal: ya se apunto bien al cogerla
 */
public record AtenderCitaRequest(
        Long motoId,

        @NotNull(message = "El kilometraje de entrada es obligatorio")
        @PositiveOrZero(message = "El kilometraje no puede ser negativo")
        Integer kmEntrada,

        String problemaReportado) {
}
