package com.motorsport19.taller.fichaje.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.usuario.domain.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Una jornada de trabajo: cuando se empezo y cuando se termino.
 *
 * <p>El registro de jornada es obligatorio en Espana (art. 34.9 del Estatuto de
 * los Trabajadores), y eso decide como esta hecha esta clase. Lo que se guarda
 * son las <b>horas concretas</b>, no un total: el total se calcula, pero lo que
 * hay que poder ensenar es a que hora entro y a que hora salio cada persona.
 *
 * <p><b>Lo que se ficha no se sobrescribe nunca.</b> Si el administrador tiene
 * que corregir una jornada —alguien se fue sin fichar la salida, o fichó al
 * llegar a la oficina en vez de al entrar—, la correccion se guarda aparte,
 * junto con quien la hizo y por que. Un registro que se puede reescribir sin
 * dejar rastro no prueba nada, ni ante una inspeccion ni ante el propio
 * trabajador.
 *
 * <p>Y cada cambio deja su fila en {@link CambioFichaje}, con las horas que
 * habia antes: si se corrige dos veces, se ven las dos.
 */
@Entity
@Table(name = "fichaje")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fichaje extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Lo que quedo registrado al pulsar el boton. No se toca jamas. */
    @Column(name = "inicio", nullable = false)
    private Instant inicio;

    /** Nulo mientras la jornada sigue abierta. */
    @Column(name = "fin")
    private Instant fin;

    @Column(name = "inicio_corregido")
    private Instant inicioCorregido;

    @Column(name = "fin_corregido")
    private Instant finCorregido;

    @Column(name = "motivo_correccion", length = 300)
    private String motivoCorreccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corregido_por")
    private Usuario corregidoPor;

    @Column(name = "corregido_en")
    private Instant corregidoEn;

    /**
     * La cerro el administrador porque el trabajador se fue sin fichar salida.
     *
     * <p>Se marca para que se vea distinta en el listado: no es una jornada
     * normal, es una que hubo que cerrar a mano, y quien revisa las horas tiene
     * que saberlo.
     */
    @Column(name = "cerrada_por_olvido", nullable = false)
    private boolean cerradaPorOlvido;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @OneToMany(mappedBy = "fichaje", cascade = CascadeType.ALL)
    @OrderBy("fecha ASC, id ASC")
    private List<CambioFichaje> cambios = new ArrayList<>();

    // ==================================================================

    /** Empieza la jornada de alguien, aqui y ahora. */
    public static Fichaje empezar(Usuario usuario) {
        if (usuario == null) {
            throw new ReglaNegocioException("Un fichaje tiene que ser de alguien.");
        }
        Fichaje jornada = new Fichaje();
        jornada.usuario = usuario;
        jornada.inicio = Instant.now();
        return jornada;
    }

    /** Cierra la jornada. La hora es la de ahora: no se elige. */
    public void terminar() {
        exigirAbierta();
        this.fin = Instant.now();
    }

    /**
     * Cierre a mano de una jornada que se quedo abierta.
     *
     * <p>Alguien se fue a casa sin pulsar el boton y a la manana siguiente
     * sigue abierta desde ayer. No se puede dejar asi —bloquearia a esa persona
     * y falsearia sus horas—, pero tampoco vale inventarse la hora de salida:
     * la pone el administrador, con su motivo, y queda marcada como tal.
     */
    public void cerrarPorOlvido(Instant finReal, String motivo, Usuario administrador) {
        exigirAbierta();
        String razon = textoONulo(motivo);
        if (razon == null) {
            throw new ReglaNegocioException(
                    "Cerrar una jornada olvidada exige decir por que: es una hora que no fichó nadie.");
        }
        if (finReal == null || finReal.isBefore(inicio)) {
            throw new ReglaNegocioException(
                    "La hora de salida no puede ser anterior a la de entrada.");
        }
        if (finReal.isAfter(Instant.now())) {
            throw new ReglaNegocioException("La hora de salida no puede estar en el futuro.");
        }
        this.fin = finReal;
        this.cerradaPorOlvido = true;
        this.motivoCorreccion = razon;
        this.corregidoPor = administrador;
        this.corregidoEn = Instant.now();
        cambios.add(CambioFichaje.de(this, inicioReal(), null, razon, administrador));
    }

    /**
     * Corrige las horas de una jornada ya cerrada, sin borrar las fichadas.
     *
     * <p>Pasar nulo en una de las dos deja esa como estaba.
     */
    public void corregir(Instant nuevoInicio, Instant nuevoFin, String motivo, Usuario administrador) {
        if (fin == null) {
            throw new ConflictoException(
                    "No se puede corregir una jornada que todavia esta abierta. Cierrala primero.");
        }
        String razon = textoONulo(motivo);
        if (razon == null) {
            throw new ReglaNegocioException("Toda correccion tiene que decir por que.");
        }

        Instant desde = nuevoInicio != null ? nuevoInicio : inicioReal();
        Instant hasta = nuevoFin != null ? nuevoFin : finReal();
        if (hasta.isBefore(desde)) {
            throw new ReglaNegocioException("La salida no puede ser anterior a la entrada.");
        }
        if (hasta.isAfter(Instant.now())) {
            throw new ReglaNegocioException("Una jornada no puede terminar en el futuro.");
        }
        Instant inicioAntes = inicioReal();
        Instant finAntes = finReal();
        if (desde.equals(inicioAntes) && hasta.equals(finAntes)) {
            throw new ReglaNegocioException("No has cambiado ninguna hora.");
        }

        this.inicioCorregido = desde;
        this.finCorregido = hasta;
        this.motivoCorreccion = razon;
        this.corregidoPor = administrador;
        this.corregidoEn = Instant.now();
        cambios.add(CambioFichaje.de(this, inicioAntes, finAntes, razon, administrador));
    }

    // ==================================================================

    /** Los cambios a mano, del primero al ultimo. Solo se anaden desde aqui. */
    public List<CambioFichaje> getCambios() {
        return Collections.unmodifiableList(cambios);
    }

    public boolean estaAbierta() {
        return fin == null;
    }

    public boolean fueCorregida() {
        return inicioCorregido != null || finCorregido != null;
    }

    /** La entrada que cuenta: la corregida si la hay, y si no la que se fichó. */
    public Instant inicioReal() {
        return inicioCorregido != null ? inicioCorregido : inicio;
    }

    /** La salida que cuenta. Nula mientras la jornada siga abierta. */
    public Instant finReal() {
        return finCorregido != null ? finCorregido : fin;
    }

    /**
     * Lo que ha durado la jornada.
     *
     * <p>Si sigue abierta, lo que lleva hasta ahora: es lo que hay que enseñarle
     * a quien esta trabajando en este momento.
     */
    public Duration duracion() {
        return Duration.between(inicioReal(), finReal() != null ? finReal() : Instant.now());
    }

    private void exigirAbierta() {
        if (fin != null) {
            throw new ConflictoException("Esta jornada ya estaba cerrada.");
        }
    }

    private static String textoONulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
