package com.motorsport19.taller.estadisticas.service;

import java.math.BigDecimal;

/**
 * Una fila cruda del informe mensual, tal y como sale de la base de datos.
 *
 * <p>Los importes derivados (IVA a liquidar, margen) no se guardan aqui: los
 * calcula {@link ResumenMes} a partir de estos, para que solo exista un sitio
 * donde se decide como se restan.
 */
public record FilaMes(
        int mes,
        BigDecimal baseFacturada,
        BigDecimal ivaRepercutido,
        BigDecimal totalFacturado,
        int numeroFacturas,
        BigDecimal ingresoManoDeObra,
        BigDecimal ingresoPiezas,
        BigDecimal comprasMaterial,
        BigDecimal ivaSoportado,
        BigDecimal costeMaterialVendido,
        int ordenesAbiertas
) {
}
