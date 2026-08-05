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

    @Column(name = "software_nombre", nullable = false, length = 100)
    private String softwareNombre;

    @Column(name = "software_version", nullable = false, length = 30)
    private String softwareVersion;

    @Column(name = "software_nif", length = 20)
    private String softwareNif;

    /** URL base que se codifica en el QR de la factura para su verificacion. */
    @Column(name = "url_verificacion_qr", length = 300)
    private String urlVerificacionQr;
}
