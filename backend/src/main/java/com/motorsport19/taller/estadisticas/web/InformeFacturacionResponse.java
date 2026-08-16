package com.motorsport19.taller.estadisticas.web;

import com.motorsport19.taller.estadisticas.service.FilaReparto;
import com.motorsport19.taller.estadisticas.service.ResumenMes;
import com.motorsport19.taller.estadisticas.service.TotalesEjercicio;
import com.motorsport19.taller.estadisticas.service.TrabajoSinFacturar;

import java.util.List;

/**
 * Todo el informe en una respuesta.
 *
 * <p>Va junto a proposito: la pantalla necesita las cinco piezas a la vez para
 * pintarse, y cinco peticiones sueltas harian que las tarjetas y las graficas
 * aparecieran de una en una.
 */
public record InformeFacturacionResponse(
        int ejercicio,
        List<Integer> ejerciciosDisponibles,
        TotalesEjercicio totales,
        List<ResumenMes> meses,
        List<FilaReparto> mejoresClientes,
        List<FilaReparto> piezasMasUsadas,
        /** Ordenes terminadas y sin factura. No depende del ejercicio elegido. */
        TrabajoSinFacturar trabajoSinFacturar
) {
}
