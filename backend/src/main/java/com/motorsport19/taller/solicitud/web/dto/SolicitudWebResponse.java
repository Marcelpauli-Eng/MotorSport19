package com.motorsport19.taller.solicitud.web.dto;

import com.motorsport19.taller.solicitud.domain.CanalPresupuesto;
import com.motorsport19.taller.solicitud.domain.EstadoSolicitud;
import com.motorsport19.taller.solicitud.domain.SolicitudWeb;
import com.motorsport19.taller.solicitud.domain.TipoSolicitud;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SolicitudWebResponse(
        Long id,
        String referencia,
        TipoSolicitud tipo,
        String tipoDescripcion,
        EstadoSolicitud estado,
        String estadoDescripcion,
        Instant recibidaEn,
        String idioma,

        String nombre,
        String telefono,
        String email,
        String marca,
        String modelo,
        String matricula,
        Integer anio,
        /** Como se apuntaria en una cita sin ficha: marca, modelo y matricula. */
        String descripcionMoto,
        String necesita,
        LocalDate fechaPreferida,
        /** Cuantas fotos pedir a /fotos/{1..n}. */
        int fotos,

        /** Nulo para quien no puede ver importes. */
        BigDecimal presupuestoImporte,
        String presupuestoDetalle,
        CanalPresupuesto presupuestoCanal,
        Instant presupuestadaEn,
        String presupuestadaPor,

        Long citaId,
        Instant citaFechaHora,
        String nota,
        Instant atendidaEn,
        String atendidaPor,
        /** La orden que se abrio al aceptar el presupuesto. */
        Long ordenTrabajoId,
        String ordenTrabajoCodigo
) {

    public static SolicitudWebResponse de(SolicitudWeb s) {
        return new SolicitudWebResponse(
                s.getId(), s.getReferencia(), s.getTipo(), s.getTipo().getDescripcion(),
                s.getEstado(), s.getEstado().getDescripcion(), s.getRecibidaEn(), s.getIdioma(),
                s.getNombre(), s.getTelefono(), s.getEmail(), s.getMarca(), s.getModelo(),
                s.getMatricula(), s.getAnio(), s.descripcionMoto(), s.getNecesita(), s.getFechaPreferida(),
                s.getNumFotos(),
                s.getPresupuestoImporte(), s.getPresupuestoDetalle(), s.getPresupuestoCanal(),
                s.getPresupuestadaEn(),
                s.getPresupuestadaPor() == null ? null : s.getPresupuestadaPor().getNombreCompleto(),
                s.getCita() == null ? null : s.getCita().getId(),
                s.getCita() == null ? null : s.getCita().getFechaHora(),
                s.getNota(), s.getAtendidaEn(),
                s.getAtendidaPor() == null ? null : s.getAtendidaPor().getNombreCompleto(),
                s.getOrdenTrabajo() == null ? null : s.getOrdenTrabajo().getId(),
                s.getOrdenTrabajo() == null ? null : s.getOrdenTrabajo().codigoVisible());
    }

    /**
     * La misma ficha sin el importe, para quien no puede ver dinero. El detalle
     * se queda: dice que se ofrecio, no cuanto cuesta.
     */
    public SolicitudWebResponse sinImportes() {
        return new SolicitudWebResponse(id, referencia, tipo, tipoDescripcion, estado, estadoDescripcion,
                recibidaEn, idioma, nombre, telefono, email, marca, modelo, matricula, anio, descripcionMoto,
                necesita, fechaPreferida, fotos, null, presupuestoDetalle, presupuestoCanal,
                presupuestadaEn, presupuestadaPor, citaId, citaFechaHora, nota, atendidaEn, atendidaPor,
                ordenTrabajoId, ordenTrabajoCodigo);
    }
}
