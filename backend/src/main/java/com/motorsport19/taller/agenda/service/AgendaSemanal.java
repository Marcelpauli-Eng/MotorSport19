package com.motorsport19.taller.agenda.service;

import com.motorsport19.taller.agenda.domain.Cita;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * La semana del taller repartida por tecnico.
 *
 * <p>Contesta a la pregunta con la que se coge el telefono: «¿cuando puedo
 * meter esta moto y con quien?». Por eso la unidad no es el dia sino el cruce
 * de dia y tecnico: un martes puede estar lleno para uno y vacio para otro, y
 * un total del dia esconde justo eso.
 *
 * <p>Los tecnicos sin ninguna cita salen igualmente. Son los que tienen la
 * semana libre, o sea, exactamente a los que hay que mandarles trabajo.
 */
public record AgendaSemanal(
        LocalDate desde,
        LocalDate hasta,
        /** Horas que se considera que rinde un tecnico al dia. */
        BigDecimal capacidadDiaria,
        List<LocalDate> dias,
        List<ColumnaTecnico> tecnicos,
        BigDecimal horasComprometidas,
        BigDecimal horasLibres
) {

    /**
     * Una fila de la parrilla: un tecnico y su semana.
     *
     * @param tecnicoId nulo en la fila de las citas que aun no tienen tecnico
     */
    public record ColumnaTecnico(
            Long tecnicoId,
            String nombre,
            List<DiaTecnico> dias,
            BigDecimal horasComprometidas,
            BigDecimal horasLibres,
            int citas
    ) {
    }

    /**
     * Un dia de un tecnico.
     *
     * @param horasLibres lo que queda de su jornada; nunca baja de cero, aunque
     *                    este sobrecargado. Un hueco negativo no es un hueco
     * @param saturado    tiene comprometido mas de lo que da el dia
     */
    public record DiaTecnico(
            LocalDate dia,
            List<CitaBreve> citas,
            BigDecimal horasComprometidas,
            BigDecimal horasLibres,
            boolean saturado
    ) {
    }

    /** Lo justo para pintar la pastilla de la parrilla sin abrir la cita. */
    public record CitaBreve(
            Long id,
            java.time.Instant fechaHora,
            BigDecimal duracionEstimada,
            String estado,
            String cliente,
            String moto,
            String motivo
    ) {
        public static CitaBreve de(Cita c) {
            return new CitaBreve(
                    c.getId(), c.getFechaHora(), c.getDuracionEstimada(), c.getEstado().name(),
                    c.nombreDeContacto(), c.moto(), c.getMotivo());
        }
    }
}
