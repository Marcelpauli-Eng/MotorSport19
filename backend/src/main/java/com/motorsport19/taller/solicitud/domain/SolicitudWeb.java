package com.motorsport19.taller.solicitud.domain;

import com.motorsport19.taller.agenda.domain.Cita;
import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

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
public class SolicitudWeb extends EntidadAuditable {

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
        String moto = marca + " " + modelo;
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
