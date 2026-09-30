package com.motorsport19.taller.estadisticas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Un mes del informe de facturacion, con los importes derivados ya calculados.
 *
 * <p>Aqui viven las tres restas que le interesan a quien lleva el taller:
 *
 * <ul>
 *   <li><b>IVA a liquidar</b> = repercutido − soportado. Es lo que, a grandes
 *       rasgos, habra que ingresar en Hacienda ese trimestre.</li>
 *   <li><b>Margen bruto</b> = base facturada − coste del material que se ha ido
 *       en esos trabajos. Lo que queda para pagar nominas, local y luz.</li>
 *   <li><b>Reparto</b> entre mano de obra y piezas, que es lo que dice de que
 *       vive de verdad el taller.</li>
 * </ul>
 */
public record ResumenMes(
        int mes,
        String nombreMes,
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
        /** Sobre lo que se calcula el margen: sin coste conocido no hay margen (cero). */
        BigDecimal baseConCoste,
        BigDecimal margenBruto,
        BigDecimal margenPorcentaje,
        int ordenesAbiertas,
        BigDecimal diasMediosEnTaller
) {

    private static final String[] NOMBRES = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    public static ResumenMes de(FilaMes f, BigDecimal diasMediosEnTaller) {
        BigDecimal ivaALiquidar = f.ivaRepercutido().subtract(f.ivaSoportado());
        // Solo con las facturas de este programa: las de NEXTGO no traen lo que
        // costo el material, y contarlas daria un margen inflado.
        BigDecimal margen = f.baseConCoste().subtract(f.costeMaterialVendido());

        // Sobre la base, no sobre el total: el IVA no es del taller.
        BigDecimal porcentaje = f.baseConCoste().signum() == 0
                ? BigDecimal.ZERO
                : margen.multiply(BigDecimal.valueOf(100))
                        .divide(f.baseConCoste(), 1, RoundingMode.HALF_UP);

        return new ResumenMes(
                f.mes(),
                NOMBRES[f.mes() - 1],
                redondear(f.baseFacturada()),
                redondear(f.ivaRepercutido()),
                redondear(f.totalFacturado()),
                f.numeroFacturas(),
                redondear(f.ingresoManoDeObra()),
                redondear(f.ingresoPiezas()),
                redondear(f.comprasMaterial()),
                redondear(f.ivaSoportado()),
                redondear(ivaALiquidar),
                redondear(f.costeMaterialVendido()),
                redondear(f.baseConCoste()),
                redondear(margen),
                porcentaje,
                f.ordenesAbiertas(),
                diasMediosEnTaller.setScale(1, RoundingMode.HALF_UP)
        );
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
