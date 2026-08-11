package com.motorsport19.taller.agenda.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.moto.domain.Moto;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cita de entrada al taller.
 *
 * <p><b>Una cita no es una orden de trabajo.</b> Es un compromiso de que una moto
 * va a entrar, y como tal se mueve de dia y se cancela con normalidad. La OT nace
 * cuando la moto esta fisicamente en el taller: por eso son entidades distintas y
 * no un estado mas de la OT. Una orden que se pudiera mover de fecha o borrar
 * romperia la numeracion correlativa y el historial.
 *
 * <p>La moto puede no estar dada de alta: media agenda se coge por telefono de
 * gente que llama por primera vez. En ese caso se apunta el contacto a mano, y al
 * atender la cita ya se crean cliente y moto de verdad.
 */
@Entity
@Table(name = "cita")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cita extends EntidadAuditable {

    private static final BigDecimal DURACION_MAXIMA = new BigDecimal("24");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    /** Horas que se espera que la moto ocupe puesto. Alimenta la carga del dia. */
    @Column(name = "duracion_estimada", nullable = false, precision = 5, scale = 2)
    private BigDecimal duracionEstimada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moto_id")
    private Moto moto;

    /**
     * Cliente ya dado de alta cuando la moto todavia no tiene ficha.
     *
     * <p>Es el caso del de siempre que llama trayendo otra moto: se le reconoce
     * y no hay que volver a teclearle nombre y telefono. Si hay moto, manda el
     * cliente de la moto y este campo sobra.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    // Solo se usan cuando no hay ni moto ni cliente en el sistema.
    @Column(name = "contacto_nombre", length = 150)
    private String contactoNombre;

    @Column(name = "contacto_telefono", length = 30)
    private String contactoTelefono;

    @Column(name = "descripcion_moto", length = 150)
    private String descripcionMoto;

    @Column(name = "motivo", nullable = false, columnDefinition = "text")
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id")
    private Usuario tecnico;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoCita estado;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    /** OT que nacio de esta cita. Solo se rellena al atenderla. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_trabajo_id")
    private OrdenTrabajo ordenTrabajo;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    // ------------------------------------------------------------------
    // Creacion
    // ------------------------------------------------------------------

    /**
     * Aparta un hueco en la agenda.
     *
     * @param moto moto del sistema, o nulo si el cliente aun no esta dado de alta
     */
    public static Cita agendar(Instant fechaHora, BigDecimal duracionEstimada, Moto moto,
                               Cliente cliente, String contactoNombre, String contactoTelefono,
                               String descripcionMoto, String motivo, Usuario tecnico,
                               String observaciones) {
        Cita cita = new Cita();
        cita.aplicarDatos(fechaHora, duracionEstimada, moto, cliente, contactoNombre, contactoTelefono,
                descripcionMoto, motivo, tecnico, observaciones);
        cita.estado = EstadoCita.PENDIENTE;
        return cita;
    }

    // ------------------------------------------------------------------
    // Modificacion
    // ------------------------------------------------------------------

    /** Cambia los datos de la cita. Solo mientras siga viva. */
    public void actualizar(Instant fechaHora, BigDecimal duracionEstimada, Moto moto,
                           Cliente cliente, String contactoNombre, String contactoTelefono,
                           String descripcionMoto, String motivo, Usuario tecnico,
                           String observaciones) {
        exigirViva();
        aplicarDatos(fechaHora, duracionEstimada, moto, cliente, contactoNombre, contactoTelefono,
                descripcionMoto, motivo, tecnico, observaciones);
    }

    /**
     * Mueve la cita de fecha sin tocar nada mas.
     *
     * <p>Va aparte de {@link #actualizar} porque es lo que mas pasa —el cliente
     * llama para cambiar el dia— y no hay que obligar a reenviar la ficha entera.
     */
    public void reprogramar(Instant nuevaFechaHora) {
        exigirViva();
        this.fechaHora = exigirFecha(nuevaFechaHora);
    }

    public void confirmar() {
        cambiarEstado(EstadoCita.CONFIRMADA);
    }

    /**
     * La moto ha entrado: la cita queda cerrada y enlazada con su orden.
     *
     * <p>Es la unica forma de llegar a ATENDIDA, y exige la OT: la base de datos
     * tiene una restriccion que impide que una cosa vaya sin la otra.
     */
    public void atender(OrdenTrabajo orden) {
        if (orden == null) {
            throw new ReglaNegocioException(
                    "Para dar por atendida una cita hay que abrir su orden de trabajo.");
        }
        cambiarEstado(EstadoCita.ATENDIDA);
        this.ordenTrabajo = orden;
    }

    public void cancelar(String motivo) {
        cambiarEstado(EstadoCita.CANCELADA);
        this.motivoCancelacion = textoONulo(motivo);
    }

    /** El cliente no aparecio. Se distingue de cancelar: el hueco se perdio. */
    public void marcarNoPresentado(String motivo) {
        cambiarEstado(EstadoCita.NO_PRESENTADO);
        this.motivoCancelacion = textoONulo(motivo);
    }

    // ------------------------------------------------------------------
    // Consultas de dominio
    // ------------------------------------------------------------------

    /** Solo lo que sigue vivo cuenta para la carga del dia. */
    public boolean ocupaAgenda() {
        return estado.ocupaAgenda();
    }

    /** Como se llama a quien viene, este o no dado de alta. */
    public String nombreDeContacto() {
        Cliente conFicha = clienteConFicha();
        return conFicha != null ? conFicha.nombreCompleto() : contactoNombre;
    }

    /** Telefono al que llamar si hay que mover la cita. */
    public String telefonoDeContacto() {
        Cliente conFicha = clienteConFicha();
        if (conFicha != null && conFicha.getTelefono() != null) {
            return conFicha.getTelefono();
        }
        return contactoTelefono;
    }

    /** El cliente que tiene ficha: el de la moto si la hay, o el elegido a mano. */
    public Cliente clienteConFicha() {
        if (moto != null && moto.getCliente() != null) {
            return moto.getCliente();
        }
        return cliente;
    }

    /** Como reconocer la moto cuando entre por la puerta. */
    public String moto() {
        return moto != null ? moto.descripcion() : descripcionMoto;
    }

    // ------------------------------------------------------------------

    private void aplicarDatos(Instant fechaHora, BigDecimal duracionEstimada, Moto moto,
                              Cliente cliente, String contactoNombre, String contactoTelefono,
                              String descripcionMoto, String motivo, Usuario tecnico,
                              String observaciones) {
        this.fechaHora = exigirFecha(fechaHora);
        this.duracionEstimada = exigirDuracion(duracionEstimada);

        if (textoONulo(motivo) == null) {
            throw new ReglaNegocioException("Hay que apuntar a que viene la moto.");
        }

        this.moto = moto;
        // Si hay moto, el cliente sale de ella: guardar otro aparte solo daria
        // pie a que digan cosas distintas.
        this.cliente = moto != null ? null : cliente;
        this.contactoNombre = textoONulo(contactoNombre);
        this.contactoTelefono = textoONulo(contactoTelefono);
        this.descripcionMoto = textoONulo(descripcionMoto);

        // Hay que saber a quien llamar: por la moto, por el cliente, o a mano.
        // Una cita sin ninguna de las tres cosas es un hueco ocupado que nadie
        // sabe de quien es.
        if (moto == null && cliente == null
                && (this.contactoNombre == null || this.contactoTelefono == null)) {
            throw new ReglaNegocioException(
                    "Elija la moto o el cliente. Si no estan dados de alta, apunte al menos nombre "
                    + "y telefono de quien la trae.");
        }
        if (cliente != null && !cliente.isActivo()) {
            throw new ConflictoException(
                    "El cliente %s esta dado de baja: no se le pueden dar citas."
                            .formatted(cliente.nombreCompleto()));
        }
        if (moto != null && !moto.isActivo()) {
            throw new ConflictoException(
                    "La moto %s esta dada de baja: no se le pueden dar citas."
                            .formatted(moto.getMatricula()));
        }

        this.motivo = textoONulo(motivo);
        this.tecnico = tecnico;
        this.observaciones = textoONulo(observaciones);
    }

    private void cambiarEstado(EstadoCita destino) {
        if (!estado.puedeTransitarA(destino)) {
            throw new ConflictoException(
                    "Una cita %s no puede pasar a %s."
                            .formatted(estado.getDescripcion().toLowerCase(),
                                    destino.getDescripcion().toLowerCase()));
        }
        this.estado = destino;
    }

    private void exigirViva() {
        if (estado.esTerminal()) {
            throw new ConflictoException(
                    "Esta cita esta %s y ya no admite cambios."
                            .formatted(estado.getDescripcion().toLowerCase()));
        }
    }

    private static Instant exigirFecha(Instant fechaHora) {
        if (fechaHora == null) {
            throw new ReglaNegocioException("La cita necesita fecha y hora.");
        }
        return fechaHora;
    }

    private static BigDecimal exigirDuracion(BigDecimal duracion) {
        if (duracion == null || duracion.signum() <= 0) {
            throw new ReglaNegocioException("La duracion estimada tiene que ser mayor que cero.");
        }
        if (duracion.compareTo(DURACION_MAXIMA) > 0) {
            throw new ReglaNegocioException(
                    "Una cita no puede durar mas de 24 horas. Si el trabajo es de varios dias, "
                    + "apunte solo la entrada.");
        }
        return duracion;
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
