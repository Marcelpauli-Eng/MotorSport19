package com.motorsport19.taller.configuracion.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
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
import java.util.List;
import java.util.Optional;

/**
 * Una tasa o un plus que se aplica solo al anadir una pieza a una orden.
 *
 * <p>Apunta a un grupo (familia) entero o a una pieza concreta. Si hay de las
 * dos, manda la de la pieza: es la excepcion que alguien ha puesto a proposito
 * sobre la regla general de su grupo.
 *
 * <ul>
 *   <li><b>TASA</b>: una linea aparte de {@code valor} euros por unidad, con la
 *       misma cantidad que la pieza. La tasa de reciclaje de neumaticos es el
 *       caso de siempre.</li>
 *   <li><b>PLUS</b>: un descuento de {@code valor} por ciento en la propia
 *       linea de la pieza.</li>
 * </ul>
 */
@Entity
@Table(name = "regla_cobro")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReglaCobro extends EntidadAuditable {

    public enum Tipo { TASA, PLUS }

    private static final BigDecimal CIEN = new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private Tipo tipo;

    /** Grupo al que se aplica, o nulo si es de una pieza concreta. */
    @Column(name = "familia", length = 60)
    private String familia;

    /** La pieza concreta, o nula si vale para todo el grupo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pieza_id")
    private Pieza pieza;

    /** Texto de la linea de la tasa. */
    @Column(name = "concepto", length = 300)
    private String concepto;

    /** Euros por unidad en una tasa; tanto por ciento en un plus. */
    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    /**
     * Con una pieza, la regla es solo para ella y el grupo se ignora: el grupo
     * de la pieza puede cambiar despues, y la regla tiene que seguirla.
     */
    public static ReglaCobro crear(Tipo tipo, String familia, Pieza pieza, String concepto,
                                   BigDecimal valor) {
        if (tipo == null) {
            throw new ReglaNegocioException("Indica si es una tasa o un plus.");
        }
        ReglaCobro regla = new ReglaCobro();
        regla.tipo = tipo;
        regla.pieza = pieza;
        regla.familia = pieza == null ? textoONulo(familia) : null;
        if (regla.pieza == null && regla.familia == null) {
            throw new ReglaNegocioException("Elige el grupo o la pieza a la que se aplica.");
        }
        regla.concepto = textoONulo(concepto);
        if (valor == null || valor.signum() <= 0) {
            throw new ReglaNegocioException(tipo == Tipo.TASA
                    ? "El importe de la tasa tiene que ser mayor que cero."
                    : "El descuento tiene que ser mayor que cero.");
        }
        if (tipo == Tipo.TASA && regla.concepto == null) {
            throw new ReglaNegocioException(
                    "Pon el concepto de la tasa: es el texto que sale en el presupuesto.");
        }
        if (tipo == Tipo.PLUS && valor.compareTo(CIEN) > 0) {
            throw new ReglaNegocioException("El descuento no puede pasar del 100 %.");
        }
        regla.valor = valor;
        return regla;
    }

    /** Si esta regla alcanza a la pieza, sea por ella misma o por su grupo. */
    public boolean aplicaA(Pieza p) {
        if (pieza != null) {
            return pieza.getId() != null && pieza.getId().equals(p.getId());
        }
        return familia.equalsIgnoreCase(textoONulo(p.getFamilia()));
    }

    /** Si las dos reglas son del mismo tipo y apuntan a lo mismo. */
    public boolean mismaQue(ReglaCobro otra) {
        return tipo == otra.tipo
                && (pieza != null
                        ? otra.pieza != null && pieza.getId().equals(otra.pieza.getId())
                        : otra.pieza == null && familia.equalsIgnoreCase(otra.familia));
    }

    /** La regla de ese tipo que manda sobre la pieza: la suya antes que la de su grupo. */
    public static Optional<ReglaCobro> laQueManda(List<ReglaCobro> reglas, Tipo tipo, Pieza pieza) {
        if (pieza == null) {
            return Optional.empty();
        }
        List<ReglaCobro> aplicables = reglas.stream()
                .filter(r -> r.tipo == tipo && r.aplicaA(pieza))
                .toList();
        return aplicables.stream().filter(r -> r.pieza != null).findFirst()
                .or(() -> aplicables.stream().findFirst());
    }

    private static String textoONulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
