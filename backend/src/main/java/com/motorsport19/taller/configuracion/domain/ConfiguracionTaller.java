package com.motorsport19.taller.configuracion.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    /**
     * Identificacion del programa emisor. No la elige el taller: identifica al
     * software que emite las facturas, y falsearla rompe la trazabilidad.
     */
    /**
     * Tope de la factura simplificada con el que arranca una instalacion nueva.
     *
     * <p>Es el limite alto que la norma da a los talleres de reparacion de
     * vehiculos. Se puede cambiar en Ajustes, y quien lo confirma es la gestoria
     * del taller.
     */
    public static final java.math.BigDecimal LIMITE_SIMPLIFICADA_POR_DEFECTO =
            new java.math.BigDecimal("3000.00");

    public static final String SOFTWARE_NOMBRE = "MotorSport19 Taller";
    public static final String SOFTWARE_VERSION = "0.1.0";

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

    /**
     * Importe maximo, con IVA, de una factura simplificada.
     *
     * <p>Configurable y no clavado en el codigo: depende del tipo de actividad
     * —los talleres de reparacion de vehiculos tienen un limite mas alto que el
     * general— y puede cambiar por ley. Quien lo sabe es la gestoria.
     */
    @Column(name = "limite_factura_simplificada", nullable = false, precision = 12, scale = 2)
    private BigDecimal limiteFacturaSimplificada = LIMITE_SIMPLIFICADA_POR_DEFECTO;

    /**
     * Familia de pieza que se considera neumatico, o nulo si no se cobra tasa.
     *
     * <p>Se guarda la familia y no una lista de piezas porque el almacen ya esta
     * ordenado asi: dar de alta un neumatico nuevo no puede obligar a acordarse
     * de apuntarlo tambien aqui, que es como se acaba facturando uno sin tasa.
     */
    @Column(name = "familia_neumaticos", length = 60)
    private String familiaNeumaticos;

    /**
     * La pieza que se cobra como tasa por cada neumatico.
     *
     * <p>Es una pieza corriente del almacen y no un importe suelto: asi su
     * precio y su IVA se cambian donde se cambian los de todo lo demas, y
     * cuando la tasa suba no hay que tocar el programa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pieza_tasa_neumatico_id")
    private Pieza piezaTasaNeumatico;

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
     * La fila de un taller recien instalado, todavia sin datos.
     *
     * <p>La instalacion no puede traer unos datos fiscales de relleno: una razon
     * social o un NIF inventados acabarian impresos en una factura de verdad.
     * Por eso la fila no la crea ninguna migracion, sino el administrador la
     * primera vez que guarda los datos de la empresa en Ajustes, y hasta
     * entonces el resto del programa se niega a emitir nada.
     */
    public static ConfiguracionTaller sinRellenar() {
        ConfiguracionTaller nueva = new ConfiguracionTaller();
        nueva.id = ID_UNICO;
        nueva.softwareNombre = SOFTWARE_NOMBRE;
        nueva.softwareVersion = SOFTWARE_VERSION;
        return nueva;
    }

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
    /**
     * Deja configurada la tasa de reciclaje de neumaticos, o la desactiva.
     *
     * <p>Va aparte de {@link #actualizar} a proposito: aquello son los datos
     * fiscales del taller y esto es una regla de cobro. Mezclarlas obligaria a
     * mandar los trece campos de la empresa para tocar un desplegable.
     *
     * <p>Con cualquiera de las dos cosas a nulo, la tasa queda desactivada: sin
     * saber que es un neumatico o cuanto se cobra no hay nada que anadir, y un
     * taller que no vende neumaticos no tiene por que configurar nada.
     */
    public void configurarTasaNeumatico(String familiaNeumaticos, Pieza piezaTasa) {
        String familia = textoONulo(familiaNeumaticos);
        if (familia == null || piezaTasa == null) {
            this.familiaNeumaticos = null;
            this.piezaTasaNeumatico = null;
            return;
        }
        // Si la propia tasa fuera de la familia de los neumaticos, anadirla
        // dispararia otra tasa, y esa otra. Se corta aqui y no en el servicio:
        // aqui es donde se decide, y asi no depende de por donde se llame.
        if (familia.equalsIgnoreCase(textoONulo(piezaTasa.getFamilia()))) {
            throw new ReglaNegocioException(
                    ("La pieza de la tasa no puede estar en la familia «%s»: cada neumatico "
                     + "anadiria su tasa, y la tasa otra tasa.").formatted(familia));
        }
        this.familiaNeumaticos = familia;
        this.piezaTasaNeumatico = piezaTasa;
    }

    /** Si esta pieza lleva tasa de reciclaje. Falso si la tasa no esta configurada. */
    public boolean llevaTasaDeReciclaje(Pieza pieza) {
        return piezaTasaNeumatico != null
                && familiaNeumaticos != null
                && pieza != null
                && familiaNeumaticos.equalsIgnoreCase(textoONulo(pieza.getFamilia()));
    }

    public void actualizar(String razonSocial, String nif, String direccion, String codigoPostal,
                           String ciudad, String provincia, String pais, String telefono,
                           String email, BigDecimal tarifaHoraDefecto, String tipoIvaDefecto,
                           BigDecimal capacidadDiariaHoras,
                           BigDecimal limiteFacturaSimplificada) {
        this.razonSocial = exigir(razonSocial, "La razon social es obligatoria.");
        this.nif = exigir(nif, "El NIF del taller es obligatorio.").toUpperCase();
        this.direccion = exigir(direccion, "La direccion es obligatoria.");
        this.codigoPostal = exigir(codigoPostal, "El codigo postal es obligatorio.");
        this.ciudad = exigir(ciudad, "La ciudad es obligatoria.");
        // La columna no admite nulos: dejarla vacia es la forma de decir que el
        // taller no tiene provincia que poner (Ceuta, Melilla, extranjero).
        this.provincia = provincia == null ? "" : provincia.trim();
        this.pais = pais == null || pais.isBlank() ? "ES" : pais.trim();
        this.telefono = telefono;
        this.email = email;
        // Se acepta lo que diga la gestoria, pero no un numero absurdo: un limite
        // negativo dejaria el taller sin poder emitir ninguna simplificada sin
        // que nadie entendiera por que.
        if (limiteFacturaSimplificada != null && limiteFacturaSimplificada.signum() < 0) {
            throw new ReglaNegocioException(
                    "El limite de la factura simplificada no puede ser negativo.");
        }
        // Sin valor se conserva el que hubiera. Nunca se queda a nulo: la columna
        // no lo admite, y un taller recien instalado guardaba sus datos por
        // primera vez con este campo vacio y se estrellaba contra la base.
        if (limiteFacturaSimplificada != null) {
            this.limiteFacturaSimplificada = limiteFacturaSimplificada;
        } else if (this.limiteFacturaSimplificada == null) {
            this.limiteFacturaSimplificada = LIMITE_SIMPLIFICADA_POR_DEFECTO;
        }

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

    /** El texto sin espacios sobrantes, o nulo si no habia nada. */
    private static String textoONulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String exigir(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new com.motorsport19.taller.common.error.ReglaNegocioException(mensaje);
        }
        return valor.trim();
    }
}
