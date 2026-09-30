package com.motorsport19.taller.orden.domain;

import com.motorsport19.taller.inventario.domain.Pieza;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Linea de una orden de trabajo: horas de taller o una pieza consumida.
 *
 * <p>Los importes y las reglas de la linea viven en {@link LineaImporte}; aqui
 * solo queda de que orden es.
 */
@Entity
@Table(name = "linea_ot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineaOT extends LineaImporte {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_trabajo_id", nullable = false)
    private OrdenTrabajo ordenTrabajo;

    private LineaOT(OrdenTrabajo orden) {
        this.ordenTrabajo = orden;
    }

    /** Horas de taller, valoradas a la tarifa congelada de la OT. */
    static LineaOT manoDeObra(OrdenTrabajo orden, int numeroLinea, String descripcion, BigDecimal horas,
                              BigDecimal tarifaHora, BigDecimal descuentoPct, String tipoIva,
                              BigDecimal porcentajeIva) {
        LineaOT linea = new LineaOT(orden);
        linea.rellenarManoDeObra(numeroLinea, descripcion, horas, tarifaHora, descuentoPct, tipoIva,
                porcentajeIva);
        return linea;
    }

    /** Pieza del catalogo, con el precio de venta de hoy congelado en la linea. */
    static LineaOT pieza(OrdenTrabajo orden, int numeroLinea, Pieza pieza, BigDecimal cantidad,
                         BigDecimal descuentoPct, BigDecimal porcentajeIva) {
        LineaOT linea = new LineaOT(orden);
        linea.rellenarPieza(numeroLinea, pieza, cantidad, descuentoPct, porcentajeIva);
        return linea;
    }

    /** Tasa de una regla de cobro. No sale del almacen. */
    static LineaOT tasa(OrdenTrabajo orden, int numeroLinea, String concepto, BigDecimal cantidad,
                        BigDecimal importe, String tipoIva, BigDecimal porcentajeIva) {
        LineaOT linea = new LineaOT(orden);
        linea.rellenarTasa(numeroLinea, concepto, cantidad, importe, tipoIva, porcentajeIva);
        return linea;
    }
}
