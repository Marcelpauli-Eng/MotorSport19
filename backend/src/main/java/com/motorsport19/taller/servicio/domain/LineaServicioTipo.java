package com.motorsport19.taller.servicio.domain;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.orden.domain.TipoLinea;
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

/**
 * Una linea de una plantilla: horas de taller o una pieza del catalogo.
 *
 * <p>Reutiliza {@link TipoLinea} de las ordenes a proposito. Una plantilla es
 * el molde de unas lineas de OT, y si manana se anadiera un tercer tipo de
 * linea a las ordenes, esto tiene que hablar del mismo vocabulario y no de una
 * copia suya que se quedaria corta.
 *
 * <p>No guarda precio ni descuento. El precio se lee al volcar; el descuento es
 * una negociacion con un cliente concreto y no tiene sentido en un molde.
 */
@Entity
@Table(name = "linea_servicio_tipo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineaServicioTipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_tipo_id", nullable = false)
    private ServicioTipo servicioTipo;

    @Column(name = "numero_linea", nullable = false)
    private Integer numeroLinea;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoLinea tipo;

    /** Solo en mano de obra. En las piezas manda la descripcion del catalogo. */
    @Column(name = "descripcion", length = 300)
    private String descripcion;

    /** Solo en lineas de tipo PIEZA. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pieza_id")
    private Pieza pieza;

    /** Horas en MANO_DE_OBRA, unidades en PIEZA. */
    @Column(name = "cantidad", nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    // ==================================================================
    // Fabricas
    // ==================================================================

    static LineaServicioTipo manoDeObra(ServicioTipo servicio, int numeroLinea, String descripcion,
                                        BigDecimal horas) {
        String limpia = textoONulo(descripcion);
        if (limpia == null) {
            throw new ReglaNegocioException(
                    "La linea %d es de mano de obra y necesita una descripcion.".formatted(numeroLinea));
        }
        LineaServicioTipo linea = base(servicio, numeroLinea, TipoLinea.MANO_DE_OBRA, horas);
        linea.descripcion = limpia;
        linea.pieza = null;
        return linea;
    }

    static LineaServicioTipo pieza(ServicioTipo servicio, int numeroLinea, Pieza pieza,
                                   BigDecimal unidades) {
        if (pieza == null) {
            throw new ReglaNegocioException(
                    "La linea %d es de pieza y necesita una referencia del catalogo."
                            .formatted(numeroLinea));
        }
        LineaServicioTipo linea = base(servicio, numeroLinea, TipoLinea.PIEZA, unidades);
        // La descripcion no se copia: si manana cambia el nombre de la pieza en
        // el catalogo, la plantilla debe decir lo nuevo. Aqui no hay nada que
        // congelar, porque nada de esto se le ha facturado todavia a nadie.
        linea.descripcion = null;
        linea.pieza = pieza;
        return linea;
    }

    // ==================================================================
    // Consultas
    // ==================================================================

    public boolean esManoDeObra() {
        return tipo == TipoLinea.MANO_DE_OBRA;
    }

    /** Lo que se lee en pantalla: el texto propio o el del catalogo. */
    public String textoVisible() {
        return esManoDeObra() ? descripcion : pieza.getDescripcion();
    }

    // ==================================================================

    private static LineaServicioTipo base(ServicioTipo servicio, int numeroLinea, TipoLinea tipo,
                                          BigDecimal cantidad) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ReglaNegocioException(
                    "La cantidad de la linea %d debe ser mayor que cero.".formatted(numeroLinea));
        }
        LineaServicioTipo linea = new LineaServicioTipo();
        linea.servicioTipo = servicio;
        linea.numeroLinea = numeroLinea;
        linea.tipo = tipo;
        linea.cantidad = cantidad;
        return linea;
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
