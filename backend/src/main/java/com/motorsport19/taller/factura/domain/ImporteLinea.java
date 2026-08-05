package com.motorsport19.taller.factura.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Importes de una linea, calculados con <b>exactamente</b> el mismo redondeo que
 * las columnas generadas de PostgreSQL.
 *
 * <p>Hace falta calcularlos en Java porque la huella de la factura se computa
 * ANTES del INSERT, y en ese momento las columnas generadas todavia no existen.
 *
 * <p>Que los dos calculos coincidan no se deja a la buena fe: el trigger diferido
 * {@code tg_factura_validar_totales} compara al hacer commit los totales de la
 * cabecera con la suma de las lineas que ha calculado la base de datos. Si el
 * redondeo de aqui se separase un centimo del de PostgreSQL, la transaccion
 * fallaria en vez de guardar una factura descuadrada.
 *
 * <p>{@code ROUND()} sobre {@code numeric} en PostgreSQL redondea alejandose del
 * cero, igual que {@link RoundingMode#HALF_UP} en Java. Coinciden tambien para
 * importes negativos, que aparecen en las rectificativas por diferencias.
 */
public record ImporteLinea(BigDecimal baseImponible, BigDecimal cuotaIva, BigDecimal total) {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final int ESCALA = 2;

    /**
     * Calcula los importes de una linea.
     *
     * @param cantidad       unidades u horas
     * @param precioUnitario precio congelado de la linea
     * @param descuentoPct   descuento aplicado, de 0 a 100
     * @param porcentajeIva  tipo de IVA congelado en la linea
     */
    public static ImporteLinea de(BigDecimal cantidad, BigDecimal precioUnitario,
                                  BigDecimal descuentoPct, BigDecimal porcentajeIva) {
        BigDecimal descuento = descuentoPct == null ? BigDecimal.ZERO : descuentoPct;

        // ROUND(cantidad * precio * (1 - descuento / 100), 2)
        BigDecimal base = cantidad
                .multiply(precioUnitario)
                .multiply(BigDecimal.ONE.subtract(descuento.divide(CIEN, 10, RoundingMode.HALF_UP)))
                .setScale(ESCALA, RoundingMode.HALF_UP);

        // ROUND(base * porcentaje / 100, 2), sobre la base YA redondeada
        BigDecimal cuota = base
                .multiply(porcentajeIva)
                .divide(CIEN, ESCALA, RoundingMode.HALF_UP);

        return new ImporteLinea(base, cuota, base.add(cuota));
    }

    /** Importes en cero, con la escala correcta. */
    public static ImporteLinea cero() {
        BigDecimal cero = BigDecimal.ZERO.setScale(ESCALA);
        return new ImporteLinea(cero, cero, cero);
    }

    public ImporteLinea mas(ImporteLinea otro) {
        return new ImporteLinea(
                baseImponible.add(otro.baseImponible),
                cuotaIva.add(otro.cuotaIva),
                total.add(otro.total));
    }
}
