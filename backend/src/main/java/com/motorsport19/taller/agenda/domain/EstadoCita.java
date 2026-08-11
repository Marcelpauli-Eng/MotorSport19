package com.motorsport19.taller.agenda.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de una cita, con sus transiciones validas.
 *
 * <pre>
 *   PENDIENTE ──→ CONFIRMADA ──→ ATENDIDA
 *       │              │
 *       └──────────────┴──→ CANCELADA / NO_PRESENTADO
 * </pre>
 *
 * <p>ATENDIDA es el final feliz: la moto entro y la cita se convirtio en orden
 * de trabajo. Se distingue de NO_PRESENTADO a proposito, porque el hueco perdido
 * por alguien que no aparece es justo lo que un taller quiere poder mirar.
 */
public enum EstadoCita {

    PENDIENTE("Pendiente de confirmar"),
    CONFIRMADA("Confirmada"),
    ATENDIDA("Atendida"),
    CANCELADA("Cancelada"),
    NO_PRESENTADO("No se presento");

    private final String descripcion;

    /** Se asigna abajo: un EnumSet del propio enum no se puede crear en el constructor. */
    private Set<EstadoCita> siguientes;

    EstadoCita(String descripcion) {
        this.descripcion = descripcion;
    }

    static {
        PENDIENTE.siguientes = EnumSet.of(CONFIRMADA, ATENDIDA, CANCELADA, NO_PRESENTADO);
        // Una cita confirmada tambien se puede atender directamente: el cliente
        // que llega sin avisar es lo normal, no la excepcion.
        CONFIRMADA.siguientes = EnumSet.of(ATENDIDA, CANCELADA, NO_PRESENTADO);
        ATENDIDA.siguientes = EnumSet.noneOf(EstadoCita.class);
        CANCELADA.siguientes = EnumSet.noneOf(EstadoCita.class);
        NO_PRESENTADO.siguientes = EnumSet.noneOf(EstadoCita.class);
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Set<EstadoCita> siguientesPosibles() {
        return Collections.unmodifiableSet(siguientes);
    }

    public boolean puedeTransitarA(EstadoCita destino) {
        return destino != null && siguientes.contains(destino);
    }

    /** Una cita cerrada ya no admite cambios ni ocupa hueco en la agenda. */
    public boolean esTerminal() {
        return siguientes.isEmpty();
    }

    /** Solo lo que sigue vivo cuenta para la carga del dia. */
    public boolean ocupaAgenda() {
        return this == PENDIENTE || this == CONFIRMADA;
    }
}
