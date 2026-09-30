package com.motorsport19.taller.solicitud.domain;

/** Lo que pide el cliente desde la web. */
public enum TipoSolicitud {
    CITA("Cita"),
    PRESUPUESTO("Presupuesto");

    private final String descripcion;

    TipoSolicitud(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
