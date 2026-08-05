package com.motorsport19.taller.orden.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.inventario.domain.Pieza;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;

/**
 * Linea de una orden de trabajo: horas de taller o una pieza consumida.
 *
 * <p><b>Precios congelados.</b> {@link #precioUnitario} y {@link #porcentajeIva} se
 * copian del catalogo (o de la tarifa de la OT) en el instante de anadir la linea.
 * Si despues sube el precio de venta de la pieza, esta OT no se altera.
 *
 * <p>Los importes ({@link #baseImponible}, {@link #cuotaIva}, {@link #total}) son
 * columnas generadas por PostgreSQL: la aplicacion no puede desincronizarse de la
 * base de datos porque no los escribe.
 */
@Entity
@Table(name = "linea_ot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineaOT extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_trabajo_id", nullable = false)
    private OrdenTrabajo ordenTrabajo;

    /** Posicion de la linea dentro de la OT, empezando en 1. */
    @Column(name = "numero_linea", nullable = false)
    private Integer numeroLinea;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoLinea tipo;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion;

    /** Solo en lineas de tipo PIEZA. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pieza_id")
    private Pieza pieza;

    /** Horas en MANO_DE_OBRA, unidades en PIEZA. */
    @Column(name = "cantidad", nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    /** Congelado al crear la linea. */
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioUnitario;

    @Column(name = "descuento_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoPct;

    @Column(name = "tipo_iva", nullable = false, length = 20)
    private String tipoIva;

    /** Porcentaje de IVA congelado en la linea. */
    @Column(name = "porcentaje_iva", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIva;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "base_imponible", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "cuota_iva", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cuotaIva;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "total", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal total;
}
