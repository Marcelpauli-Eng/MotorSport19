package com.motorsport19.taller.estadisticas.service;

import java.math.BigDecimal;

/**
 * Totales de un ejercicio completo.
 *
 * <p>{@code variacionBase} es la variacion porcentual de la base facturada
 * frente al mismo tramo del año anterior, o nulo si no hay año anterior con el
 * que comparar. {@code mesesComputados} dice cuantos meses entran en el
 * acumulado, para que la interfaz pueda decir «hasta agosto» y no dar a entender
 * que es el año entero.
 */
public record TotalesEjercicio(
        int ejercicio,
        BigDecimal baseFacturada,
        BigDecimal ivaRepercutido,
        BigDecimal totalFacturado,
        int numeroFacturas,
        BigDecimal ingresoManoDeObra,
        BigDecimal ingresoPiezas,
        BigDecimal comprasMaterial,
        BigDecimal ivaSoportado,
        BigDecimal ivaALiquidar,
        BigDecimal costeMaterialVendido,
        BigDecimal margenBruto,
        BigDecimal margenPorcentaje,
        BigDecimal ticketMedio,
        BigDecimal variacionBase,
        int mesesComputados
) {
}
