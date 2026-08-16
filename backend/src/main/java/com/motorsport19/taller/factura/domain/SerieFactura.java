package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ReglaNegocioException;
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

/**
 * Serie de facturacion de un ejercicio.
 *
 * <p>{@link #ultimoNumero} es un contador <b>transaccional</b>: se incrementa con
 * la fila bloqueada dentro de la misma transaccion que inserta la factura. A
 * diferencia de una secuencia de PostgreSQL, si la transaccion hace rollback el
 * numero vuelve atras y la numeracion no queda con huecos.
 */
@Entity
@Table(name = "serie_factura")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SerieFactura extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Prefijo visible en el numero de factura (A, R, ...). */
    @Column(name = "codigo", nullable = false, length = 10)
    private String codigo;

    @Column(name = "ejercicio", nullable = false)
    private Integer ejercicio;

    @Column(name = "descripcion", nullable = false, length = 150)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoFactura tipo;

    @Column(name = "ultimo_numero", nullable = false)
    private Integer ultimoNumero;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    /**
     * Serie reservada a las facturas simplificadas.
     *
     * <p>Van aparte para que el libro quede ordenado y la gestoria las distinga
     * de un vistazo. Una factura simplificada solo se emite en una serie asi, y
     * una completa nunca.
     */
    @Column(name = "simplificada", nullable = false)
    private boolean simplificada;

    // ==================================================================
    // Alta y mantenimiento
    // ==================================================================

    /**
     * Abre una serie de facturacion.
     *
     * <p>El contador empieza en cero: la primera factura de la serie sera el
     * numero 1. Nace activa, porque una serie que se acaba de crear es la que se
     * va a usar.
     */
    public static SerieFactura crear(String codigo, Integer ejercicio, String descripcion,
                                     TipoFactura tipo, boolean simplificada) {
        String codigoLimpio = textoONulo(codigo);
        if (codigoLimpio == null) {
            throw new ReglaNegocioException("La serie necesita un codigo (A, R, F...).");
        }
        if (codigoLimpio.length() > 10) {
            throw new ReglaNegocioException("El codigo de la serie no puede pasar de 10 caracteres.");
        }
        if (ejercicio == null || ejercicio < 2000 || ejercicio > 2200) {
            throw new ReglaNegocioException("El ejercicio de la serie no es un año valido.");
        }
        if (tipo == null) {
            throw new ReglaNegocioException("Hay que decir si la serie es ordinaria o rectificativa.");
        }
        // Una rectificativa corrige a una factura concreta y arrastra sus datos:
        // no tiene sentido una serie de rectificativas «simplificadas».
        if (simplificada && tipo != TipoFactura.ORDINARIA) {
            throw new ReglaNegocioException(
                    "Solo una serie de facturas ordinarias puede ser de simplificadas.");
        }

        SerieFactura serie = new SerieFactura();
        serie.codigo = codigoLimpio.toUpperCase();
        serie.simplificada = simplificada;
        serie.ejercicio = ejercicio;
        serie.descripcion = textoONulo(descripcion) != null
                ? textoONulo(descripcion)
                : "Serie %s de %d".formatted(serie.codigo, ejercicio);
        serie.tipo = tipo;
        serie.ultimoNumero = 0;
        serie.activa = true;
        return serie;
    }

    /**
     * Cambia solo el texto descriptivo.
     *
     * <p>El codigo, el ejercicio y el tipo NO se tocan nunca: van impresos en el
     * numero de cada factura ya emitida y forman parte de la cadena de huellas.
     * Cambiarlos dejaria facturas cuyo numero no se corresponde con su serie.
     */
    public void renombrar(String descripcion) {
        String limpio = textoONulo(descripcion);
        if (limpio == null) {
            throw new ReglaNegocioException("La descripcion de la serie no puede quedar vacia.");
        }
        this.descripcion = limpio;
    }

    public void activar() {
        this.activa = true;
    }

    /**
     * Cierra la serie para nuevas facturas.
     *
     * <p>No borra nada: las facturas ya emitidas siguen donde estan, con su
     * numeracion intacta. Es lo que se hace al terminar un ejercicio.
     */
    public void desactivar() {
        this.activa = false;
    }

    /** Si ya ha emitido alguna factura. Una serie estrenada no se puede borrar. */
    public boolean tieneFacturas() {
        return ultimoNumero != null && ultimoNumero > 0;
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
