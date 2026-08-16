package com.motorsport19.taller.moto.domain;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.common.util.Matriculas;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.time.Year;

/**
 * Moto de un cliente.
 *
 * <p>El historial de intervenciones no se modela como una coleccion: se consulta
 * a traves de {@code orden_trabajo.moto_id} para no cargar anos de ordenes cada
 * vez que se lee una ficha.
 */
@Entity
@Table(name = "moto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Moto extends EntidadAuditable {

    /** Primera motocicleta de la historia (Daimler Reitwagen). */
    private static final int ANIO_MINIMO = 1885;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "matricula", nullable = false, length = 15)
    private String matricula;

    @Column(name = "marca", nullable = false, length = 60)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 100)
    private String modelo;

    @Column(name = "anio")
    private Integer anio;

    /** Cilindrada en centimetros cubicos. */
    @Column(name = "cilindrada")
    private Integer cilindrada;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "numero_bastidor", length = 30)
    private String numeroBastidor;

    /** Ultimo kilometraje conocido. Se actualiza con el km de entrada de cada OT. */
    @Column(name = "km_actual", nullable = false)
    private Integer kmActual;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_baja")
    private Instant fechaBaja;

    // ------------------------------------------------------------------
    // Creacion
    // ------------------------------------------------------------------

    public static Moto registrar(Cliente cliente, String matricula, String marca, String modelo,
                                 Integer anio, Integer cilindrada, String color, String numeroBastidor,
                                 Integer kmActual, String observaciones) {
        if (cliente == null) {
            throw new ReglaNegocioException("La moto debe pertenecer a un cliente.");
        }
        Moto moto = new Moto();
        moto.cliente = cliente;
        moto.aplicarDatos(matricula, marca, modelo, anio, cilindrada, color, numeroBastidor, observaciones);
        moto.kmActual = validarKilometraje(kmActual == null ? 0 : kmActual);
        moto.activo = true;
        return moto;
    }

    // ------------------------------------------------------------------
    // Modificacion
    // ------------------------------------------------------------------

    public void actualizarDatos(String matricula, String marca, String modelo, Integer anio,
                                Integer cilindrada, String color, String numeroBastidor, String observaciones) {
        comprobarActiva();
        aplicarDatos(matricula, marca, modelo, anio, cilindrada, color, numeroBastidor, observaciones);
    }

    /**
     * Actualiza el kilometraje conocido.
     *
     * <p>El cuentakilometros no retrocede. Si llega una lectura menor que la
     * registrada, o es un error de tecleo o alguien ha manipulado el cuadro; en
     * ambos casos hay que mirarlo, no guardarlo en silencio.
     */
    public void registrarKilometraje(int km) {
        comprobarActiva();
        exigirLecturaNoRetrocede(km);
        this.kmActual = km;
    }

    /**
     * Comprueba una lectura del cuentakilometros sin llegar a guardarla.
     *
     * <p>Existe aparte de {@link #registrarKilometraje} porque hay que poder
     * rechazar una lectura ANTES de haber creado nada. Al abrir una orden, por
     * ejemplo: si se comprueba al final, para entonces ya se ha consumido un
     * numero del contador del ejercicio y se ha montado la OT entera.
     */
    public void exigirLecturaNoRetrocede(int km) {
        validarKilometraje(km);
        if (km < kmActual) {
            throw new ReglaNegocioException(
                    ("El kilometraje de la moto %s no puede disminuir: ya tenia registrados %d km "
                     + "y se han indicado %d km.").formatted(matricula, kmActual, km));
        }
    }

    public void cambiarPropietario(Cliente nuevoPropietario) {
        comprobarActiva();
        if (nuevoPropietario == null) {
            throw new ReglaNegocioException("La moto debe pertenecer a un cliente.");
        }
        this.cliente = nuevoPropietario;
    }

    public void darDeBaja() {
        if (!activo) {
            throw new ConflictoException("La moto %s ya estaba dada de baja.".formatted(matricula));
        }
        this.activo = false;
        this.fechaBaja = Instant.now();
    }

    public void reactivar() {
        if (activo) {
            throw new ConflictoException("La moto %s ya estaba activa.".formatted(matricula));
        }
        this.activo = true;
        this.fechaBaja = null;
    }

    /** Descripcion para listados y facturas: "Yamaha MT-07". */
    public String descripcion() {
        return marca + " " + modelo;
    }

    // ------------------------------------------------------------------

    private void aplicarDatos(String matricula, String marca, String modelo, Integer anio, Integer cilindrada,
                              String color, String numeroBastidor, String observaciones) {
        String matriculaNormalizada = Matriculas.normalizar(matricula);
        if (matriculaNormalizada == null) {
            throw new ReglaNegocioException("La matricula es obligatoria.");
        }
        if (textoONulo(marca) == null) {
            throw new ReglaNegocioException("La marca es obligatoria.");
        }
        if (textoONulo(modelo) == null) {
            throw new ReglaNegocioException("El modelo es obligatorio.");
        }
        if (anio != null && (anio < ANIO_MINIMO || anio > Year.now().getValue() + 1)) {
            throw new ReglaNegocioException(
                    "El ano %d no es valido para una motocicleta.".formatted(anio));
        }
        if (cilindrada != null && cilindrada <= 0) {
            throw new ReglaNegocioException("La cilindrada debe ser mayor que cero.");
        }

        this.matricula = matriculaNormalizada;
        this.marca = textoONulo(marca);
        this.modelo = textoONulo(modelo);
        this.anio = anio;
        this.cilindrada = cilindrada;
        this.color = textoONulo(color);
        this.numeroBastidor = textoONulo(numeroBastidor) != null
                ? textoONulo(numeroBastidor).toUpperCase()
                : null;
        this.observaciones = textoONulo(observaciones);
    }

    private void comprobarActiva() {
        if (!activo) {
            throw new ConflictoException(
                    "La moto %s esta dada de baja: reactivela antes de modificarla.".formatted(matricula));
        }
    }

    private static int validarKilometraje(int km) {
        if (km < 0) {
            throw new ReglaNegocioException("El kilometraje no puede ser negativo.");
        }
        return km;
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
