package com.motorsport19.taller.factura.domain;

/**
 * Modalidad de rectificacion de una factura.
 */
public enum TipoRectificativa {

    /** La rectificativa sustituye integramente a la original. */
    POR_SUSTITUCION("Por sustitucion"),

    /** La rectificativa recoge solo la diferencia respecto a la original. */
    POR_DIFERENCIAS("Por diferencias");

    private final String descripcion;

    TipoRectificativa(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
