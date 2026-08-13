package com.motorsport19.taller.estadisticas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Una de las dos columnas del informe por regimen de IVA: sus meses y su
 * acumulado del periodo.
 *
 * <p>{@code pesoPorcentaje} es lo que representa esta columna sobre el total
 * facturado en el periodo. Es el numero que contesta a la pregunta de verdad:
 * cuanto del taller va por cada via.
 */
public record ColumnaIva(
        boolean conIva,
        String titulo,
        List<ResumenMesIva> meses,
        BigDecimal baseFacturada,
        BigDecimal ivaRepercutido,
        BigDecimal totalFacturado,
        int numeroFacturas,
        BigDecimal ingresoManoDeObra,
        BigDecimal ingresoPiezas,
        BigDecimal gastoMaterial,
        BigDecimal margenBruto,
        BigDecimal margenPorcentaje,
        BigDecimal ticketMedio,
        BigDecimal pesoPorcentaje
) {

    static ColumnaIva de(boolean conIva, List<ResumenMesIva> meses, BigDecimal totalGeneral) {
        BigDecimal base = suma(meses, ResumenMesIva::baseFacturada);
        BigDecimal iva = suma(meses, ResumenMesIva::ivaRepercutido);
        BigDecimal total = suma(meses, ResumenMesIva::totalFacturado);
        BigDecimal gasto = suma(meses, ResumenMesIva::gastoMaterial);
        BigDecimal manoDeObra = suma(meses, ResumenMesIva::ingresoManoDeObra);
        BigDecimal piezas = suma(meses, ResumenMesIva::ingresoPiezas);
        int facturas = meses.stream().mapToInt(ResumenMesIva::numeroFacturas).sum();
        BigDecimal margen = base.subtract(gasto);

        return new ColumnaIva(
                conIva,
                conIva ? "Con IVA" : "Sin IVA (0 %)",
                meses,
                base, iva, total, facturas,
                manoDeObra, piezas,
                gasto,
                margen,
                ResumenMesIva.porcentaje(margen, base),
                facturas == 0 ? BigDecimal.ZERO
                        : total.divide(BigDecimal.valueOf(facturas), 2, RoundingMode.HALF_UP),
                ResumenMesIva.porcentaje(total, totalGeneral));
    }

    private static BigDecimal suma(List<ResumenMesIva> meses,
                                   java.util.function.Function<ResumenMesIva, BigDecimal> campo) {
        return meses.stream()
                .map(campo)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
