package com.motorsport19.taller.solicitud.domain;

/** Por donde se le mando el presupuesto al cliente. */
public enum CanalPresupuesto {
    WHATSAPP("WhatsApp"),
    EMAIL("email");

    private final String descripcion;

    CanalPresupuesto(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
