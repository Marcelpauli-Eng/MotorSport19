package com.motorsport19.taller.orden.service;

import com.motorsport19.taller.orden.domain.EstadoOT;

import java.util.List;

/**
 * Resultado de intentar consumir el material de una orden de trabajo.
 *
 * @param estadoResultante {@link EstadoOT#EN_REPARACION} si se pudo servir todo,
 *                         {@link EstadoOT#ESPERANDO_PIEZAS} si falto algo
 * @param consumidas       cuantas lineas de pieza se sirvieron por completo
 * @param faltantes        detalle de lo que no se pudo servir; vacio si fue bien
 */
public record ResultadoConsumo(
        EstadoOT estadoResultante,
        int consumidas,
        List<PiezaFaltante> faltantes
) {

    public boolean completo() {
        return faltantes.isEmpty();
    }

    /** Texto para el historial de la OT y para el aviso al mostrador. */
    public String descripcionDeFaltantes() {
        if (faltantes.isEmpty()) {
            return null;
        }
        return "Sin existencias suficientes de: "
                + String.join("; ", faltantes.stream().map(PiezaFaltante::resumen).toList());
    }
}
