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
import java.math.RoundingMode;

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

    // Los tres importes los calcula PostgreSQL como columnas generadas. Java
    // calcula los suyos por separado para la huella, y el trigger diferido
    // comprueba al hacer commit que ambos coinciden.

    @Generated(event = EventType.INSERT)
    @Column(name = "base_imponible", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @Generated(event = EventType.INSERT)
    @Column(name = "cuota_iva", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cuotaIva;

    @Generated(event = EventType.INSERT)
    @Column(name = "total", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal total;

    static LineaFactura copiar(Factura factura, int numeroLinea, LineaAFacturar origen) {
        LineaFactura linea = new LineaFactura();
        linea.factura = factura;
        linea.numeroLinea = numeroLinea;
        linea.tipo = origen.tipo();
        linea.descripcion = origen.descripcion();
        linea.piezaSku = origen.piezaSku();
        linea.cantidad = origen.cantidad();
        linea.precioUnitario = origen.precioUnitario();
        linea.descuentoPct = origen.descuentoPct();
        linea.tipoIva = origen.tipoIva();
        linea.porcentajeIva = origen.porcentajeIva();
        return linea;
    }

    /**
     * Importes calculados en Java.
     *
     * <p>Mientras la linea no se haya insertado, las columnas generadas por la
     * base de datos estan vacias; este metodo da el mismo resultado en ambos
     * momentos.
     */
    public ImporteLinea importes() {
        return ImporteLinea.de(cantidad, precioUnitario, descuentoPct, porcentajeIva);
    }

    /**
     * Lo que costaria la linea a precio de tarifa, sin el descuento.
     *
     * <p>Se redondea igual que la base imponible para que la resta entre ambos
     * de justo el descuento aplicado. Un cliente que revisa la factura suma, y
     * si le sale un centimo de diferencia llama al taller.
     */
    public BigDecimal importeBruto() {
        return cantidad.multiply(precioUnitario).setScale(2, RoundingMode.HALF_UP);
    }

    /** Rebaja aplicada en esta linea, en euros. */
    public BigDecimal importeDescuento() {
        return importeBruto().subtract(importes().baseImponible());
    }

    public boolean tieneDescuento() {
        return descuentoPct != null && descuentoPct.signum() > 0;
    }
}
