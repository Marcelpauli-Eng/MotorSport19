package com.motorsport19.taller.usuario.domain;

/**
 * Roles de acceso al sistema.
 */
public enum Rol {

    /** Acceso total, incluida la configuracion fiscal y los precios. */
    ADMIN("Administrador"),

    /** Clientes, motos, ordenes de trabajo, facturacion y consulta de inventario. */
    MOSTRADOR("Mostrador"),

    /** Solo sus ordenes asignadas: diagnostico, estado y consumo de piezas. */
    TECNICO("Tecnico");

    private final String descripcion;

    Rol(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
