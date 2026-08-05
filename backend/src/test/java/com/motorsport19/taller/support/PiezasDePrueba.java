package com.motorsport19.taller.support;

import com.motorsport19.taller.inventario.domain.Pieza;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

/**
 * Construccion de piezas para los tests.
 *
 * <p>{@code id} y {@code stockActual} los escribe exclusivamente la base de datos
 * (identidad y trigger de movimientos), asi que la entidad no expone ninguna
 * forma de fijarlos: es justo la garantia que se quiere proteger. En los tests
 * unitarios no hay base de datos, asi que aqui se simula lo que ella habria
 * dejado escrito, y solo aqui.
 */
public final class PiezasDePrueba {

    private PiezasDePrueba() {
    }

    /** Pieza con existencias y stock minimo concretos. */
    public static Pieza con(Long id, String sku, String stockActual, String stockMinimo) {
        Pieza pieza = Pieza.registrar(
                sku,
                "Pieza de prueba " + sku,
                "MarcaTest",
                "A1-01",
                new BigDecimal(stockMinimo),
                new BigDecimal("10.0000"),
                new BigDecimal("20.0000"),
                "GENERAL",
                null,
                "UD",
                null);

        ReflectionTestUtils.setField(pieza, "id", id);
        ReflectionTestUtils.setField(pieza, "stockActual", new BigDecimal(stockActual));
        return pieza;
    }

    /** Pieza con existencias holgadas: 100 unidades y minimo 5. */
    public static Pieza conStock(Long id, String sku, String stockActual) {
        return con(id, sku, stockActual, "5");
    }

    /** Simula el efecto del trigger tras aplicar un movimiento. */
    public static void simularStockTrasMovimiento(Pieza pieza, String nuevoStock) {
        ReflectionTestUtils.setField(pieza, "stockActual", new BigDecimal(nuevoStock));
    }
}
