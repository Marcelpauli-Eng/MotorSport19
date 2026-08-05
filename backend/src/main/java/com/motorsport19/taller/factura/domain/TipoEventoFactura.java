package com.motorsport19.taller.factura.domain;

/**
 * Tipos de suceso que se anotan en el registro de eventos de facturacion.
 */
public enum TipoEventoFactura {

    EMISION("Emision de factura"),
    RECTIFICACION("Emision de factura rectificativa"),
    GENERACION_PDF("Generacion del PDF"),
    EXPORTACION("Exportacion del registro de facturacion"),
    VERIFICACION_CADENA("Verificacion de la cadena de huellas"),
    CONSULTA("Consulta de factura"),
    INCIDENCIA("Incidencia detectada");

    private final String descripcion;

    TipoEventoFactura(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
