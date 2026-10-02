package com.motorsport19.taller.solicitud.domain;

import com.motorsport19.taller.agenda.domain.Cita;
import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.orden.domain.ConLineas;
import com.motorsport19.taller.orden.domain.LineaImporte;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Cita o presupuesto que un cliente pide desde la web publica.
 *
 * <p><b>No es una cita ni un cliente.</b> Es lo que alguien escribio en un
 * formulario, y hasta que mostrador lo lee no se mezcla con los datos del
 * taller: al darle cita nace una {@link Cita} de verdad, y si no se descarta.
 * Asi un nombre mal escrito o un envio de spam no acaban en la agenda.
 */
@Entity
@Table(name = "solicitud_web")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitudWeb extends EntidadAuditable implements ConLineas<LineaPresupuestoWeb> {

    public static final int MAXIMO_FOTOS = 3;
    private static final Set<String> IDIOMAS = Set.of("es", "ca", "en", "fr");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** La pone la web: si reintenta el envio, la solicitud no se duplica. */
    @Column(name = "referencia", nullable = false, length = 40, updatable = false)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20, updatable = false)
    private TipoSolicitud tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoSolicitud estado;

    @Column(name = "recibida_en", nullable = false, updatable = false)
    private Instant recibidaEn;

    /** Idioma en que se relleno: en el que habra que contestar. */
    @Column(name = "idioma", nullable = false, length = 2, updatable = false)
    private String idioma;

    @Column(name = "nombre", nullable = false, length = 120, updatable = false)
    private String nombre;

    @Column(name = "telefono", nullable = false, length = 30, updatable = false)
    private String telefono;

    @Column(name = "email", length = 160, updatable = false)
    private String email;

    @Column(name = "marca", nullable = false, length = 60, updatable = false)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 60, updatable = false)
    private String modelo;

    @Column(name = "matricula", length = 20, updatable = false)
    private String matricula;

    /** Año de la moto, si lo dijo. */
    @Column(name = "anio", updatable = false)
    private Integer anio;

    @Column(name = "necesita", nullable = false, columnDefinition = "text", updatable = false)
    private String necesita;

    @Column(name = "fecha_preferida", updatable = false)
    private LocalDate fechaPreferida;

    @Column(name = "num_fotos", nullable = false, updatable = false)
    private short numFotos;

    /** El ultimo presupuesto que se le mando. */
    @Column(name = "presupuesto_importe", precision = 10, scale = 2)
    private BigDecimal presupuestoImporte;

    @Column(name = "presupuesto_detalle", columnDefinition = "text")
    private String presupuestoDetalle;

    @Enumerated(EnumType.STRING)
    @Column(name = "presupuesto_canal", length = 10)
    private CanalPresupuesto presupuestoCanal;

    @Column(name = "presupuestada_en")
    private Instant presupuestadaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presupuestada_por")
    private Usuario presupuestadaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id")
    private Cita cita;

    @Column(name = "nota", length = 500)
    private String nota;

    @Column(name = "atendida_en")
    private Instant atendidaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendida_por")
    private Usuario atendidaPor;

    /** Precio de la hora del presupuesto: el del taller al empezarlo. */
    @Column(name = "tarifa_hora", precision = 12, scale = 2)
    private BigDecimal tarifaHora;

    /** IVA impuesto a todo el presupuesto, o nulo si cada linea lleva el suyo. */
    @Column(name = "tipo_iva", length = 20)
    private String tipoIva;

    /** La orden que se abrio cuando el cliente acepto el presupuesto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_trabajo_id")
    private OrdenTrabajo ordenTrabajo;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroLinea ASC")
    private List<LineaPresupuestoWeb> lineas = new ArrayList<>();

    // ------------------------------------------------------------------
    // Llegada
    // ------------------------------------------------------------------

    public static SolicitudWeb recibir(String referencia, TipoSolicitud tipo, String idioma,
                                       String nombre, String telefono, String email,
                                       String marca, String modelo, String matricula,
                                       String necesita, LocalDate fechaPreferida, int numFotos) {
        SolicitudWeb s = new SolicitudWeb();
        s.referencia = obligatorio(referencia, "La solicitud llego sin referencia.");
        if (tipo == null) {
            throw new ReglaNegocioException("La solicitud no dice si es cita o presupuesto.");
        }
        s.tipo = tipo;
        s.idioma = obligatorio(idioma, "La solicitud llego sin idioma.");
        if (!IDIOMAS.contains(s.idioma)) {
            throw new ReglaNegocioException("Idioma de la solicitud no reconocido: " + s.idioma);
        }
        s.nombre = obligatorio(nombre, "Falta el nombre de quien pide la solicitud.");
        s.telefono = obligatorio(telefono, "Falta el telefono de contacto.");
        s.email = textoONulo(email);
        s.marca = obligatorio(marca, "Falta la marca de la moto.");
        s.modelo = obligatorio(modelo, "Falta el modelo de la moto.");
        s.matricula = textoONulo(matricula);
        s.necesita = obligatorio(necesita, "Falta lo que necesita la moto.");
        // Un presupuesto no va a un dia concreto: si la web mandara fecha, sobra.
        s.fechaPreferida = tipo == TipoSolicitud.CITA ? fechaPreferida : null;
        if (numFotos < 0 || numFotos > MAXIMO_FOTOS) {
            throw new ReglaNegocioException("Una solicitud trae como mucho %d fotos.".formatted(MAXIMO_FOTOS));
        }
        s.numFotos = (short) numFotos;
        s.estado = EstadoSolicitud.PENDIENTE;
        s.recibidaEn = Instant.now();
        return s;
    }

    /**
     * Apunta el año que dejo en la web. Es opcional: uno que no puede ser se
     * ignora en vez de rechazar la solicitud entera, que se perderia por un
     * dato que no hace falta para contestarle.
     */
    public void anotarAnio(Integer anio) {
        boolean posible = anio != null && anio >= 1885 && anio <= Year.now().getValue() + 1;
        this.anio = posible ? anio : null;
    }

    // ------------------------------------------------------------------
    // Presupuesto
    // ------------------------------------------------------------------

    private static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("99999999.99");

    /**
     * Apunta el presupuesto que se le manda. La solicitud sigue abierta: queda a
     * la espera de que el cliente conteste, y si lo acepta se le da cita.
     *
     * <p>Se puede volver a mandar corregido; se queda el ultimo. El mensaje en si
     * lo manda quien lo atiende, desde su WhatsApp o su correo: aqui solo queda
     * constancia de que se mando, cuanto y por donde.
     */
    public void enviarPresupuesto(BigDecimal importe, String detalle, CanalPresupuesto canal,
                                  Usuario quienLoHace) {
        exigirAbierta();
        if (importe == null || importe.signum() <= 0) {
            throw new ReglaNegocioException("El presupuesto necesita un importe mayor que cero.");
        }
        if (importe.compareTo(IMPORTE_MAXIMO) > 0 || importe.scale() > 2) {
            throw new ReglaNegocioException("Importe de presupuesto no valido.");
        }
        if (canal == null) {
            throw new ReglaNegocioException("Falta por donde se manda el presupuesto.");
        }
        if (canal == CanalPresupuesto.EMAIL && email == null) {
            throw new ReglaNegocioException("El cliente no dejo email: mandale el presupuesto por WhatsApp.");
        }
        String texto = textoONulo(detalle);
        if (texto != null && texto.length() > 3000) {
            throw new ReglaNegocioException("El detalle del presupuesto no puede superar los 3000 caracteres.");
        }
        this.presupuestoImporte = importe;
        this.presupuestoDetalle = texto;
        this.presupuestoCanal = canal;
        this.presupuestadaEn = Instant.now();
        this.presupuestadaPor = quienLoHace;
        this.estado = EstadoSolicitud.PRESUPUESTADA;
    }

    /**
     * Manda el presupuesto montado con lineas: el importe es su total.
     *
     * <p>A partir de aqui ya no se tocan las lineas: es lo que ha visto el
     * cliente. Para cambiarlo se reescribe.
     */
    public void enviarPresupuesto(CanalPresupuesto canal, Usuario quienLoHace) {
        if (lineas.isEmpty()) {
            throw new ReglaNegocioException("El presupuesto no tiene ninguna linea todavia.");
        }
        enviarPresupuesto(total(), null, canal, quienLoHace);
    }

    /**
     * El cliente no lo acepta tal cual: vuelve a pendiente para corregirlo y
     * mandarlo otra vez. Las lineas se quedan donde estaban.
     */
    public void reescribirPresupuesto() {
        if (estado != EstadoSolicitud.PRESUPUESTADA) {
            throw new ConflictoException("Solo se reescribe un presupuesto que ya se ha mandado.");
        }
        this.estado = EstadoSolicitud.PENDIENTE;
    }

    /** El cliente acepta: la solicitud se cierra con la orden que se le ha abierto. */
    public void aceptarPresupuesto(OrdenTrabajo orden, Usuario quienLoHace) {
        if (estado != EstadoSolicitud.PRESUPUESTADA) {
            throw new ConflictoException("Solo se acepta un presupuesto que ya se ha mandado.");
        }
        if (orden == null) {
            throw new ReglaNegocioException("Falta la orden de trabajo que se le ha abierto.");
        }
        cerrar(EstadoSolicitud.ATENDIDA, "Presupuesto aceptado: orden " + orden.codigoVisible(), quienLoHace);
        this.ordenTrabajo = orden;
    }

    /** El cliente no lo quiere. */
    public void rechazarPresupuesto(String motivo, Usuario quienLoHace) {
        String texto = textoONulo(motivo);
        cerrar(EstadoSolicitud.DESCARTADA,
                texto == null ? "Presupuesto rechazado." : "Presupuesto rechazado: " + texto, quienLoHace);
    }

    // ------------------------------------------------------------------
    // Lineas del presupuesto
    // ------------------------------------------------------------------

    /** Se monta mientras esta pendiente; mandado, ya es lo que ha visto el cliente. */
    public boolean permiteEditarLineas() {
        return estado == EstadoSolicitud.PENDIENTE;
    }

    /** Al empezar el presupuesto se congela el precio de la hora del taller. */
    public void empezarPresupuesto(BigDecimal tarifaDelTaller) {
        if (tarifaHora == null) {
            this.tarifaHora = tarifaDelTaller;
        }
    }

    public void cambiarTarifaHora(BigDecimal nuevaTarifa) {
        exigirLineasEditables();
        if (nuevaTarifa == null || nuevaTarifa.signum() <= 0) {
            throw new ReglaNegocioException("El precio de la hora tiene que ser mayor que cero.");
        }
        this.tarifaHora = nuevaTarifa;
        lineas.stream().filter(LineaImporte::esManoDeObra).forEach(l -> l.repreciarManoDeObra(nuevaTarifa));
    }

    public LineaPresupuestoWeb anadirManoDeObra(String descripcion, BigDecimal horas, BigDecimal descuentoPct,
                                                String tipoIva, BigDecimal porcentajeIva) {
        exigirLineasEditables();
        return anadir(LineaPresupuestoWeb.manoDeObra(this, siguienteNumeroDeLinea(), descripcion, horas,
                tarifaHora, descuentoPct, tipoIva, porcentajeIva));
    }

    @Override
    public LineaPresupuestoWeb anadirPieza(Pieza pieza, BigDecimal cantidad, BigDecimal descuentoPct,
                                           BigDecimal porcentajeIva) {
        exigirLineasEditables();
        return anadir(LineaPresupuestoWeb.pieza(this, siguienteNumeroDeLinea(), pieza, cantidad, descuentoPct,
                porcentajeIva));
    }

    @Override
    public LineaPresupuestoWeb anadirTasa(String concepto, BigDecimal cantidad, BigDecimal importe,
                                          String tipoIva, BigDecimal porcentajeIva) {
        exigirLineasEditables();
        return anadir(LineaPresupuestoWeb.tasa(this, siguienteNumeroDeLinea(), concepto, cantidad, importe,
                tipoIva, porcentajeIva));
    }

    public void cambiarPrecioDeManoDeObra(LineaPresupuestoWeb linea, BigDecimal precioUnitario) {
        exigirLineasEditables();
        linea.repreciarManoDeObra(precioUnitario);
    }

    public void cambiarCantidadDeLinea(LineaPresupuestoWeb linea, BigDecimal cantidad) {
        exigirLineasEditables();
        linea.cambiarCantidad(cantidad, null);
    }

    public void cambiarDescuentoDeLinea(LineaPresupuestoWeb linea, BigDecimal descuentoPct) {
        exigirLineasEditables();
        linea.cambiarDescuento(descuentoPct);
    }

    /** El «hazme un 10 % en todo»: pisa los descuentos de cada linea. */
    public void aplicarDescuentoGeneral(BigDecimal descuentoPct) {
        exigirLineasEditables();
        lineas.forEach(l -> l.cambiarDescuento(descuentoPct));
    }

    public void aplicarTipoIvaGeneral(String codigo, BigDecimal porcentaje) {
        exigirLineasEditables();
        this.tipoIva = codigo;
        lineas.forEach(l -> l.cambiarTipoIva(codigo, porcentaje));
    }

    @Override
    public void quitarLinea(LineaPresupuestoWeb linea) {
        exigirLineasEditables();
        if (!lineas.remove(linea)) {
            throw new ReglaNegocioException("Esa linea no es de este presupuesto.");
        }
    }

    public Optional<LineaPresupuestoWeb> buscarLinea(Long lineaId) {
        return lineas.stream().filter(l -> lineaId.equals(l.getId())).findFirst();
    }

    @Override
    public List<LineaPresupuestoWeb> getLineas() {
        return Collections.unmodifiableList(lineas);
    }

    public BigDecimal importeBruto() {
        return sumar(LineaImporte::importeBruto);
    }

    public BigDecimal totalDescuento() {
        return sumar(LineaImporte::importeDescuento);
    }

    public BigDecimal baseImponible() {
        return sumar(LineaImporte::getBaseImponible);
    }

    public BigDecimal totalIva() {
        return sumar(LineaImporte::getCuotaIva);
    }

    /** Total con IVA. Lo calcula la base de datos linea a linea. */
    public BigDecimal total() {
        return sumar(LineaImporte::getTotal);
    }

    public BigDecimal horasManoDeObra() {
        return lineas.stream().filter(LineaImporte::esManoDeObra).map(LineaImporte::getCantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LineaPresupuestoWeb anadir(LineaPresupuestoWeb linea) {
        lineas.add(linea);
        return linea;
    }

    private void exigirLineasEditables() {
        exigirAbierta();
        if (!permiteEditarLineas()) {
            throw new ConflictoException(
                    "Este presupuesto ya se ha mandado al cliente. Reescribelo para cambiarlo.");
        }
        if (tarifaHora == null) {
            throw new ConflictoException("El presupuesto no se ha empezado todavia.");
        }
    }

    private int siguienteNumeroDeLinea() {
        return lineas.stream().mapToInt(LineaImporte::getNumeroLinea).max().orElse(0) + 1;
    }

    private BigDecimal sumar(Function<LineaImporte, BigDecimal> campo) {
        return lineas.stream().map(campo).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------
    // Cierre
    // ------------------------------------------------------------------

    /** Se le ha dado cita en la agenda. */
    public void darCita(Cita cita, Usuario quienLoHace) {
        if (cita == null) {
            throw new ReglaNegocioException("Falta la cita que se le ha dado.");
        }
        cerrar(EstadoSolicitud.ATENDIDA, null, quienLoHace);
        this.cita = cita;
    }

    /** Resuelta por otra via: presupuesto dado por telefono, dudas contestadas... */
    public void marcarAtendida(String nota, Usuario quienLoHace) {
        cerrar(EstadoSolicitud.ATENDIDA, nota, quienLoHace);
    }

    /** Spam, duplicada, o el cliente ya no la quiere. */
    public void descartar(String nota, Usuario quienLoHace) {
        cerrar(EstadoSolicitud.DESCARTADA, nota, quienLoHace);
    }

    /**
     * Falla si ya se atendio o descarto. Presupuestada sigue abierta.
     *
     * <p>Dos puestos abriendo la misma solicitud a la vez es lo normal en
     * mostrador: el segundo tiene que enterarse de que ya esta hecha.
     */
    public void exigirAbierta() {
        if (!estado.abierta()) {
            throw new ConflictoException("Esta solicitud ya esta %s.".formatted(estado.getDescripcion().toLowerCase()));
        }
    }

    private void cerrar(EstadoSolicitud nuevo, String nota, Usuario quienLoHace) {
        exigirAbierta();
        String texto = textoONulo(nota);
        if (texto != null && texto.length() > 500) {
            throw new ReglaNegocioException("La nota no puede superar los 500 caracteres.");
        }
        this.estado = nuevo;
        this.nota = texto;
        this.atendidaEn = Instant.now();
        this.atendidaPor = quienLoHace;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /** Como se apunta la moto en una cita sin ficha: «Yamaha R6 · 1234ABC». */
    public String descripcionMoto() {
        String moto = marca + " " + modelo + (anio == null ? "" : " (" + anio + ")");
        return matricula == null ? moto : moto + " · " + matricula;
    }

    private static String obligatorio(String valor, String mensaje) {
        String texto = textoONulo(valor);
        if (texto == null) {
            throw new ReglaNegocioException(mensaje);
        }
        return texto;
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }
}
