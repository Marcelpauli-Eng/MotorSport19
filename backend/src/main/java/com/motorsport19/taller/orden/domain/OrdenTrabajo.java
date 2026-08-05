package com.motorsport19.taller.orden.domain;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
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
import java.util.Optional;

/**
 * Orden de trabajo: la unidad de trabajo del taller, desde que entra la moto
 * hasta que se entrega.
 *
 * <p>Todos los cambios de estado pasan por {@link #cambiarEstado}, que valida la
 * transicion contra {@link EstadoOT} y deja constancia en el historial. No hay
 * ninguna forma de asignar el estado directamente.
 *
 * <p>Una OT en {@link EstadoOT#ENTREGADA} es <b>inmutable</b>: ademas de la
 * maquina de estados, los triggers de la base de datos rechazan cualquier
 * modificacion de la cabecera y de sus lineas.
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

    // ==================================================================
    // Apertura
    // ==================================================================

    /**
     * Abre una orden de trabajo.
     *
     * <p>El numero correlativo y la tarifa/hora los aporta el servicio: el
     * numero sale de un contador transaccional y la tarifa se congela aqui, de
     * modo que si manana sube la tarifa del taller esta OT mantiene la pactada.
     */
    public static OrdenTrabajo abrir(Integer ejercicio, Integer numero, Moto moto, BigDecimal tarifaHora,
                                     int kmEntrada, String problemaReportado, LocalDate fechaEstimadaSalida,
                                     Usuario tecnico, String observaciones, Usuario usuarioApertura) {
        if (moto == null) {
            throw new ReglaNegocioException("La orden de trabajo debe referirse a una moto.");
        }
        if (!moto.isActivo()) {
            throw new ConflictoException(
                    "La moto %s esta dada de baja: no se le pueden abrir ordenes de trabajo."
                            .formatted(moto.getMatricula()));
        }
        if (textoONulo(problemaReportado) == null) {
            throw new ReglaNegocioException(
                    "Hay que describir el problema que reporta el cliente al abrir la orden.");
        }
        if (kmEntrada < 0) {
            throw new ReglaNegocioException("El kilometraje de entrada no puede ser negativo.");
        }
        if (tarifaHora == null || tarifaHora.signum() < 0) {
            throw new ReglaNegocioException("La tarifa por hora no puede ser negativa.");
        }

        OrdenTrabajo orden = new OrdenTrabajo();
        orden.ejercicio = ejercicio;
        orden.numero = numero;
        orden.moto = moto;
        // Instantanea del propietario: si la moto se vende manana, esta OT sigue
        // perteneciendo a quien la trajo.
        orden.cliente = moto.getCliente();
        orden.fechaEntrada = Instant.now();
        orden.fechaEstimadaSalida = fechaEstimadaSalida;
        orden.kmEntrada = kmEntrada;
        orden.problemaReportado = textoONulo(problemaReportado);
        orden.tecnico = tecnico;
        orden.tarifaHora = tarifaHora;
        orden.observaciones = textoONulo(observaciones);
        orden.estado = EstadoOT.RECIBIDA;

        orden.historialEstados.add(
                CambioEstadoOT.apertura(orden, usuarioApertura, "Entrada de la moto en el taller"));

        return orden;
    }

    // ==================================================================
    // Transiciones de estado
    // ==================================================================

    public void iniciarDiagnostico(Usuario tecnico, Usuario usuario) {
        cambiarEstado(EstadoOT.EN_DIAGNOSTICO, usuario, null);
        if (tecnico != null) {
            this.tecnico = tecnico;
        }
    }

    /**
     * Pasa a presupuestada.
     *
     * <p>Exige diagnostico y al menos una linea: un presupuesto sin conceptos no
     * es un presupuesto, y el cliente no puede aprobar lo que no ve.
     */
    public void presupuestar(Usuario usuario) {
        if (textoONulo(diagnostico) == null) {
            throw new ReglaNegocioException(
                    "No se puede presupuestar la orden %s sin haber registrado el diagnostico."
                            .formatted(codigoVisible()));
        }
        if (lineas.isEmpty()) {
            throw new ReglaNegocioException(
                    "No se puede presupuestar la orden %s sin ninguna linea de trabajo."
                            .formatted(codigoVisible()));
        }
        cambiarEstado(EstadoOT.PRESUPUESTADA, usuario, null);
        this.fechaPresupuesto = Instant.now();
    }

    public void aprobar(String aprobadoPor, Usuario usuario) {
        cambiarEstado(EstadoOT.APROBADA, usuario,
                aprobadoPor == null ? null : "Aprobado por " + aprobadoPor);
        this.fechaAprobacion = Instant.now();
        this.aprobadoPor = textoONulo(aprobadoPor) != null
                ? textoONulo(aprobadoPor)
                : cliente.nombreCompleto();
    }

    /** El cliente no acepta el presupuesto: la moto vuelve sin reparar. */
    public void rechazar(String motivo, Usuario usuario) {
        if (textoONulo(motivo) == null) {
            throw new ReglaNegocioException(
                    "Hay que indicar por que el cliente rechaza el presupuesto de la orden %s."
                            .formatted(codigoVisible()));
        }
        cambiarEstado(EstadoOT.RECHAZADA, usuario, motivo);
        this.motivoRechazo = textoONulo(motivo);
        this.fechaRealSalida = Instant.now();
    }

    /**
     * Entra en reparacion. Lo invoca el servicio DESPUES de haber consumido las
     * piezas del almacen, porque solo entonces se sabe si hay material para
     * trabajar.
     */
    public void entrarEnReparacion(Usuario usuario, String motivo) {
        cambiarEstado(EstadoOT.EN_REPARACION, usuario, motivo);
    }

    /** Se bloquea por falta de material. El motivo debe decir que falta. */
    public void bloquearPorFaltaDePiezas(String motivo, Usuario usuario) {
        if (textoONulo(motivo) == null) {
            throw new ReglaNegocioException(
                    "Hay que indicar que piezas faltan para bloquear la orden %s.".formatted(codigoVisible()));
        }
        cambiarEstado(EstadoOT.ESPERANDO_PIEZAS, usuario, motivo);
    }

    public void marcarLista(Usuario usuario) {
        cambiarEstado(EstadoOT.LISTA, usuario, null);
    }

    /**
     * Entrega al cliente. A partir de aqui la OT queda congelada: ni la maquina
     * de estados ni la base de datos admiten mas cambios.
     */
    public void entregar(Usuario usuario) {
        cambiarEstado(EstadoOT.ENTREGADA, usuario, null);
        this.fechaRealSalida = Instant.now();
    }

    // ==================================================================
    // Contenido
    // ==================================================================

    public void registrarDiagnostico(String diagnostico) {
        exigirModificable();
        if (textoONulo(diagnostico) == null) {
            throw new ReglaNegocioException("El diagnostico no puede quedar vacio.");
        }
        this.diagnostico = textoONulo(diagnostico);
    }

    public void asignarTecnico(Usuario tecnico) {
        exigirModificable();
        this.tecnico = tecnico;
    }

    public void actualizarDatos(LocalDate fechaEstimadaSalida, String observaciones) {
        exigirModificable();
        this.fechaEstimadaSalida = fechaEstimadaSalida;
        this.observaciones = textoONulo(observaciones);
    }

    /** Anade horas de taller, valoradas a la tarifa congelada de esta OT. */
    public LineaOT anadirManoDeObra(String descripcion, BigDecimal horas, BigDecimal descuentoPct,
                                    String tipoIva, BigDecimal porcentajeIva) {
        exigirLineasEditables();
        LineaOT linea = LineaOT.manoDeObra(this, siguienteNumeroDeLinea(), descripcion, horas,
                tarifaHora, descuentoPct, tipoIva, porcentajeIva);
        lineas.add(linea);
        return linea;
    }

    /** Anade una pieza, congelando su precio de catalogo en el momento de anadirla. */
    public LineaOT anadirPieza(Pieza pieza, BigDecimal cantidad, BigDecimal descuentoPct,
                               BigDecimal porcentajeIva) {
        exigirLineasEditables();
        LineaOT linea = LineaOT.pieza(this, siguienteNumeroDeLinea(), pieza, cantidad, descuentoPct,
                porcentajeIva);
        lineas.add(linea);
        return linea;
    }

    public void quitarLinea(LineaOT linea) {
        exigirLineasEditables();
        if (!lineas.remove(linea)) {
            throw new ReglaNegocioException(
                    "La linea indicada no pertenece a la orden %s.".formatted(codigoVisible()));
        }
    }

    public Optional<LineaOT> buscarLinea(Long lineaId) {
        return lineas.stream().filter(l -> l.getId() != null && l.getId().equals(lineaId)).findFirst();
    }

    /** Lineas de tipo PIEZA, que son las que consumen almacen. */
    public List<LineaOT> lineasDePiezas() {
        return lineas.stream().filter(LineaOT::esDePieza).toList();
    }

    // ==================================================================
    // Consultas
    // ==================================================================

    public List<LineaOT> getLineas() {
        return Collections.unmodifiableList(lineas);
    }

    public List<CambioEstadoOT> getHistorialEstados() {
        return Collections.unmodifiableList(historialEstados);
    }

    /** Suma de las bases imponibles de las lineas. */
    public BigDecimal baseImponible() {
        return sumar(LineaOT::getBaseImponible);
    }

    public BigDecimal totalIva() {
        return sumar(LineaOT::getCuotaIva);
    }

    public BigDecimal total() {
        return sumar(LineaOT::getTotal);
    }

    public BigDecimal horasManoDeObra() {
        return lineas.stream()
                .filter(l -> !l.esDePieza())
                .map(LineaOT::getCantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String codigoVisible() {
        return codigo != null ? codigo : "OT-%d-%05d".formatted(ejercicio, numero);
    }

    public boolean puedeFacturarse() {
        return estado.permiteFacturar();
    }

    // ==================================================================

    /**
     * Unico punto por el que cambia el estado: valida la transicion y deja
     * constancia de quien la hizo y cuando.
     */
    private void cambiarEstado(EstadoOT nuevo, Usuario usuario, String motivo) {
        estado.exigirTransicionA(nuevo, codigoVisible());
        EstadoOT anterior = this.estado;
        this.estado = nuevo;
        historialEstados.add(CambioEstadoOT.transicion(this, anterior, nuevo, usuario, motivo));
    }

    /** Rechaza cualquier modificacion sobre una OT ya cerrada. */
    private void exigirModificable() {
        if (estado.esTerminal()) {
            throw new ConflictoException(
                    "La orden de trabajo %s esta %s y no admite modificaciones."
                            .formatted(codigoVisible(), estado.getDescripcion().toUpperCase()));
        }
    }

    private void exigirLineasEditables() {
        if (!estado.permiteEditarLineas()) {
            throw new ConflictoException(
                    ("No se pueden modificar las lineas de la orden %s en estado %s. "
                     + "Solo se editan mientras se diagnostica, se presupuesta o se repara.")
                            .formatted(codigoVisible(), estado.name()));
        }
    }

    private int siguienteNumeroDeLinea() {
        return lineas.stream()
                .mapToInt(LineaOT::getNumeroLinea)
                .max()
                .orElse(0) + 1;
    }

    private BigDecimal sumar(java.util.function.Function<LineaOT, BigDecimal> campo) {
        return lineas.stream()
                .map(campo)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
