package com.motorsport19.taller.inventario.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.Instant;

/**
 * Pieza del catalogo de recambios.
 *
 * <p><b>El stock no se edita.</b> {@link #stockActual} es un acumulado derivado
 * que solo escribe el trigger de {@link MovimientoStock} en la base de datos;
 * el mapeo lo declara no insertable y no actualizable para que Hibernate no
 * pueda tocarlo ni por accidente. Cualquier cambio de existencias exige
 * registrar un movimiento.
 */
@Entity
@Table(name = "pieza")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pieza extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku", nullable = false, length = 50)
    private String sku;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String descripcion;

    @Column(name = "marca", length = 60)
    private String marca;

    /** Ubicacion fisica en el almacen (estanteria, cajon...). */
    @Column(name = "ubicacion", length = 50)
    private String ubicacion;

    /**
     * Existencias actuales. DERIVADO de los movimientos de stock: la base de
     * datos lo inicializa a cero y a partir de ahi solo lo modifica el trigger
     * {@code tg_movimiento_stock_aplicar}.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "stock_actual", insertable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal stockActual;

    /** Umbral por debajo del cual la pieza aparece en las alertas de reposicion. */
    @Column(name = "stock_minimo", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockMinimo;

    @Column(name = "precio_coste", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioCoste;

    /** Precio de catalogo. Se congela como precio de linea al anadirla a una OT. */
    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioVenta;

    @Column(name = "tipo_iva", nullable = false, length = 20)
    private String tipoIva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Column(name = "unidad_medida", nullable = false, length = 10)
    private String unidadMedida;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_baja")
    private Instant fechaBaja;
}
