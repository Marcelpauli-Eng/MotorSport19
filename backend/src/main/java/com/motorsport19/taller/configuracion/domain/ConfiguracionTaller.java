package com.motorsport19.taller.configuracion.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Datos fiscales y operativos del taller (el emisor de las facturas).
 *
 * <p>Tabla de una unica fila, con {@code id = 1} garantizado por una restriccion
 * CHECK en la base de datos. Estos datos se COPIAN dentro de cada factura en el
 * momento de emitirla: si el taller cambia de domicilio, las facturas antiguas
 * siguen mostrando el domicilio que tenian cuando se emitieron.
 */
@Entity
@Table(name = "configuracion_taller")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfiguracionTaller extends EntidadAuditable {

    /** Siempre 1: la tabla solo admite una fila. */
    public static final Integer ID_UNICO = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(name = "nif", nullable = false, length = 20)
    private String nif;

    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion;

    @Column(name = "codigo_postal", nullable = false, length = 10)
    private String codigoPostal;

    @Column(name = "ciudad", nullable = false, length = 100)
    private String ciudad;

    @Column(name = "provincia", nullable = false, length = 100)
    private String provincia;

    @Column(name = "pais", nullable = false, length = 60)
    private String pais;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "email", length = 150)
    private String email;

    /** Tarifa/hora por defecto de mano de obra. Se congela en cada OT al abrirla. */
    @Column(name = "tarifa_hora_defecto", nullable = false, precision = 12, scale = 2)
    private BigDecimal tarifaHoraDefecto;

    @Column(name = "tipo_iva_defecto", nullable = false, length = 20)
    private String tipoIvaDefecto;

    /**
     * Horas de taller que caben en un dia.
     *
     * <p>No es un limite que impida nada: es la referencia contra la que la
     * agenda avisa de que un dia esta lleno. Un taller siempre puede meter una
     * urgencia mas, pero conviene que se vea que la esta metiendo.
     */
    @Column(name = "capacidad_diaria_horas", nullable = false, precision = 5, scale = 2)
    private BigDecimal capacidadDiariaHoras;

    @Column(name = "software_nombre", nullable = false, length = 100)
    private String softwareNombre;

    @Column(name = "software_version", nullable = false, length = 30)
    private String softwareVersion;

    @Column(name = "software_nif", length = 20)
    private String softwareNif;

    /** URL base que se codifica en el QR de la factura para su verificacion. */
    @Column(name = "url_verificacion_qr", length = 300)
    private String urlVerificacionQr;

    /**
     * Cambia los datos del taller.
     *
     * <p>Los datos del software (nombre, version, NIF) no se tocan desde aqui:
     * identifican al programa que emite, no al taller, y falsearlos romperia la
     * trazabilidad de las facturas ya emitidas.
     *
     * <p>Cambiar estos datos NO afecta a las facturas antiguas: cada una lleva
     * dentro una copia de como estaba el taller el dia que se emitio.
     */
    public void actualizar(String razonSocial, String nif, String direccion, String codigoPostal,
                           String ciudad, String provincia, String pais, String telefono,
                           String email, BigDecimal tarifaHoraDefecto, String tipoIvaDefecto,
                           BigDecimal capacidadDiariaHoras) {
        this.razonSocial = exigir(razonSocial, "La razon social es obligatoria.");
        this.nif = exigir(nif, "El NIF del taller es obligatorio.").toUpperCase();
        this.direccion = exigir(direccion, "La direccion es obligatoria.");
        this.codigoPostal = exigir(codigoPostal, "El codigo postal es obligatorio.");
        this.ciudad = exigir(ciudad, "La ciudad es obligatoria.");
        this.provincia = provincia;
        this.pais = pais == null || pais.isBlank() ? "ES" : pais.trim();
        this.telefono = telefono;
        this.email = email;

        if (tarifaHoraDefecto == null || tarifaHoraDefecto.signum() <= 0) {
            throw new com.motorsport19.taller.common.error.ReglaNegocioException(
                    "La tarifa por hora tiene que ser mayor que cero.");
        }
        this.tarifaHoraDefecto = tarifaHoraDefecto;
        this.tipoIvaDefecto = exigir(tipoIvaDefecto, "El tipo de IVA por defecto es obligatorio.");

        if (capacidadDiariaHoras == null || capacidadDiariaHoras.signum() <= 0) {
            throw new com.motorsport19.taller.common.error.ReglaNegocioException(
                    "La capacidad diaria del taller tiene que ser mayor que cero.");
        }
        this.capacidadDiariaHoras = capacidadDiariaHoras;
    }

    private static String exigir(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new com.motorsport19.taller.common.error.ReglaNegocioException(mensaje);
        }
        return valor.trim();
    }
}
