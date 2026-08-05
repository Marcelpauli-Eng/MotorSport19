package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * Factura emitida.
 *
 * <p><b>Inmutable.</b> Una vez insertada no se edita ni se borra jamas: los
 * triggers de PostgreSQL rechazan UPDATE y DELETE sobre esta tabla, sobre sus
 * lineas y sobre su desglose de IVA. Toda correccion se hace emitiendo una
 * factura rectificativa que apunta a la original mediante
 * {@link #facturaRectificada}.
 *
 * <p>Todos los campos de negocio se declaran no actualizables, de modo que
 * Hibernate tampoco intentaria modificarlos.
 *
 * <p>La factura se autocontiene: datos fiscales de emisor y receptor, matricula
 * y lineas se COPIAN en el momento de la emision. Se puede leer integra dentro
 * de veinte anos aunque el cliente, la moto o el catalogo ya no existan.
 */
@Entity
@Table(name = "factura")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Factura {

    /** Huella de partida de la cadena: 64 ceros. */
    public static final String HUELLA_GENESIS = "0".repeat(64);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ----- Identificacion -----

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "serie_id", nullable = false, updatable = false)
    private SerieFactura serie;

    /** Copia del codigo de serie: la factura no depende de otras tablas para leerse. */
    @Column(name = "serie_codigo", nullable = false, updatable = false, length = 10)
    private String serieCodigo;

    @Column(name = "ejercicio", nullable = false, updatable = false)
    private Integer ejercicio;

    @Column(name = "numero", nullable = false, updatable = false)
    private Integer numero;

    /** Numero visible completo (A/2026/000123). Lo compone la base de datos. */
    @Generated(event = EventType.INSERT)
    @Column(name = "numero_completo", insertable = false, updatable = false, length = 40)
    private String numeroCompleto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 20)
    private TipoFactura tipo;

    // ----- Origen y rectificacion -----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_trabajo_id", updatable = false)
    private OrdenTrabajo ordenTrabajo;

    /** Factura corregida por esta rectificativa. Solo en tipo RECTIFICATIVA. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_rectificada_id", updatable = false)
    private Factura facturaRectificada;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_rectificativa", updatable = false, length = 20)
    private TipoRectificativa tipoRectificativa;

    @Column(name = "motivo_rectificacion", updatable = false, columnDefinition = "text")
    private String motivoRectificacion;

    // ----- Fechas -----

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private LocalDate fechaEmision;

    @Column(name = "fecha_operacion", nullable = false, updatable = false)
    private LocalDate fechaOperacion;

    @Column(name = "timestamp_emision", nullable = false, updatable = false)
    private Instant timestampEmision;

    // ----- Datos fiscales congelados -----

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "nombre",       column = @Column(name = "emisor_razon_social", nullable = false, updatable = false, length = 200)),
            @AttributeOverride(name = "nif",          column = @Column(name = "emisor_nif",          nullable = false, updatable = false, length = 20)),
            @AttributeOverride(name = "direccion",    column = @Column(name = "emisor_direccion",    nullable = false, updatable = false, length = 200)),
            @AttributeOverride(name = "codigoPostal", column = @Column(name = "emisor_cp",           nullable = false, updatable = false, length = 10)),
            @AttributeOverride(name = "ciudad",       column = @Column(name = "emisor_ciudad",       nullable = false, updatable = false, length = 100)),
            @AttributeOverride(name = "provincia",    column = @Column(name = "emisor_provincia",    nullable = false, updatable = false, length = 100)),
            @AttributeOverride(name = "pais",         column = @Column(name = "emisor_pais",         nullable = false, updatable = false, length = 60))
    })
    private DatosFiscales emisor;

    /** Referencia informativa al cliente; los datos que valen son los copiados. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receptor_id", updatable = false)
    private Cliente receptor;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "nombre",       column = @Column(name = "receptor_nombre",    nullable = false, updatable = false, length = 250)),
            @AttributeOverride(name = "nif",          column = @Column(name = "receptor_nif",       nullable = false, updatable = false, length = 20)),
            @AttributeOverride(name = "direccion",    column = @Column(name = "receptor_direccion", nullable = false, updatable = false, length = 200)),
            @AttributeOverride(name = "codigoPostal", column = @Column(name = "receptor_cp",        nullable = false, updatable = false, length = 10)),
            @AttributeOverride(name = "ciudad",       column = @Column(name = "receptor_ciudad",    nullable = false, updatable = false, length = 100)),
            @AttributeOverride(name = "provincia",    column = @Column(name = "receptor_provincia", nullable = false, updatable = false, length = 100)),
            @AttributeOverride(name = "pais",         column = @Column(name = "receptor_pais",      nullable = false, updatable = false, length = 60))
    })
    private DatosFiscales datosReceptor;

    // ----- Descripcion del servicio, congelada -----

    @Column(name = "matricula", updatable = false, length = 15)
    private String matricula;

    @Column(name = "descripcion_vehiculo", updatable = false, length = 200)
    private String descripcionVehiculo;

    @Column(name = "codigo_ot", updatable = false, length = 20)
    private String codigoOt;

    // ----- Importes -----

    @Column(name = "base_imponible", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @Column(name = "total_iva", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalIva;

    @Column(name = "total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal total;

    // ----- Cadena de huellas -----

    /** Posicion en el registro global de facturacion; da el orden de la cadena. */
    @Column(name = "numero_registro", nullable = false, updatable = false)
    private Long numeroRegistro;

    /** Huella de la factura anterior, o {@link #HUELLA_GENESIS} en la primera. */
    @Column(name = "huella_anterior", nullable = false, updatable = false, length = 64)
    private String huellaAnterior;

    @Column(name = "huella", nullable = false, updatable = false, length = 64)
    private String huella;

    /** Texto canonico exacto sobre el que se calculo la huella. Permite reverificarla. */
    @Column(name = "cadena_huella", nullable = false, updatable = false, columnDefinition = "text")
    private String cadenaHuella;

    @Column(name = "algoritmo_huella", nullable = false, updatable = false, length = 20)
    private String algoritmoHuella;

    @Column(name = "qr_contenido", updatable = false, columnDefinition = "text")
    private String qrContenido;

    // ----- Software emisor -----

    @Column(name = "software_nombre", nullable = false, updatable = false, length = 100)
    private String softwareNombre;

    @Column(name = "software_version", nullable = false, updatable = false, length = 30)
    private String softwareVersion;

    @Column(name = "software_nif", updatable = false, length = 20)
    private String softwareNif;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    // ----- Detalle -----

    @OneToMany(mappedBy = "factura", cascade = CascadeType.PERSIST)
    @OrderBy("numeroLinea ASC")
    private List<LineaFactura> lineas = new ArrayList<>();

    @OneToMany(mappedBy = "factura", cascade = CascadeType.PERSIST)
    @OrderBy("porcentajeIva ASC")
    private List<DesgloseIvaFactura> desgloseIva = new ArrayList<>();

    public List<LineaFactura> getLineas() {
        return Collections.unmodifiableList(lineas);
    }

    public List<DesgloseIvaFactura> getDesgloseIva() {
        return Collections.unmodifiableList(desgloseIva);
    }
}
