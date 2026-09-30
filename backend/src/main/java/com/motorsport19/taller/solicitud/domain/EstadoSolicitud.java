package com.motorsport19.taller.solicitud.domain;

/**
 * Estados de una solicitud de la web.
 *
 * <pre>
 *   PENDIENTE ──→ ATENDIDA
 *       └──────→ DESCARTADA
 * </pre>
 *
 * <p>Solo se sale de PENDIENTE, y una sola vez: la solicitud es el aviso de que
 * alguien espera respuesta, y lo que pasa despues (la cita, la orden) ya tiene
 * su propio historial.
 */
public enum EstadoSolicitud {
    PENDIENTE("Pendiente"),
    ATENDIDA("Atendida"),
    DESCARTADA("Descartada");

    private final String descripcion;

    EstadoSolicitud(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
