package com.motorsport19.taller.documento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Todo lo que necesita el papel, ya resuelto.
 *
 * <p>El generador no sabe si esta imprimiendo un presupuesto o una factura: se
 * le da esto y lo dibuja. Asi los dos documentos salen exactamente con el mismo
 * formato, que es justo lo que se le pide, y no hay dos maquetaciones que se
 * puedan ir separando con el tiempo.
 *
 * @param titulo        «PRESUPUESTO» o «FACTURA», tal cual va impreso
 * @param rotuloTotal   el pie grande de la derecha: «TOTAL PRESUPUESTO»
 * @param numeroDocumento referencia larga del documento
 * @param siniestro     numero de parte del seguro; «S/N» cuando no hay
 * @param fechaValidez  hasta cuando se mantiene el precio; nulo en facturas
 */
public record DocumentoImprimible(
        String titulo,
        String rotuloTotal,
        String numeroDocumento,
        String serie,
        LocalDate fecha,
        String siniestro,

        Emisor emisor,
        Cliente cliente,
        Vehiculo vehiculo,

        String formaPago,
        LocalDate fechaValidez,

        List<Linea> lineas,
        Totales totales,
        String observaciones
) {

    public record Emisor(String razonSocial, String direccion, String poblacion,
                         String nif, String telefono, String email) {
    }

    public record Cliente(String nombre, String poblacion, String nif,
                          String numero, String telefono) {
    }

    public record Vehiculo(String matricula, String bastidor, String modelo, Integer km) {
    }

    /**
     * Una linea del documento.
     *
     * <p>{@code cabecera} marca las filas que solo rotulan un bloque («MANO DE
     * OBRA»): van en negrita, sin cantidad ni precio, y con el subtotal del
     * bloque a la derecha.
     */
    public record Linea(
            boolean cabecera,
            String codigo,
            String descripcion,
            BigDecimal cantidad,
            BigDecimal precio,
            BigDecimal descuentoPct,
            BigDecimal total
    ) {
        public static Linea de(String codigo, String descripcion, BigDecimal cantidad,
                               BigDecimal precio, BigDecimal descuentoPct, BigDecimal total) {
            return new Linea(false, codigo, descripcion, cantidad, precio, descuentoPct, total);
        }

        public static Linea cabecera(String rotulo, BigDecimal subtotal) {
            return new Linea(true, null, rotulo, null, null, null, subtotal);
        }
    }

    /**
     * La banda de totales del pie.
     *
     * <p>{@code tasas} y {@code portes} van siempre a cero: el programa no los
     * gestiona todavia, pero las columnas se imprimen igual porque forman parte
     * del formato del documento que el taller lleva usando.
     */
    public record Totales(
            BigDecimal importe,
            BigDecimal descuentoLinea,
            BigDecimal tasas,
            BigDecimal descuentos,
            BigDecimal portes,
            BigDecimal baseImponible,
            BigDecimal porcentajeIva,
            BigDecimal impuestos,
            BigDecimal irpfPorcentaje,
            BigDecimal irpf,
            BigDecimal total
    ) {
    }
}
