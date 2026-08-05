package com.motorsport19.taller.inventario.service;

import com.motorsport19.taller.inventario.domain.MovimientoStock;

import java.math.BigDecimal;

/**
 * Resultado de intentar consumir material sin lanzar excepcion.
 *
 * <p>Existe por un motivo muy concreto: cuando un metodo {@code @Transactional}
 * deja escapar una excepcion, Spring marca la transaccion como <i>rollback-only</i>
 * aunque quien llama la capture. El servicio de ordenes de trabajo necesita
 * precisamente eso — intentar servir varias lineas, quedarse con las que puede y
 * continuar con las que no —, asi que aqui la falta de existencias se devuelve
 * como dato y no como excepcion.
 *
 * @param servido     si se pudo servir la cantidad pedida
 * @param disponible  existencias en el momento del intento
 * @param solicitado  cantidad que se pedia
 * @param movimiento  asiento generado, o {@code null} si no se sirvio
 */
public record IntentoConsumo(
        boolean servido,
        BigDecimal disponible,
        BigDecimal solicitado,
        MovimientoStock movimiento
) {

    static IntentoConsumo servido(MovimientoStock movimiento, BigDecimal disponible,
                                  BigDecimal solicitado) {
        return new IntentoConsumo(true, disponible, solicitado, movimiento);
    }

    static IntentoConsumo sinExistencias(BigDecimal disponible, BigDecimal solicitado) {
        return new IntentoConsumo(false, disponible, solicitado, null);
    }

    /** Unidades que faltaban. Cero cuando si se pudo servir. */
    public BigDecimal faltan() {
        return servido ? BigDecimal.ZERO : solicitado.subtract(disponible).max(BigDecimal.ZERO);
    }
}
