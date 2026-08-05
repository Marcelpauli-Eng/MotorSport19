package com.motorsport19.taller.inventario.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.common.util.ValidadorDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Proveedor habitual de recambios. Baja logica.
 */
@Entity
@Table(name = "proveedor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Proveedor extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "nif", length = 20)
    private String nif;

    @Column(name = "direccion", length = 200)
    private String direccion;

    @Column(name = "codigo_postal", length = 10)
    private String codigoPostal;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "provincia", length = 100)
    private String provincia;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_baja")
    private Instant fechaBaja;

    public static Proveedor registrar(String nombre, String nif, String direccion, String codigoPostal,
                                      String ciudad, String provincia, String telefono, String email,
                                      String observaciones) {
        Proveedor proveedor = new Proveedor();
        proveedor.aplicarDatos(nombre, nif, direccion, codigoPostal, ciudad, provincia, telefono, email,
                observaciones);
        proveedor.activo = true;
        return proveedor;
    }

    public void actualizarDatos(String nombre, String nif, String direccion, String codigoPostal,
                                String ciudad, String provincia, String telefono, String email,
                                String observaciones) {
        if (!activo) {
            throw new ConflictoException(
                    "El proveedor '%s' esta dado de baja: reactivelo antes de modificarlo.".formatted(this.nombre));
        }
        aplicarDatos(nombre, nif, direccion, codigoPostal, ciudad, provincia, telefono, email, observaciones);
    }

    public void darDeBaja() {
        if (!activo) {
            throw new ConflictoException("El proveedor '%s' ya estaba dado de baja.".formatted(nombre));
        }
        this.activo = false;
        this.fechaBaja = Instant.now();
    }

    public void reactivar() {
        if (activo) {
            throw new ConflictoException("El proveedor '%s' ya estaba activo.".formatted(nombre));
        }
        this.activo = true;
        this.fechaBaja = null;
    }

    private void aplicarDatos(String nombre, String nif, String direccion, String codigoPostal, String ciudad,
                              String provincia, String telefono, String email, String observaciones) {
        String nombreLimpio = textoONulo(nombre);
        if (nombreLimpio == null) {
            throw new ReglaNegocioException("El nombre del proveedor es obligatorio.");
        }
        // Solo se comprueba el digito de control si el documento tiene forma de
        // NIF, NIE o CIF espanol. Un proveedor extranjero puede tener un numero
        // de IVA con otro formato y no hay que cerrarle la puerta.
        String nifNormalizado = ValidadorDocumento.normalizar(nif);
        if (nifNormalizado != null && ValidadorDocumento.esDocumentoEspanol(nifNormalizado)
                && !ValidadorDocumento.esValido(nifNormalizado)) {
            throw new ReglaNegocioException(
                    "El NIF/CIF '%s' del proveedor no es valido: el digito de control no cuadra."
                            .formatted(nifNormalizado));
        }

        this.nombre = nombreLimpio;
        this.nif = nifNormalizado;
        this.direccion = textoONulo(direccion);
        this.codigoPostal = textoONulo(codigoPostal);
        this.ciudad = textoONulo(ciudad);
        this.provincia = textoONulo(provincia);
        this.telefono = textoONulo(telefono);
        this.email = textoONulo(email);
        this.observaciones = textoONulo(observaciones);
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
