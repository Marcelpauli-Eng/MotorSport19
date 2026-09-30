package com.motorsport19.taller.solicitud.domain;

import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.orden.domain.LineaImporte;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Linea del presupuesto de una solicitud web. Se valora igual que la de una orden. */
@Entity
@Table(name = "linea_presupuesto_web")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineaPresupuestoWeb extends LineaImporte {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private SolicitudWeb solicitud;

    private LineaPresupuestoWeb(SolicitudWeb solicitud) {
        this.solicitud = solicitud;
    }

    static LineaPresupuestoWeb manoDeObra(SolicitudWeb s, int numeroLinea, String descripcion, BigDecimal horas,
                                          BigDecimal tarifaHora, BigDecimal descuentoPct, String tipoIva,
                                          BigDecimal porcentajeIva) {
        LineaPresupuestoWeb linea = new LineaPresupuestoWeb(s);
        linea.rellenarManoDeObra(numeroLinea, descripcion, horas, tarifaHora, descuentoPct, tipoIva,
                porcentajeIva);
        return linea;
    }

    static LineaPresupuestoWeb pieza(SolicitudWeb s, int numeroLinea, Pieza pieza, BigDecimal cantidad,
                                     BigDecimal descuentoPct, BigDecimal porcentajeIva) {
        LineaPresupuestoWeb linea = new LineaPresupuestoWeb(s);
        linea.rellenarPieza(numeroLinea, pieza, cantidad, descuentoPct, porcentajeIva);
        return linea;
    }

    static LineaPresupuestoWeb tasa(SolicitudWeb s, int numeroLinea, String concepto, BigDecimal cantidad,
                                    BigDecimal importe, String tipoIva, BigDecimal porcentajeIva) {
        LineaPresupuestoWeb linea = new LineaPresupuestoWeb(s);
        linea.rellenarTasa(numeroLinea, concepto, cantidad, importe, tipoIva, porcentajeIva);
        return linea;
    }
}
