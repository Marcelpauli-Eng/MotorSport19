package com.motorsport19.taller.solicitud.web.dto;

import com.motorsport19.taller.solicitud.domain.EstadoSolicitud;
import com.motorsport19.taller.solicitud.domain.SolicitudWeb;
import com.motorsport19.taller.solicitud.domain.TipoSolicitud;

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
        /** Como se apuntaria en una cita sin ficha: marca, modelo y matricula. */
        String descripcionMoto,
        String necesita,
        LocalDate fechaPreferida,
        /** Cuantas fotos pedir a /fotos/{1..n}. */
        int fotos,

        Long citaId,
        Instant citaFechaHora,
        String nota,
        Instant atendidaEn,
        String atendidaPor
) {

    public static SolicitudWebResponse de(SolicitudWeb s) {
        return new SolicitudWebResponse(
                s.getId(), s.getReferencia(), s.getTipo(), s.getTipo().getDescripcion(),
                s.getEstado(), s.getEstado().getDescripcion(), s.getRecibidaEn(), s.getIdioma(),
                s.getNombre(), s.getTelefono(), s.getEmail(), s.getMarca(), s.getModelo(),
                s.getMatricula(), s.descripcionMoto(), s.getNecesita(), s.getFechaPreferida(),
                s.getNumFotos(),
                s.getCita() == null ? null : s.getCita().getId(),
                s.getCita() == null ? null : s.getCita().getFechaHora(),
                s.getNota(), s.getAtendidaEn(),
                s.getAtendidaPor() == null ? null : s.getAtendidaPor().getNombreCompleto());
    }
}
