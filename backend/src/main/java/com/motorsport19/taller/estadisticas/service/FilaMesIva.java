package com.motorsport19.taller.estadisticas.service;

import java.math.BigDecimal;

/**
 * Una fila cruda del informe mensual partido por regimen de IVA.
 *
 * <p>Lleva el año ademas del mes porque el periodo que se pide no tiene por que
 * caber en un ejercicio: un trimestre a caballo entre dos años saldria con dos
 * eneros indistinguibles.
 *
 * <p>{@code conIva} distingue las facturas con cuota de las emitidas al 0 %.
 */
public record FilaMesIva(
        int anio,
        int mes,
        boolean conIva,
        BigDecimal baseFacturada,
        BigDecimal ivaRepercutido,
        BigDecimal totalFacturado,
        int numeroFacturas,
        BigDecimal ingresoManoDeObra,
        BigDecimal ingresoPiezas,
        BigDecimal costeMaterialVendido
) {
}
