package com.motorsport19.taller.factura.domain;

/**
 * Tipos de suceso que se anotan en el registro de eventos de facturacion.
 */
public enum TipoEventoFactura {

    EMISION("Emision de factura"),
    RECTIFICACION("Emision de factura rectificativa"),
    GENERACION_PDF("Generación del PDF"),
    EXPORTACION("Exportación del registro de facturación"),
    VERIFICACION_CADENA("Verificación de la cadena de huellas"),
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
