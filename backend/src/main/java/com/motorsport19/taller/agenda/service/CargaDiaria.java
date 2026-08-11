package com.motorsport19.taller.agenda.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuanto trabajo hay comprometido un dia concreto.
 *
 * @param horasComprometidas suma de las duraciones estimadas de las citas vivas
 * @param capacidad          horas de taller disponibles ese dia
 * @param saturado           si lo comprometido pasa de la capacidad. No impide
 *                           nada: un taller siempre puede meter una urgencia
 *                           mas, pero conviene que se vea que la esta metiendo
 */
public record CargaDiaria(
        LocalDate dia,
        int citas,
        BigDecimal horasComprometidas,
        BigDecimal capacidad,
        boolean saturado
) {

    public static CargaDiaria de(LocalDate dia, int citas, BigDecimal horas, BigDecimal capacidad) {
        return new CargaDiaria(dia, citas, horas, capacidad, horas.compareTo(capacidad) > 0);
    }

    /** Porcentaje de ocupacion, para pintar la barra de carga. */
    public int porcentaje() {
        if (capacidad.signum() <= 0) {
            return 0;
        }
        return horasComprometidas
                .multiply(BigDecimal.valueOf(100))
                .divide(capacidad, 0, java.math.RoundingMode.HALF_UP)
                .intValue();
    }
}
