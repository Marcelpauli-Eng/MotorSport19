package com.motorsport19.taller.orden.domain;

/**
 * Naturaleza de una linea de orden de trabajo o de factura.
 */
public enum TipoLinea {

    /** Horas de taller: cantidad = horas, precio unitario = tarifa/hora. */
    MANO_DE_OBRA("Mano de obra"),

    /** Recambio consumido: cantidad = unidades, precio unitario = precio de venta. */
    PIEZA("Pieza"),

    /**
     * Tasa que acompana a una pieza (la de reciclaje de un neumatico): cantidad =
     * unidades, precio unitario = lo que diga su regla. No sale del almacen.
     */
    TASA("Tasa");

    private final String descripcion;

    TipoLinea(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
