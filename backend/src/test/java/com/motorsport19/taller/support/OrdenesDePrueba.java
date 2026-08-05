package com.motorsport19.taller.support;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

/**
 * Construccion de ordenes de trabajo para los tests.
 *
 * <p>Los identificadores los asigna la base de datos, asi que aqui se fijan por
 * reflexion para poder relacionar lineas y movimientos en los tests unitarios.
 */
public final class OrdenesDePrueba {

    public static final BigDecimal TARIFA_HORA = new BigDecimal("45.00");
    public static final BigDecimal IVA_GENERAL = new BigDecimal("21.00");

    private OrdenesDePrueba() {
    }

    public static Cliente cliente() {
        return Cliente.registrar("Carlos", "Nunez Prieto", "600100101", null);
    }

    public static Moto moto(Cliente cliente) {
        return Moto.registrar(cliente, "1234 JKL", "Yamaha", "MT-07", 2021, 689, "Azul",
                null, 24500, null);
    }

    /** Orden recien abierta, en estado RECIBIDA. */
    public static OrdenTrabajo recienAbierta() {
        Moto moto = moto(cliente());
        ReflectionTestUtils.setField(moto, "id", 1L);

        OrdenTrabajo orden = OrdenTrabajo.abrir(2026, 1, moto, TARIFA_HORA, 24600,
                "Ruido en la transmision", null, null, null, null);
        ReflectionTestUtils.setField(orden, "id", 1L);
        return orden;
    }

    /**
     * Orden avanzada hasta APROBADA, con una linea de mano de obra y las piezas
     * que se le indiquen.
     */
    public static OrdenTrabajo aprobadaCon(com.motorsport19.taller.inventario.domain.Pieza... piezas) {
        OrdenTrabajo orden = recienAbierta();
        orden.iniciarDiagnostico(null, null);
        orden.registrarDiagnostico("Kit de transmision al final de su vida util");
        orden.anadirManoDeObra("Sustitucion del kit de transmision", new BigDecimal("2.000"),
                BigDecimal.ZERO, "GENERAL", IVA_GENERAL);

        long siguienteId = 100L;
        for (com.motorsport19.taller.inventario.domain.Pieza pieza : piezas) {
            orden.anadirPieza(pieza, BigDecimal.ONE, BigDecimal.ZERO, IVA_GENERAL);
        }
        for (LineaOT linea : orden.getLineas()) {
            ReflectionTestUtils.setField(linea, "id", siguienteId++);
        }

        orden.presupuestar(null);
        orden.aprobar("Carlos Nunez Prieto", null);
        return orden;
    }

    /** Fija la cantidad de una linea sin pasar por las validaciones de consumo. */
    public static void fijarCantidad(LineaOT linea, String cantidad) {
        ReflectionTestUtils.setField(linea, "cantidad", new BigDecimal(cantidad));
    }
}
