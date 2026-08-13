package com.motorsport19.taller.orden.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Estados por los que pasa una orden de trabajo, con sus transiciones validas.
 *
 * <p>La maquina de estados vive aqui, en el dominio, y no como texto libre: cada
 * estado declara explicitamente a cuales puede saltar. Cualquier otro salto se
 * rechaza con {@link TransicionInvalidaException}.
 *
 * <pre>
 *   RECIBIDA ──→ EN_DIAGNOSTICO ──→ PRESUPUESTADA ──→ APROBADA ──→ EN_REPARACION ──→ LISTA ──→ ENTREGADA
 *      │                                 │                              ↕
 *      │                                 └──→ RECHAZADA         ESPERANDO_PIEZAS ──→ LISTA
 *      │                                                                ↑
 *      └──→ PREPARADA ────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Hay por tanto dos caminos hasta el taller, y son dos situaciones distintas
 * del mostrador, no dos formas de hacer lo mismo:
 *
 * <ul>
 *   <li>La moto entra con una averia por determinar: se diagnostica, se
 *       presupuesta y el cliente decide. Es el camino largo.</li>
 *   <li>El trabajo ya esta hablado y cerrado con el cliente: direccion compone
 *       la orden entera —conceptos, piezas y precios—, se la asigna a un
 *       tecnico y este solo tiene que ejecutarla. Es {@link #PREPARADA}.</li>
 * </ul>
 *
 * <p>ENTREGADA y RECHAZADA son terminales. Una OT ENTREGADA es ademas inmutable:
 * lo garantiza tambien un trigger de la base de datos, no solo este enum.
 */
public enum EstadoOT {

    RECIBIDA("Recibida"),

    /**
     * Compuesta por direccion y a la espera de que el tecnico la empiece.
     *
     * <p>No pasa por presupuesto ni por aprobacion porque el precio ya se cerro
     * fuera del programa. Lo que queda es trabajo de taller.
     */
    PREPARADA("Preparada, pendiente de empezar"),

    EN_DIAGNOSTICO("En diagnóstico"),
    PRESUPUESTADA("Presupuestada"),
    APROBADA("Aprobada por el cliente"),
    EN_REPARACION("En reparación"),
    ESPERANDO_PIEZAS("Esperando piezas"),
    LISTA("Lista para entregar"),
    ENTREGADA("Entregada"),
    RECHAZADA("Presupuesto rechazado");

    private final String descripcion;

    /**
     * Transiciones permitidas desde este estado.
     *
     * <p>Se asigna en el bloque estatico de abajo y NO aqui: un EnumSet del propio
     * enum no se puede crear mientras se construyen sus constantes, porque en ese
     * momento {@code values()} todavia no existe.
     */
    private Set<EstadoOT> siguientes;

    EstadoOT(String descripcion) {
        this.descripcion = descripcion;
    }

    static {
        // Dos salidas: el camino largo (diagnostico) o el corto, cuando el
        // trabajo ya viene decidido y solo hay que hacerlo.
        RECIBIDA.siguientes = EnumSet.of(EN_DIAGNOSTICO, PREPARADA);
        // De una preparada no se sale a presupuesto: el precio ya esta cerrado.
        // Si el cliente se echa atras, se rechaza igual que un presupuesto.
        PREPARADA.siguientes = EnumSet.of(EN_REPARACION, RECHAZADA);
        EN_DIAGNOSTICO.siguientes = EnumSet.of(PRESUPUESTADA);
        // El cliente decide: acepta el presupuesto o se lleva la moto sin reparar.
        PRESUPUESTADA.siguientes = EnumSet.of(APROBADA, RECHAZADA);
        APROBADA.siguientes = EnumSet.of(EN_REPARACION);
        // Si falta material, la reparacion se bloquea; cuando llega, se reanuda.
        EN_REPARACION.siguientes = EnumSet.of(ESPERANDO_PIEZAS, LISTA);
        ESPERANDO_PIEZAS.siguientes = EnumSet.of(EN_REPARACION, LISTA);
        LISTA.siguientes = EnumSet.of(ENTREGADA);
        // Terminales.
        ENTREGADA.siguientes = EnumSet.noneOf(EstadoOT.class);
        RECHAZADA.siguientes = EnumSet.noneOf(EstadoOT.class);
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Estados a los que se puede saltar desde este. */
    public Set<EstadoOT> siguientesPosibles() {
        return Collections.unmodifiableSet(siguientes);
    }

    public boolean puedeTransitarA(EstadoOT destino) {
        return destino != null && siguientes.contains(destino);
    }

    /** Un estado terminal cierra la OT: ya no admite mas transiciones. */
    public boolean esTerminal() {
        return siguientes.isEmpty();
    }

    /**
     * Indica si en este estado la OT sigue "viva" en el taller, es decir, si
     * aparece en el tablero de trabajo pendiente.
     */
    public boolean estaAbierta() {
        return !esTerminal();
    }

    /**
     * Indica si en este estado se pueden anadir, modificar o quitar lineas.
     *
     * <p>Mientras se diagnostica y se repara si: es normal descubrir averias
     * nuevas con la moto abierta. Una vez lista para entregar, el presupuesto
     * esta cerrado y solo queda facturarlo.
     *
     * <p>En PREPARADA tambien, porque es justo el estado en el que direccion
     * compone la orden antes de pasarsela al tecnico.
     */
    public boolean permiteEditarLineas() {
        return this == PREPARADA
                || this == EN_DIAGNOSTICO
                || this == PRESUPUESTADA
                || this == APROBADA
                || this == EN_REPARACION
                || this == ESPERANDO_PIEZAS;
    }

    /** Estados desde los que se puede emitir factura. */
    public boolean permiteFacturar() {
        return this == LISTA || this == ENTREGADA;
    }

    /** Comprueba la transicion y falla con un mensaje que dice que si se puede hacer. */
    public void exigirTransicionA(EstadoOT destino, String codigoOt) {
        if (!puedeTransitarA(destino)) {
            throw new TransicionInvalidaException(codigoOt, this, destino);
        }
    }
}
