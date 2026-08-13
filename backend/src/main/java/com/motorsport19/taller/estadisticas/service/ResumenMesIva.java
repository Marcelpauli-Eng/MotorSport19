package com.motorsport19.taller.estadisticas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Un mes de una de las dos columnas del informe por regimen de IVA.
 *
 * <p>El <b>gasto</b> es el coste del material que se ha ido en los trabajos
 * facturados ese mes, valorado al precio de coste que tenia la pieza cuando
 * salio del almacen. No incluye las compras de reposicion: una compra entra al
 * almacen sin factura de venta detras, asi que no se puede repartir entre las
 * dos columnas sin inventarse el reparto. Esa cifra sigue estando entera en el
 * informe general del ejercicio.
 *
 * <p>El <b>margen</b> se calcula sobre la base imponible, no sobre el total: el
 * IVA no es dinero del taller, y compararlo contra el coste haria que la columna
 * con IVA pareciese mas rentable solo por llevar IVA.
 */
public record ResumenMesIva(
        int anio,
        int mes,
        String nombreMes,
        String etiqueta,
        BigDecimal baseFacturada,
        BigDecimal ivaRepercutido,
        BigDecimal totalFacturado,
        int numeroFacturas,
        BigDecimal ingresoManoDeObra,
        BigDecimal ingresoPiezas,
        BigDecimal gastoMaterial,
        BigDecimal margenBruto,
        BigDecimal margenPorcentaje
) {

    private static final String[] NOMBRES = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    public static ResumenMesIva de(FilaMesIva f) {
        BigDecimal margen = f.baseFacturada().subtract(f.costeMaterialVendido());

        return new ResumenMesIva(
                f.anio(),
                f.mes(),
                NOMBRES[f.mes() - 1],
                "%s %d".formatted(NOMBRES[f.mes() - 1].substring(0, 3), f.anio()),
                redondear(f.baseFacturada()),
                redondear(f.ivaRepercutido()),
                redondear(f.totalFacturado()),
                f.numeroFacturas(),
                redondear(f.ingresoManoDeObra()),
                redondear(f.ingresoPiezas()),
                redondear(f.costeMaterialVendido()),
                redondear(margen),
                porcentaje(margen, f.baseFacturada()));
    }

    static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0
                ? BigDecimal.ZERO
                : parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
