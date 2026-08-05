package com.motorsport19.taller.factura.domain;

/**
 * Naturaleza de la factura.
 */
public enum TipoFactura {

    /** Factura normal emitida a partir de una orden de trabajo. */
    ORDINARIA("Factura ordinaria"),

    /**
     * Factura rectificativa. Es el UNICO mecanismo para corregir una factura ya
     * emitida: la original nunca se edita ni se borra.
     */
    RECTIFICATIVA("Factura rectificativa");

    private final String descripcion;

    TipoFactura(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
