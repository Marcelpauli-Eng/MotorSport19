package com.motorsport19.taller.factura.service;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de verificar el registro de facturacion de extremo a extremo.
 *
 * @param facturasVerificadas cuantas facturas se han recorrido
 * @param anomalias           lo que no cuadra; vacio significa cadena integra
 * @param primeraHuella       huella de la primera factura de la cadena
 * @param ultimaHuella        huella de la ultima; es el "sello" del registro completo
 */
public record InformeVerificacion(
        Instant momento,
        long facturasVerificadas,
        List<AnomaliaCadena> anomalias,
        String primeraHuella,
        String ultimaHuella
) {

    /**
     * Se anota explicitamente para Jackson: al ser un record, solo serializa sus
     * componentes, y este dato derivado es justo el que mira quien consulta el
     * informe.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("integra")
    public boolean integra() {
        return anomalias.isEmpty();
    }

    @com.fasterxml.jackson.annotation.JsonProperty("resumen")
    public String resumen() {
        if (facturasVerificadas == 0) {
            return "No hay facturas emitidas: no hay nada que verificar.";
        }
        return integra()
                ? "Cadena integra: %d facturas verificadas sin anomalias.".formatted(facturasVerificadas)
                : "ATENCION: %d anomalia(s) en %d facturas verificadas."
                        .formatted(anomalias.size(), facturasVerificadas);
    }
}
