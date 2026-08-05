package com.motorsport19.taller.orden.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ReglaNegocioException;
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
 * copian del catalogo (o de la tarifa de la OT) en el instante de crear la linea.
 * Si despues sube el precio de venta de la pieza, esta OT no se altera. Por eso
 * las fabricas leen el precio una sola vez y no guardan forma de recalcularlo.
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

    // ==================================================================
    // Fabricas
    // ==================================================================

    /** Horas de taller, valoradas a la tarifa congelada de la OT. */
    static LineaOT manoDeObra(OrdenTrabajo orden, int numeroLinea, String descripcion, BigDecimal horas,
                              BigDecimal tarifaHora, BigDecimal descuentoPct, String tipoIva,
                              BigDecimal porcentajeIva) {
        if (textoONulo(descripcion) == null) {
            throw new ReglaNegocioException("La linea de mano de obra necesita una descripcion.");
        }
        LineaOT linea = base(orden, numeroLinea, TipoLinea.MANO_DE_OBRA, descripcion, horas,
                tarifaHora, descuentoPct, tipoIva, porcentajeIva);
        linea.pieza = null;
        return linea;
    }

    /**
     * Pieza del catalogo.
     *
     * <p>El precio se toma AQUI de {@code pieza.precioVenta} y se queda fijado en
     * la linea. A partir de este momento el catalogo puede cambiar lo que quiera.
     */
    static LineaOT pieza(OrdenTrabajo orden, int numeroLinea, Pieza pieza, BigDecimal cantidad,
                         BigDecimal descuentoPct, BigDecimal porcentajeIva) {
        if (pieza == null) {
            throw new ReglaNegocioException("La linea de tipo PIEZA necesita una pieza del catalogo.");
        }
        if (!pieza.isActivo()) {
            throw new ReglaNegocioException(
                    "La pieza %s esta dada de baja y no se puede anadir a una orden de trabajo."
                            .formatted(pieza.getSku()));
        }
        LineaOT linea = base(orden, numeroLinea, TipoLinea.PIEZA, pieza.getDescripcion(), cantidad,
                pieza.getPrecioVenta(), descuentoPct, pieza.getTipoIva(), porcentajeIva);
        linea.pieza = pieza;
        return linea;
    }

    // ==================================================================
    // Modificacion
    // ==================================================================

    /**
     * Cambia la cantidad de la linea.
     *
     * <p>No permite bajar de lo ya consumido del almacen: esas unidades han salido
     * fisicamente y hay que devolverlas antes de dejar de facturarlas.
     */
    public void cambiarCantidad(BigDecimal nuevaCantidad, BigDecimal yaConsumido) {
        exigirCantidadPositiva(nuevaCantidad);
        if (yaConsumido != null && nuevaCantidad.compareTo(yaConsumido) < 0) {
            throw new ReglaNegocioException(
                    ("La linea %d ya ha consumido %s unidades del almacen. Devuelvalas antes de bajar la "
                     + "cantidad a %s.").formatted(numeroLinea, yaConsumido.toPlainString(),
                            nuevaCantidad.toPlainString()));
        }
        this.cantidad = nuevaCantidad;
    }

    public void cambiarDescuento(BigDecimal descuentoPct) {
        this.descuentoPct = validarDescuento(descuentoPct);
    }

    public void cambiarDescripcion(String descripcion) {
        if (textoONulo(descripcion) == null) {
            throw new ReglaNegocioException("La descripcion de la linea no puede quedar vacia.");
        }
        this.descripcion = textoONulo(descripcion);
    }

    // ==================================================================
    // Consultas
    // ==================================================================

    public boolean esDePieza() {
        return tipo == TipoLinea.PIEZA;
    }

    /** Referencia de almacen, o vacio si es mano de obra. */
    public String skuPieza() {
        return pieza == null ? null : pieza.getSku();
    }

    // ==================================================================

    private static LineaOT base(OrdenTrabajo orden, int numeroLinea, TipoLinea tipo, String descripcion,
                                BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuentoPct,
                                String tipoIva, BigDecimal porcentajeIva) {
        exigirCantidadPositiva(cantidad);
        if (precioUnitario == null || precioUnitario.signum() < 0) {
            throw new ReglaNegocioException("El precio unitario de la linea no puede ser negativo.");
        }
        if (porcentajeIva == null || porcentajeIva.signum() < 0
                || porcentajeIva.compareTo(new BigDecimal("100")) > 0) {
            throw new ReglaNegocioException("El porcentaje de IVA de la linea debe estar entre 0 y 100.");
        }

        LineaOT linea = new LineaOT();
        linea.ordenTrabajo = orden;
        linea.numeroLinea = numeroLinea;
        linea.tipo = tipo;
        linea.descripcion = textoONulo(descripcion);
        linea.cantidad = cantidad;
        linea.precioUnitario = precioUnitario;
        linea.descuentoPct = validarDescuento(descuentoPct);
        linea.tipoIva = tipoIva;
        linea.porcentajeIva = porcentajeIva;
        return linea;
    }

    private static void exigirCantidadPositiva(BigDecimal cantidad) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ReglaNegocioException("La cantidad de una linea debe ser mayor que cero.");
        }
    }

    private static BigDecimal validarDescuento(BigDecimal descuentoPct) {
        if (descuentoPct == null) {
            return BigDecimal.ZERO;
        }
        if (descuentoPct.signum() < 0 || descuentoPct.compareTo(new BigDecimal("100")) > 0) {
            throw new ReglaNegocioException("El descuento debe estar entre 0 y 100.");
        }
        return descuentoPct;
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
