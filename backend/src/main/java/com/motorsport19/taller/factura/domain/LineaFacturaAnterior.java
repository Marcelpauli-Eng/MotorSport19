package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.orden.domain.TipoLinea;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/** Linea de una {@link FacturaAnterior}, copiada de la factura original. */
@Entity
@Immutable
@Table(name = "linea_factura_anterior")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineaFacturaAnterior {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_anterior_id", nullable = false)
    private FacturaAnterior factura;

    @Column(name = "numero_linea", nullable = false)
    private Integer numeroLinea;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoLinea tipo;

    @Column(name = "codigo", length = 50)
    private String codigo;

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "cantidad", nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioUnitario;

    @Column(name = "descuento_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoPct;

    /** Sin IVA, como la columna «Total» de la factura original. */
    @Column(name = "importe", nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    public boolean esDePieza() {
        return tipo == TipoLinea.PIEZA;
    }
}
