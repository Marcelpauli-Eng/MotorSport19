package com.motorsport19.taller.inventario.domain;

/**
 * Naturaleza de un movimiento de stock.
 *
 * <p>El signo de la cantidad debe ser coherente con el tipo; la base de datos lo
 * exige mediante la restriccion {@code ck_movimiento_signo}.
 */
public enum TipoMovimiento {

    /** Compra a proveedor: suma existencias. */
    ENTRADA("Entrada por compra"),

    /** Consumo en una orden de trabajo: resta existencias. */
    SALIDA("Salida por consumo en OT"),

    /** Correccion tras inventario fisico: admite ambos signos y exige motivo. */
    AJUSTE("Ajuste de inventario"),

    /** Pieza que vuelve al almacen sin haberse usado: suma existencias. */
    DEVOLUCION("Devolución a almacén");

    private final String descripcion;

    TipoMovimiento(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
