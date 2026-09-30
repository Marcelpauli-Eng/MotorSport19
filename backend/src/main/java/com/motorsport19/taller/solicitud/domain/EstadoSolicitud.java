package com.motorsport19.taller.solicitud.domain;

/**
 * Estados de una solicitud de la web.
 *
 * <pre>
 *   PENDIENTE ──→ PRESUPUESTADA ──→ ATENDIDA
 *       │               │     ↺
 *       └───────────────┴──────→ DESCARTADA
 * </pre>
 *
 * <p>PRESUPUESTADA sigue abierta: se le ha mandado un presupuesto y se espera
 * respuesta. Desde ahi se le da cita si lo acepta, se vuelve a mandar corregido,
 * o se cierra. ATENDIDA y DESCARTADA son finales: lo que pase despues (la cita,
 * la orden) ya tiene su propio historial.
 */
public enum EstadoSolicitud {
    PENDIENTE("Pendiente"),
    PRESUPUESTADA("Presupuestada"),
    ATENDIDA("Atendida"),
    DESCARTADA("Descartada");

    private final String descripcion;

    EstadoSolicitud(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Todavia espera algo del taller o del cliente. */
    public boolean abierta() {
        return this == PENDIENTE || this == PRESUPUESTADA;
    }
}
