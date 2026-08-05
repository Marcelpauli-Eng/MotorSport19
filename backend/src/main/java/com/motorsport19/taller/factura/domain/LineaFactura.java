package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.orden.domain.TipoLinea;
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
 * Linea de factura: una COPIA de la linea de la OT, no una referencia.
 *
 * <p>No hay relacion con {@code Pieza} ni con {@code LineaOT} a proposito. El SKU
 * se guarda como texto. Asi la factura sigue siendo legible e integra aunque el
 * catalogo cambie por completo o la pieza se de de baja.
 *
 * <p>Inmutable: los triggers de la base de datos rechazan UPDATE y DELETE.
 */
@Entity
@Table(name = "linea_factura")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineaFactura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id", nullable = false, updatable = false)
    private Factura factura;

    @Column(name = "numero_linea", nullable = false, updatable = false)
    private Integer numeroLinea;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 20)
    private TipoLinea tipo;

    @Column(name = "descripcion", nullable = false, updatable = false, length = 300)
    private String descripcion;

    /** Copia textual del SKU. Sin clave ajena: instantanea historica. */
    @Column(name = "pieza_sku", updatable = false, length = 50)
    private String piezaSku;

    /** Puede ser negativa en rectificativas por diferencias. */
    @Column(name = "cantidad", nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false, updatable = false, precision = 12, scale = 4)
    private BigDecimal precioUnitario;

    @Column(name = "descuento_pct", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal descuentoPct;

    @Column(name = "tipo_iva", nullable = false, updatable = false, length = 20)
    private String tipoIva;

    @Column(name = "porcentaje_iva", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIva;

    @Generated(event = EventType.INSERT)
    @Column(name = "base_imponible", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @Generated(event = EventType.INSERT)
    @Column(name = "cuota_iva", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cuotaIva;

    @Generated(event = EventType.INSERT)
    @Column(name = "total", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal total;
}
