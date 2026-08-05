package com.motorsport19.taller.inventario.domain;

import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.usuario.domain.Usuario;
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
import java.time.Instant;

/**
 * Asiento del libro de movimientos de stock.
 *
 * <p>Es la <b>unica fuente de verdad</b> del inventario: {@code pieza.stock_actual}
 * es solo un acumulado cacheado de esta tabla.
 *
 * <p><b>Inmutable.</b> Los triggers de la base de datos rechazan cualquier UPDATE
 * o DELETE. Un movimiento equivocado se corrige registrando un AJUSTE de signo
 * contrario, de forma que el libro conserva tanto el error como su correccion.
 */
@Entity
@Table(name = "movimiento_stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pieza_id", nullable = false, updatable = false)
    private Pieza pieza;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 20)
    private TipoMovimiento tipo;

    /** Cantidad CON SIGNO: positiva suma existencias, negativa las resta. */
    @Column(name = "cantidad", nullable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    /** Existencias antes del movimiento. Lo calcula el trigger. */
    @Generated(event = EventType.INSERT)
    @Column(name = "stock_anterior", insertable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal stockAnterior;

    /** Existencias despues del movimiento. Lo calcula el trigger. */
    @Generated(event = EventType.INSERT)
    @Column(name = "stock_resultante", insertable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal stockResultante;

    @Column(name = "fecha", nullable = false, updatable = false)
    private Instant fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    /** OT que consume la pieza (solo en SALIDA y DEVOLUCION). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_trabajo_id", updatable = false)
    private OrdenTrabajo ordenTrabajo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linea_ot_id", updatable = false)
    private LineaOT lineaOt;

    /** Obligatorio en los AJUSTE para justificar la correccion. */
    @Column(name = "motivo", updatable = false, length = 300)
    private String motivo;

    /** Albaran o factura del proveedor en las ENTRADA. */
    @Column(name = "documento_proveedor", updatable = false, length = 60)
    private String documentoProveedor;

    @Column(name = "precio_coste_unitario", updatable = false, precision = 12, scale = 4)
    private BigDecimal precioCosteUnitario;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
