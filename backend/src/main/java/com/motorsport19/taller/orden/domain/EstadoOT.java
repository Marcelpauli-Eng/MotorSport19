package com.motorsport19.taller.orden.domain;

/**
 * Estados por los que pasa una orden de trabajo.
 *
 * <p>Flujo previsto:
 * <pre>
 *   RECIBIDA -> EN_DIAGNOSTICO -> PRESUPUESTADA -> APROBADA -> EN_REPARACION
 *            -> LISTA -> ENTREGADA
 *
 *   PRESUPUESTADA -> RECHAZADA            (el cliente no acepta el presupuesto)
 *   EN_REPARACION <-> ESPERANDO_PIEZAS    (bidireccional)
 * </pre>
 *
 * <p>La maquina de estados (que transiciones son validas y como se aplican) se
 * implementa en la fase 3. Aqui solo se declara el vocabulario del dominio.
 */
public enum EstadoOT {

    RECIBIDA("Recibida"),
    EN_DIAGNOSTICO("En diagnostico"),
    PRESUPUESTADA("Presupuestada"),
    APROBADA("Aprobada por el cliente"),
    EN_REPARACION("En reparacion"),
    ESPERANDO_PIEZAS("Esperando piezas"),
    LISTA("Lista para entregar"),
    ENTREGADA("Entregada"),
    RECHAZADA("Presupuesto rechazado");

    private final String descripcion;

    EstadoOT(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
