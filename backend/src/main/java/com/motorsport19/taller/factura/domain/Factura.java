package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.common.error.ReglaNegocioException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factura emitida.
 *
 * <p><b>Inmutable.</b> Una vez insertada no se edita ni se borra jamas: los
 * triggers de PostgreSQL rechazan UPDATE y DELETE sobre esta tabla, sobre sus
 * lineas y sobre su desglose de IVA. Toda correccion se hace emitiendo una
 * factura rectificativa que apunta a la original mediante
 * {@link #facturaRectificada}.
 *
 * <p>Esta clase refuerza lo mismo desde Java: se construye entera de una vez con
 * {@link #emitir}, todos los campos son no actualizables y no existe ningun
 * metodo que cambie su estado despues.
 *
 * <p>La factura se autocontiene: datos fiscales de emisor y receptor, matricula y
 * lineas se COPIAN en el momento de la emision. Se puede leer integra dentro de
 * veinte anos aunque el cliente, la moto o el catalogo ya no existan.
 */
@Entity
@Table(name = "factura")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Factura {

    /** Huella de partida de la cadena: 64 ceros. */
    public static final String HUELLA_GENESIS = CalculadoraHuella.HUELLA_GENESIS;

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
            @AttributeOverride(name = "nif",          column = @Column(name = "receptor_nif",       updatable = false, length = 20)),
            @AttributeOverride(name = "direccion",    column = @Column(name = "receptor_direccion", updatable = false, length = 200)),
            @AttributeOverride(name = "codigoPostal", column = @Column(name = "receptor_cp",        updatable = false, length = 10)),
            @AttributeOverride(name = "ciudad",       column = @Column(name = "receptor_ciudad",    updatable = false, length = 100)),
            @AttributeOverride(name = "provincia",    column = @Column(name = "receptor_provincia", updatable = false, length = 100)),
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

    /**
     * Factura simplificada: identifica a quien la emite, no a quien la recibe.
     *
     * <p>Es el antiguo tique. Se usa por debajo del limite que fije la
     * configuracion y solo cuando el cliente no tiene ficha fiscal completa; por
     * encima de ese importe hay que emitir factura completa aunque el cliente no
     * la quiera, porque la obligacion nace del importe y no de que la pida.
     */
    @Column(name = "simplificada", nullable = false, updatable = false)
    private boolean simplificada;

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

    // ==================================================================
    // Emision
    // ==================================================================

    /**
     * Construye la factura completa: cabecera, lineas copiadas, desglose de IVA y
     * huella encadenada.
     *
     * <p>Se hace todo de una vez y no en pasos sucesivos porque la huella cubre
     * los totales: si se pudiera anadir una linea despues, la huella dejaria de
     * corresponder al contenido.
     *
     * @param huellaAnterior huella de la factura precedente en el registro, o
     *                       {@link #HUELLA_GENESIS} si es la primera
     */
    public static Factura emitir(DatosEmision datos, List<LineaAFacturar> lineasAFacturar,
                                 String huellaAnterior) {
        validar(datos, lineasAFacturar, huellaAnterior);

        Factura factura = new Factura();
        factura.serie = datos.serie();
        factura.serieCodigo = datos.serie().getCodigo();
        factura.ejercicio = datos.serie().getEjercicio();
        factura.numero = datos.numero();
        factura.tipo = datos.tipo();

        factura.ordenTrabajo = datos.ordenTrabajo();
        factura.facturaRectificada = datos.facturaRectificada();
        factura.tipoRectificativa = datos.tipoRectificativa();
        factura.motivoRectificacion = datos.motivoRectificacion();

        factura.fechaEmision = datos.fechaEmision();
        factura.fechaOperacion = datos.fechaOperacion();
        factura.timestampEmision = datos.timestampEmision();

        factura.simplificada = datos.simplificada();
        factura.emisor = datos.emisor();
        factura.receptor = datos.receptor();
        factura.datosReceptor = datos.datosReceptor();

        factura.matricula = datos.matricula();
        factura.descripcionVehiculo = datos.descripcionVehiculo();
        factura.codigoOt = datos.codigoOt();

        factura.softwareNombre = datos.softwareNombre();
        factura.softwareVersion = datos.softwareVersion();
        factura.softwareNif = datos.softwareNif();

        factura.numeroRegistro = datos.numeroRegistro();
        factura.algoritmoHuella = CalculadoraHuella.ALGORITMO;
        factura.createdAt = datos.timestampEmision();
        factura.createdBy = datos.creadoPor();

        factura.copiarLineas(lineasAFacturar);
        factura.calcularTotalesYDesglose(lineasAFacturar);
        factura.sellar(huellaAnterior, datos.urlVerificacionQr());

        return factura;
    }

    // ==================================================================
    // Consultas
    // ==================================================================

    public List<LineaFactura> getLineas() {
        return Collections.unmodifiableList(lineas);
    }

    public List<DesgloseIvaFactura> getDesgloseIva() {
        return Collections.unmodifiableList(desgloseIva);
    }

    /** Numero visible; util antes de que la base de datos genere la columna. */
    public String numeroVisible() {
        return numeroCompleto != null
                ? numeroCompleto
                : "%s/%d/%06d".formatted(serieCodigo, ejercicio, numero);
    }

    public boolean esRectificativa() {
        return tipo == TipoFactura.RECTIFICATIVA;
    }

    /** Recalcula la huella desde la cadena almacenada y la compara con la guardada. */
    public boolean huellaEsCoherente() {
        return CalculadoraHuella.huellaCoincide(cadenaHuella, huella);
    }

    /**
     * Comprueba que los datos de la fila siguen siendo los que se sellaron.
     *
     * <p>Es la comprobacion que de verdad cierra el circulo. {@link #huellaEsCoherente()}
     * solo dice que la huella corresponde a la cadena canonica guardada; si alguien
     * modificase el total de la fila sin tocar esa cadena, la huella seguiria
     * cuadrando consigo misma. Aqui se vuelve a componer la cadena canonica a
     * partir de los valores ACTUALES y se compara con la que se sello: cualquier
     * cambio en NIF, numero, fecha, tipo, cuota, total o enlace la delata.
     */
    public boolean contenidoCoincideConElSello() {
        if (cadenaHuella == null) {
            return false;
        }
        String recalculada = CalculadoraHuella.cadenaCanonica(
                emisor.getNif(), numeroVisible(), fechaEmision, tipo, totalIva, total,
                huellaAnterior, timestampEmision);
        return recalculada.equals(cadenaHuella);
    }

    /** Comprueba que enlaza con la huella que se le pasa. */
    public boolean enlazaCon(String huellaEsperada) {
        return huellaAnterior != null && huellaAnterior.equalsIgnoreCase(huellaEsperada);
    }

    // ==================================================================

    private void copiarLineas(List<LineaAFacturar> lineasAFacturar) {
        int numeroLinea = 1;
        for (LineaAFacturar linea : lineasAFacturar) {
            lineas.add(LineaFactura.copiar(this, numeroLinea++, linea));
        }
    }

    /**
     * Calcula totales y desglose SUMANDO las lineas, nunca recalculando sobre el
     * total.
     *
     * <p>De esta forma el desglose cuadra al centimo con las lineas y con la
     * cabecera, que es exactamente lo que comprueba el trigger diferido de la
     * base de datos al hacer commit.
     */
    private void calcularTotalesYDesglose(List<LineaAFacturar> lineasAFacturar) {
        // LinkedHashMap para que el desglose salga en un orden estable.
        Map<BigDecimal, ImporteLinea> porTipo = new LinkedHashMap<>();
        Map<BigDecimal, String> codigoPorTipo = new LinkedHashMap<>();
        ImporteLinea acumulado = ImporteLinea.cero();

        for (LineaAFacturar linea : lineasAFacturar) {
            ImporteLinea importe = linea.importes();
            acumulado = acumulado.mas(importe);
            porTipo.merge(linea.porcentajeIva(), importe, ImporteLinea::mas);
            codigoPorTipo.putIfAbsent(linea.porcentajeIva(), linea.tipoIva());
        }

        this.baseImponible = acumulado.baseImponible();
        this.totalIva = acumulado.cuotaIva();
        this.total = acumulado.total();

        porTipo.forEach((porcentaje, importe) -> desgloseIva.add(DesgloseIvaFactura.de(
                this, codigoPorTipo.get(porcentaje), porcentaje,
                importe.baseImponible(), importe.cuotaIva())));
    }

    /** Encadena la factura con la anterior y compone el contenido del QR. */
    private void sellar(String huellaAnterior, String urlVerificacion) {
        this.huellaAnterior = huellaAnterior;
        this.cadenaHuella = CalculadoraHuella.cadenaCanonica(
                emisor.getNif(), numeroVisible(), fechaEmision, tipo, totalIva, total,
                huellaAnterior, timestampEmision);
        this.huella = CalculadoraHuella.calcular(cadenaHuella);
        this.qrContenido = componerQr(urlVerificacion);
    }

    private String componerQr(String urlVerificacion) {
        if (urlVerificacion == null || urlVerificacion.isBlank()) {
            return null;
        }
        return "%s?nif=%s&numserie=%s&fecha=%s&importe=%s".formatted(
                urlVerificacion,
                emisor.getNif(),
                numeroVisible(),
                fechaEmision.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                total.toPlainString());
    }

    private static void validar(DatosEmision datos, List<LineaAFacturar> lineas, String huellaAnterior) {
        if (lineas == null || lineas.isEmpty()) {
            throw new ReglaNegocioException("No se puede emitir una factura sin lineas.");
        }
        if (huellaAnterior == null || !huellaAnterior.matches("[0-9a-fA-F]{64}")) {
            throw new ReglaNegocioException(
                    "La huella anterior debe ser un SHA-256 en hexadecimal de 64 caracteres.");
        }
        if (datos.numero() <= 0) {
            throw new ReglaNegocioException("El numero de factura debe ser positivo.");
        }
        if (datos.numeroRegistro() <= 0) {
            throw new ReglaNegocioException("La posicion en el registro debe ser positiva.");
        }
        if (datos.tipo() != datos.serie().getTipo()) {
            throw new ReglaNegocioException(
                    "Una factura de tipo %s no puede emitirse en la serie %s, que es de tipo %s."
                            .formatted(datos.tipo(), datos.serie().getCodigo(), datos.serie().getTipo()));
        }
        if (datos.tipo() == TipoFactura.RECTIFICATIVA) {
            if (datos.facturaRectificada() == null) {
                throw new ReglaNegocioException(
                        "Una factura rectificativa debe indicar a que factura rectifica.");
            }
            if (datos.tipoRectificativa() == null) {
                throw new ReglaNegocioException(
                        "Una factura rectificativa debe indicar si es por sustitucion o por diferencias.");
            }
            if (datos.motivoRectificacion() == null || datos.motivoRectificacion().isBlank()) {
                throw new ReglaNegocioException(
                        "Una factura rectificativa debe explicar el motivo de la rectificacion.");
            }
        } else if (datos.facturaRectificada() != null) {
            throw new ReglaNegocioException(
                    "Una factura ordinaria no puede rectificar a otra: emita una rectificativa.");
        }
    }
}
