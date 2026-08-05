package com.motorsport19.taller.orden.web.dto;

import com.motorsport19.taller.orden.domain.CambioEstadoOT;
import com.motorsport19.taller.orden.domain.EstadoOT;

import java.time.Instant;

/** Anotacion del historial: quien movio la orden, cuando y por que. */
public record CambioEstadoResponse(
        Long id,
        EstadoOT estadoAnterior,
        EstadoOT estadoNuevo,
        String estadoNuevoDescripcion,
        Instant fecha,
        Long usuarioId,
        String usuarioNombre,
        String motivo
) {

    public static CambioEstadoResponse de(CambioEstadoOT cambio) {
        return new CambioEstadoResponse(
                cambio.getId(),
                cambio.getEstadoAnterior(),
                cambio.getEstadoNuevo(),
                cambio.getEstadoNuevo().getDescripcion(),
                cambio.getFecha(),
                cambio.getUsuario() == null ? null : cambio.getUsuario().getId(),
                cambio.getUsuario() == null ? null : cambio.getUsuario().getNombreCompleto(),
                cambio.getMotivo());
    }
}
