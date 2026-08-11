package com.motorsport19.taller.inventario.repository;

import com.motorsport19.taller.inventario.domain.MovimientoStock;
import com.motorsport19.taller.inventario.domain.TipoMovimiento;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Criterios de busqueda del libro de movimientos.
 *
 * <p>Cada criterio se anade al WHERE solo si viene informado. Es a proposito y no
 * un capricho: la version con {@code (:parametro IS NULL OR campo = :parametro)}
 * hacia que PostgreSQL no pudiera deducir el tipo de los parametros nulos y el
 * libro entero respondia 500.
 */
public final class FiltroMovimientos {

    private FiltroMovimientos() {
    }

    public static Specification<MovimientoStock> de(Long piezaId, TipoMovimiento tipo,
                                                    Instant desde, Instant hasta) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();

            if (piezaId != null) {
                condiciones.add(cb.equal(raiz.get("pieza").get("id"), piezaId));
            }
            if (tipo != null) {
                condiciones.add(cb.equal(raiz.get("tipo"), tipo));
            }
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fecha"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(raiz.get("fecha"), hasta));
            }

            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }
}
