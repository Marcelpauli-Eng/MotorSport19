package com.motorsport19.taller.estadisticas.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Trabajo terminado que todavia no se ha facturado.
 *
 * <p>Es el dinero que el taller ya se ha ganado y no ha cobrado: la moto salio
 * —o esta lista para salir— y no hay factura. En un taller pequeño se pierde
 * justo aqui, entre la prisa de entregar y el papeleo del dia siguiente.
 *
 * <p>No depende del ejercicio que se este mirando: son las ordenes que estan
 * asi <b>ahora</b>, vengan de cuando vengan. Una orden de hace tres meses sin
 * facturar es peor que una de ayer, no menos importante.
 */
public record TrabajoSinFacturar(
        int ordenes,
        BigDecimal importe,
        List<Fila> detalle
) {

    /**
     * @param importe suma de sus lineas, con IVA incluido: es lo que se le
     *                cobraria al cliente si se facturara tal cual esta
     */
    public record Fila(
            Long ordenId,
            String codigo,
            String estado,
            String cliente,
            String matricula,
            LocalDate salida,
            BigDecimal importe
    ) {
    }
}
