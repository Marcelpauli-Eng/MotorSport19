package com.motorsport19.taller.cliente.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.common.util.ValidadorDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Cliente del taller.
 *
 * <p>La ficha puede crearse incompleta (alguien que entra con una averia y deja
 * solo nombre y telefono), pero los datos fiscales son obligatorios ANTES de
 * poder emitir una factura: {@link #tieneDatosFiscalesCompletos()} es la
 * comprobacion que usara el modulo de facturacion.
 *
 * <p>Baja logica mediante {@code activo}: nunca se borra fisicamente porque
 * queda referenciado desde motos, ordenes de trabajo y facturas. La base de
 * datos ademas rechaza cualquier DELETE sobre esta tabla.
 */
@Entity
@Table(name = "cliente")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cliente extends EntidadAuditable {

    private static final String PAIS_POR_DEFECTO = "Espana";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** Vacio en personas juridicas. */
    @Column(name = "apellidos", length = 150)
    private String apellidos;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 10)
    private TipoDocumento tipoDocumento;

    /** NIF / CIF / NIE. Unico cuando esta informado (indice unico sobre mayusculas). */
    @Column(name = "documento", length = 20)
    private String documento;

    @Column(name = "direccion", length = 200)
    private String direccion;

    @Column(name = "codigo_postal", length = 10)
    private String codigoPostal;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "provincia", length = 100)
    private String provincia;

    @Column(name = "pais", nullable = false, length = 60)
    private String pais;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_baja")
    private Instant fechaBaja;

    // ------------------------------------------------------------------
    // Creacion
    // ------------------------------------------------------------------

    /**
     * Da de alta un cliente con lo minimo imprescindible. Los datos fiscales se
     * completan despues con {@link #asignarDatosFiscales}.
     */
    public static Cliente registrar(String nombre, String apellidos, String telefono, String email) {
        Cliente cliente = new Cliente();
        cliente.aplicarContacto(nombre, apellidos, telefono, email, null);
        cliente.pais = PAIS_POR_DEFECTO;
        cliente.activo = true;
        return cliente;
    }

    // ------------------------------------------------------------------
    // Modificacion
    // ------------------------------------------------------------------

    public void actualizarContacto(String nombre, String apellidos, String telefono, String email,
                                   String observaciones) {
        comprobarActivo();
        aplicarContacto(nombre, apellidos, telefono, email, observaciones);
    }

    /**
     * Asigna o corrige los datos fiscales.
     *
     * <p>Valida el digito de control del documento: un NIF mal tecleado no se
     * detectaria hasta que Hacienda rechazase la factura, y para entonces la
     * factura ya seria inmutable.
     */
    public void asignarDatosFiscales(TipoDocumento tipoDocumento, String documento, String direccion,
                                     String codigoPostal, String ciudad, String provincia, String pais) {
        comprobarActivo();

        String normalizado = ValidadorDocumento.normalizar(documento);
        if (normalizado == null) {
            throw new ReglaNegocioException("El documento fiscal es obligatorio para poder facturar.");
        }

        TipoDocumento tipoFinal = tipoDocumento != null
                ? tipoDocumento
                : ValidadorDocumento.deducirTipo(normalizado);

        // Pasaporte y "otro" no llevan digito de control comprobable.
        boolean comprobable = tipoFinal == TipoDocumento.NIF
                || tipoFinal == TipoDocumento.NIE
                || tipoFinal == TipoDocumento.CIF;
        if (comprobable && !ValidadorDocumento.esValido(normalizado)) {
            throw new ReglaNegocioException(
                    "El documento '%s' no es un %s valido: el digito de control no cuadra."
                            .formatted(normalizado, tipoFinal));
        }

        this.tipoDocumento = tipoFinal;
        this.documento = normalizado;
        this.direccion = textoONulo(direccion);
        this.codigoPostal = textoONulo(codigoPostal);
        this.ciudad = textoONulo(ciudad);
        this.provincia = textoONulo(provincia);
        this.pais = textoONulo(pais) != null ? textoONulo(pais) : PAIS_POR_DEFECTO;
    }

    /**
     * Baja logica. El cliente sigue existiendo porque conserva historial, motos
     * y facturas; simplemente deja de aparecer en las busquedas del dia a dia.
     */
    public void darDeBaja() {
        if (!activo) {
            throw new ConflictoException("El cliente '%s' ya estaba dado de baja.".formatted(nombreCompleto()));
        }
        this.activo = false;
        this.fechaBaja = Instant.now();
    }

    public void reactivar() {
        if (activo) {
            throw new ConflictoException("El cliente '%s' ya estaba activo.".formatted(nombreCompleto()));
        }
        this.activo = true;
        this.fechaBaja = null;
    }

    // ------------------------------------------------------------------
    // Consultas de dominio
    // ------------------------------------------------------------------

    /**
     * Indica si la ficha reune todo lo que exige una factura: documento fiscal y
     * domicilio completo.
     */
    public boolean tieneDatosFiscalesCompletos() {
        return documento != null
                && direccion != null
                && codigoPostal != null
                && ciudad != null
                && provincia != null
                && pais != null;
    }

    /** Nombre para mostrar. En personas juridicas los apellidos van vacios. */
    public String nombreCompleto() {
        return apellidos == null || apellidos.isBlank() ? nombre : nombre + " " + apellidos;
    }

    // ------------------------------------------------------------------

    private void aplicarContacto(String nombre, String apellidos, String telefono, String email,
                                 String observaciones) {
        String nombreLimpio = textoONulo(nombre);
        if (nombreLimpio == null) {
            throw new ReglaNegocioException("El nombre del cliente es obligatorio.");
        }
        this.nombre = nombreLimpio;
        this.apellidos = textoONulo(apellidos);
        this.telefono = textoONulo(telefono);
        this.email = textoONulo(email);
        this.observaciones = textoONulo(observaciones);
    }

    private void comprobarActivo() {
        if (!activo) {
            throw new ConflictoException(
                    "El cliente '%s' esta dado de baja: reactivelo antes de modificarlo.".formatted(nombreCompleto()));
        }
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
