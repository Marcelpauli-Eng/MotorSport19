package com.motorsport19.taller.inventario.service;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * No hay existencias suficientes para servir la cantidad pedida.
 *
 * <p>Lleva los datos del descuadre porque quien la captura necesita actuar con
 * ellos: en la fase 3, el servicio de ordenes de trabajo la usara para mover la
 * OT a {@code ESPERANDO_PIEZAS} e indicar cuantas unidades faltan, en vez de
 * permitir que el stock se vaya a negativo.
 */
@Getter
public class StockInsuficienteException extends ReglaNegocioException {

    private final String sku;
    private final BigDecimal disponible;
    private final BigDecimal solicitado;

    public StockInsuficienteException(String sku, BigDecimal disponible, BigDecimal solicitado) {
        // Sin ceros sobrantes: "1.000" unidades se lee como mil en espanol.
        super("Stock insuficiente de la pieza %s: hay %s unidades disponibles y se han solicitado %s."
                .formatted(sku, disponible.stripTrailingZeros().toPlainString(),
                        solicitado.stripTrailingZeros().toPlainString()));
        this.sku = sku;
        this.disponible = disponible;
        this.solicitado = solicitado;
    }

    /** Unidades que faltan para poder servir la cantidad pedida. */
    public BigDecimal faltan() {
        return solicitado.subtract(disponible);
    }
}
