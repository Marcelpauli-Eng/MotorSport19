package com.motorsport19.taller.orden.domain;

import com.motorsport19.taller.inventario.domain.Pieza;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que lleva lineas con importe: la orden de trabajo y el presupuesto de una
 * solicitud web. Es lo que necesitan los pluses y las tasas de las piezas para
 * aplicarse igual en los dos.
 */
public interface ConLineas<L extends LineaImporte> {

    List<L> getLineas();

    L anadirPieza(Pieza pieza, BigDecimal cantidad, BigDecimal descuentoPct, BigDecimal porcentajeIva);

    L anadirTasa(String concepto, BigDecimal cantidad, BigDecimal importe, String tipoIva,
                 BigDecimal porcentajeIva);

    void quitarLinea(L linea);
}
