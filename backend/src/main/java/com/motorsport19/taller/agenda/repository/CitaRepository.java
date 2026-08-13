package com.motorsport19.taller.agenda.repository;

import com.motorsport19.taller.agenda.domain.Cita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la agenda.
 *
 * <p>La consulta natural aqui no es «dame una pagina de citas» sino «que hay
 * entre el lunes y el domingo», asi que todo va por rango de fechas y sin
 * paginar: una semana de taller son unas decenas de citas, no miles.
 *
 * <p>La moto, su cliente y el tecnico se traen con JOIN FETCH porque el
 * calendario los muestra y {@code open-in-view} esta desactivado.
 */
public interface CitaRepository extends JpaRepository<Cita, Long> {

    @Query("""
            SELECT c FROM Cita c
              LEFT JOIN FETCH c.moto m
              LEFT JOIN FETCH m.cliente
              LEFT JOIN FETCH c.cliente
              LEFT JOIN FETCH c.tecnico
              LEFT JOIN FETCH c.ordenTrabajo
             WHERE c.fechaHora >= :desde
               AND c.fechaHora <  :hasta
             ORDER BY c.fechaHora ASC, c.id ASC
            """)
    List<Cita> buscarEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Igual que la anterior pero solo lo que sigue vivo: es lo que ocupa hueco. */
    @Query("""
            SELECT c FROM Cita c
              LEFT JOIN FETCH c.moto m
              LEFT JOIN FETCH m.cliente
              LEFT JOIN FETCH c.cliente
              LEFT JOIN FETCH c.tecnico
             WHERE c.fechaHora >= :desde
               AND c.fechaHora <  :hasta
               AND c.estado IN (com.motorsport19.taller.agenda.domain.EstadoCita.PENDIENTE,
                                com.motorsport19.taller.agenda.domain.EstadoCita.CONFIRMADA)
             ORDER BY c.fechaHora ASC, c.id ASC
            """)
    List<Cita> buscarVivasEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query("""
            SELECT c FROM Cita c
              LEFT JOIN FETCH c.moto m
              LEFT JOIN FETCH m.cliente
              LEFT JOIN FETCH c.cliente
              LEFT JOIN FETCH c.tecnico
              LEFT JOIN FETCH c.ordenTrabajo
             WHERE c.id = :id
            """)
    Optional<Cita> buscarConDetalle(@Param("id") Long id);

    /** Historial de citas de una moto, de la mas reciente a la mas antigua. */
    @Query("""
            SELECT c FROM Cita c
              LEFT JOIN FETCH c.moto m
              LEFT JOIN FETCH m.cliente
              LEFT JOIN FETCH c.cliente
              LEFT JOIN FETCH c.tecnico
              LEFT JOIN FETCH c.ordenTrabajo
             WHERE c.moto.id = :motoId
             ORDER BY c.fechaHora DESC
            """)
    List<Cita> buscarPorMoto(@Param("motoId") Long motoId);

    /**
     * Citas de un rango que acabaron en ausencia.
     *
     * <p>Alimenta el seguimiento de plantones. Se traen las entidades y no un
     * conteo agrupado porque quien falta no siempre es un cliente con ficha: la
     * mitad de la agenda se coge por telefono a gente que llama por primera vez,
     * y agrupar eso en SQL obligaria a mezclar tres columnas distintas.
     */
    @Query("""
            SELECT c FROM Cita c
              LEFT JOIN FETCH c.moto m
              LEFT JOIN FETCH m.cliente
              LEFT JOIN FETCH c.cliente
              LEFT JOIN FETCH c.tecnico
             WHERE c.fechaHora >= :desde
               AND c.fechaHora <  :hasta
               AND c.estado = com.motorsport19.taller.agenda.domain.EstadoCita.NO_PRESENTADO
             ORDER BY c.fechaHora DESC
            """)
    List<Cita> buscarAusenciasEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /**
     * Citas vivas de una moto a partir de un instante.
     *
     * <p>Se usa para avisar de que esa moto ya tiene hueco apartado: dar dos
     * citas a la misma moto casi siempre es un despiste del mostrador.
     */
    @Query("""
            SELECT c FROM Cita c
             WHERE c.moto.id = :motoId
               AND c.fechaHora >= :desde
               AND c.estado IN (com.motorsport19.taller.agenda.domain.EstadoCita.PENDIENTE,
                                com.motorsport19.taller.agenda.domain.EstadoCita.CONFIRMADA)
            """)
    List<Cita> buscarVivasDeMotoDesde(@Param("motoId") Long motoId, @Param("desde") Instant desde);
}
