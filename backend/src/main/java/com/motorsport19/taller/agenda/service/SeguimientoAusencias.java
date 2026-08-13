package com.motorsport19.taller.agenda.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Los plantones de un periodo.
 *
 * <p>Un hueco que nadie ocupa es dinero perdido: el tecnico estaba reservado y
 * la moto no aparecio. Lo que interesa no es tanto el numero como quien
 * repite, que es a quien hay que pedirle confirmacion la proxima vez.
 *
 * @param horasPerdidas suma de las duraciones que se habian apartado
 * @param porcentaje    ausencias sobre el total de citas que llegaron a su dia,
 *                      es decir, sin contar las que se cancelaron a tiempo:
 *                      cancelar avisando no es plantar
 */
public record SeguimientoAusencias(
        LocalDate desde,
        LocalDate hasta,
        int ausencias,
        int citasCerradas,
        BigDecimal porcentaje,
        BigDecimal horasPerdidas,
        List<Reincidente> reincidentes,
        List<Ausencia> ultimas
) {

    /** Quien ha faltado mas de una vez en el periodo. */
    public record Reincidente(String nombre, String telefono, int faltas) {
    }

    public record Ausencia(
            Long citaId,
            LocalDate dia,
            String cliente,
            String telefono,
            String moto,
            String motivo,
            BigDecimal horas,
            String tecnico
    ) {
    }
}
