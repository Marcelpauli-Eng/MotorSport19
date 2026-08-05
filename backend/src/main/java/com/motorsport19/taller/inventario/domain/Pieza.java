package com.motorsport19.taller.inventario.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
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
 * pueda tocarlo ni por accidente. Fijate en que esta clase no expone ningun
 * metodo para cambiarlo: la unica via es registrar un movimiento.
 */
@Entity
@Table(name = "pieza")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pieza extends EntidadAuditable {

    private static final String UNIDAD_POR_DEFECTO = "UD";
    private static final String TIPO_IVA_POR_DEFECTO = "GENERAL";

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

    // ------------------------------------------------------------------
    // Creacion
    // ------------------------------------------------------------------

    /**
     * Da de alta una pieza en el catalogo. Nace SIEMPRE con stock cero: las
     * existencias iniciales se cargan con un movimiento de ENTRADA, para que el
     * libro de movimientos explique cada unidad del almacen.
     */
    public static Pieza registrar(String sku, String descripcion, String marca, String ubicacion,
                                  BigDecimal stockMinimo, BigDecimal precioCoste, BigDecimal precioVenta,
                                  String tipoIva, Proveedor proveedor, String unidadMedida,
                                  String observaciones) {
        Pieza pieza = new Pieza();
        pieza.aplicarDatos(sku, descripcion, marca, ubicacion, stockMinimo, tipoIva, proveedor, unidadMedida,
                observaciones);
        pieza.aplicarPrecios(precioCoste, precioVenta);
        pieza.activo = true;
        return pieza;
    }

    // ------------------------------------------------------------------
    // Modificacion
    // ------------------------------------------------------------------

    public void actualizarDatos(String sku, String descripcion, String marca, String ubicacion,
                                BigDecimal stockMinimo, String tipoIva, Proveedor proveedor,
                                String unidadMedida, String observaciones) {
        comprobarActiva();
        aplicarDatos(sku, descripcion, marca, ubicacion, stockMinimo, tipoIva, proveedor, unidadMedida,
                observaciones);
    }

    /**
     * Cambia los precios de catalogo.
     *
     * <p>No afecta a ninguna OT ya abierta: las lineas guardan el precio
     * congelado en el momento en que se anadieron.
     */
    public void actualizarPrecios(BigDecimal precioCoste, BigDecimal precioVenta) {
        comprobarActiva();
        aplicarPrecios(precioCoste, precioVenta);
    }

    public void darDeBaja() {
        if (!activo) {
            throw new ConflictoException("La pieza %s ya estaba dada de baja.".formatted(sku));
        }
        this.activo = false;
        this.fechaBaja = Instant.now();
    }

    public void reactivar() {
        if (activo) {
            throw new ConflictoException("La pieza %s ya estaba activa.".formatted(sku));
        }
        this.activo = true;
        this.fechaBaja = null;
    }

    // ------------------------------------------------------------------
    // Consultas de dominio
    // ------------------------------------------------------------------

    /** Existencias actuales, nunca nulas (una pieza recien creada tiene cero). */
    public BigDecimal existencias() {
        return stockActual == null ? BigDecimal.ZERO : stockActual;
    }

    public boolean estaBajoMinimo() {
        return existencias().compareTo(stockMinimo) <= 0;
    }

    public boolean sinExistencias() {
        return existencias().signum() == 0;
    }

    /** Indica si hay existencias suficientes para servir la cantidad pedida. */
    public boolean hayExistenciasPara(BigDecimal cantidad) {
        return existencias().compareTo(cantidad) >= 0;
    }

    // ------------------------------------------------------------------

    private void aplicarDatos(String sku, String descripcion, String marca, String ubicacion,
                              BigDecimal stockMinimo, String tipoIva, Proveedor proveedor,
                              String unidadMedida, String observaciones) {
        String skuLimpio = textoONulo(sku);
        if (skuLimpio == null) {
            throw new ReglaNegocioException("El SKU de la pieza es obligatorio.");
        }
        if (textoONulo(descripcion) == null) {
            throw new ReglaNegocioException("La descripcion de la pieza es obligatoria.");
        }
        if (stockMinimo == null || stockMinimo.signum() < 0) {
            throw new ReglaNegocioException("El stock minimo no puede ser negativo.");
        }

        this.sku = skuLimpio.toUpperCase();
        this.descripcion = textoONulo(descripcion);
        this.marca = textoONulo(marca);
        this.ubicacion = textoONulo(ubicacion);
        this.stockMinimo = stockMinimo;
        this.tipoIva = textoONulo(tipoIva) != null ? textoONulo(tipoIva) : TIPO_IVA_POR_DEFECTO;
        this.proveedor = proveedor;
        this.unidadMedida = textoONulo(unidadMedida) != null
                ? textoONulo(unidadMedida).toUpperCase()
                : UNIDAD_POR_DEFECTO;
        this.observaciones = textoONulo(observaciones);
    }

    private void aplicarPrecios(BigDecimal precioCoste, BigDecimal precioVenta) {
        if (precioCoste == null || precioCoste.signum() < 0) {
            throw new ReglaNegocioException("El precio de coste no puede ser negativo.");
        }
        if (precioVenta == null || precioVenta.signum() < 0) {
            throw new ReglaNegocioException("El precio de venta no puede ser negativo.");
        }
        this.precioCoste = precioCoste;
        this.precioVenta = precioVenta;
    }

    private void comprobarActiva() {
        if (!activo) {
            throw new ConflictoException(
                    "La pieza %s esta dada de baja: reactivela antes de modificarla.".formatted(sku));
        }
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
