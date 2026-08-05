package com.motorsport19.taller.orden.domain;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.usuario.domain.Usuario;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orden de trabajo: la unidad de trabajo del taller, desde que entra la moto
 * hasta que se entrega.
 *
 * <p>Una OT en estado {@link EstadoOT#ENTREGADA} es <b>inmutable</b>: los triggers
 * de la base de datos rechazan cualquier modificacion de la cabecera y de sus
 * lineas.
 */
@Entity
@Table(name = "orden_trabajo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrdenTrabajo extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ano al que pertenece la numeracion. */
    @Column(name = "ejercicio", nullable = false)
    private Integer ejercicio;

    /** Correlativo dentro del ejercicio. */
    @Column(name = "numero", nullable = false)
    private Integer numero;

    /** Numero visible (OT-2026-00042). Lo compone la base de datos. */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "codigo", insertable = false, updatable = false, length = 20)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moto_id", nullable = false)
    private Moto moto;

    /** Propietario en el momento de la entrada. Instantanea: no se recalcula. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "fecha_entrada", nullable = false)
    private Instant fechaEntrada;

    @Column(name = "fecha_estimada_salida")
    private LocalDate fechaEstimadaSalida;

    @Column(name = "fecha_real_salida")
    private Instant fechaRealSalida;

    @Column(name = "km_entrada", nullable = false)
    private Integer kmEntrada;

    /** Averia tal y como la describe el cliente. */
    @Column(name = "problema_reportado", nullable = false, columnDefinition = "text")
    private String problemaReportado;

    /** Conclusion del tecnico tras revisar la moto. */
    @Column(name = "diagnostico", columnDefinition = "text")
    private String diagnostico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id")
    private Usuario tecnico;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoOT estado;

    /** Tarifa/hora congelada al abrir la OT. */
    @Column(name = "tarifa_hora", nullable = false, precision = 12, scale = 2)
    private BigDecimal tarifaHora;

    @Column(name = "fecha_presupuesto")
    private Instant fechaPresupuesto;

    @Column(name = "fecha_aprobacion")
    private Instant fechaAprobacion;

    /** Quien aprobo el presupuesto por parte del cliente. */
    @Column(name = "aprobado_por", length = 150)
    private String aprobadoPor;

    @Column(name = "motivo_rechazo", columnDefinition = "text")
    private String motivoRechazo;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @OneToMany(mappedBy = "ordenTrabajo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroLinea ASC")
    private List<LineaOT> lineas = new ArrayList<>();

    @OneToMany(mappedBy = "ordenTrabajo", cascade = CascadeType.ALL)
    @OrderBy("fecha ASC, id ASC")
    private List<CambioEstadoOT> historialEstados = new ArrayList<>();

    /** Vista de solo lectura: las lineas se anaden con metodos de dominio (fase 3). */
    public List<LineaOT> getLineas() {
        return Collections.unmodifiableList(lineas);
    }

    /** Vista de solo lectura del historial de estados. */
    public List<CambioEstadoOT> getHistorialEstados() {
        return Collections.unmodifiableList(historialEstados);
    }
}
