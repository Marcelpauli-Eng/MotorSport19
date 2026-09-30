package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.moto.domain.Moto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Factura emitida con el programa anterior, antes de usar este.
 *
 * <p>No es una {@link Factura} de este programa y no se trata como tal: no
 * tiene numero de aqui, ni huella, ni entra en el libro registro ni en la
 * exportacion a la gestoria. Ya la emitio y la declaro el otro programa. Sirve
 * para verla en la ficha del cliente y de la moto, y para que el historial de
 * la moto salga completo.
 *
 * <p>Solo se lee: la carga una vez el script de migracion. El PDF original no
 * se mapea porque pesa y ningun listado lo necesita; se lee aparte.
 */
@Entity
@Immutable
@Table(name = "factura_anterior")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacturaAnterior {

    @Id
    private Long id;

    /** Programa del que viene: «NEXTGO». */
    @Column(name = "origen", nullable = false, length = 40)
    private String origen;

    /** Tal cual lo imprimio el otro programa. */
    @Column(name = "numero", nullable = false, length = 40)
    private String numero;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    /** Nula si no se sabe de que moto era: aquel programa no lo ponia en la factura. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moto_id")
    private Moto moto;

    @Column(name = "receptor_nombre", nullable = false, length = 250)
    private String receptorNombre;

    @Column(name = "receptor_nif", length = 20)
    private String receptorNif;

    @Column(name = "base_imponible", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @Column(name = "total_iva", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalIva;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** La orden de trabajo que factura, si se cargo tambien su presupuesto (V26). */
    @Column(name = "orden_trabajo_id")
    private Long ordenTrabajoId;

    @OneToMany(mappedBy = "factura")
    @OrderBy("numeroLinea ASC")
    private List<LineaFacturaAnterior> lineas = new ArrayList<>();
}
