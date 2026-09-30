package com.motorsport19.taller.orden.service;

import com.motorsport19.taller.configuracion.domain.ReglaCobro;
import com.motorsport19.taller.configuracion.domain.TipoIva;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.orden.domain.ConLineas;
import com.motorsport19.taller.orden.domain.LineaImporte;
import com.motorsport19.taller.orden.domain.TipoLinea;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

/**
 * Pluses y tasas de las piezas (Ajustes &gt; Tasas y pluses).
 *
 * <p>Estan aqui y no en el servicio de ordenes porque el presupuesto de una
 * solicitud web tiene que cobrar exactamente igual: si el cliente acepta, la
 * orden que se abre no puede salir por un precio distinto del que vio.
 */
public final class CobroDePiezas {

    private CobroDePiezas() {
    }

    /**
     * Anade la linea de una pieza con el plus que le toque.
     *
     * <p>El descuento puesto a mano manda; si no hay ninguno, el plus que tenga
     * la pieza o su grupo en Ajustes &gt; Tasas y pluses. En tanto por ciento va
     * como descuento de la linea; en euros baja el precio de cada unidad.
     */
    public static <L extends LineaImporte> L anadirConPlus(ConLineas<L> orden, List<ReglaCobro> reglas, Pieza pieza,
                                         BigDecimal cantidad, BigDecimal descuentoPct,
                                         BigDecimal porcentajeIva) {
        ReglaCobro plus = descuentoPct != null && descuentoPct.signum() > 0 ? null
                : ReglaCobro.laQueManda(reglas, ReglaCobro.Tipo.PLUS, pieza).orElse(null);
        BigDecimal descuento = plus != null && plus.enPorcentaje() ? plus.getValor() : descuentoPct;
        L linea = orden.anadirPieza(pieza, cantidad, descuento, porcentajeIva);
        if (plus != null && !plus.enPorcentaje()) {
            linea.rebajarPrecio(plus.getValor());
        }
        return linea;
    }

    /**
     * Anade la tasa que acompana a una pieza, si su regla la pide.
     *
     * <p>La tasa de gestion del neumatico fuera de uso se repercute como
     * concepto aparte, no sumada al precio, asi que en el presupuesto es una
     * linea mas. Es la linea que se olvida: nadie echa en falta un euro y medio
     * hasta que la gestoria pregunta.
     *
     * <p>Tantas tasas como piezas, en una sola linea por concepto. Si ya hay una
     * de una pieza anterior se le suma la cantidad en vez de repetir el
     * concepto: dos neumaticos son dos tasas, pero en la factura del cliente eso
     * es una linea de dos unidades, no dos lineas de una.
     *
     * <p>Una tasa en tanto por ciento va sobre lo que se cobra por la pieza, ya
     * con su descuento o su plus.
     *
     * <p>Devuelve la linea de la tasa, o nulo si la pieza no lleva.
     */
    public static <L extends LineaImporte> L anadirTasa(ConLineas<L> orden, List<ReglaCobro> reglas,
                                                        L lineaPieza, BigDecimal cantidad,
                                                        Supplier<TipoIva> tipoIvaDeLaTasa) {
        ReglaCobro regla = ReglaCobro.laQueManda(reglas, ReglaCobro.Tipo.TASA, lineaPieza.getPieza())
                .orElse(null);
        if (regla == null) {
            return null;
        }
        BigDecimal importe = regla.importeDeTasa(lineaPieza.precioNeto());
        L yaPuesta = lineaDeTasa(orden, regla, importe);
        if (yaPuesta != null) {
            yaPuesta.cambiarCantidad(yaPuesta.getCantidad().add(cantidad), null);
            return yaPuesta;
        }
        TipoIva tipoIva = tipoIvaDeLaTasa.get();
        return orden.anadirTasa(regla.getConcepto(), cantidad, importe, tipoIva.getCodigo(),
                tipoIva.getPorcentaje());
    }

    /**
     * Mueve la tasa lo mismo que se ha movido su pieza.
     *
     * <p>Al cambiar la cantidad de un neumatico o quitarlo, la tasa tiene que
     * seguirle: si no, cuatro neumaticos salian con dos tasas, y sin ningun
     * neumatico la tasa se seguia cobrando. Solo se corrige la linea de tasa que
     * ya haya; si alguien la quito a mano, no se vuelve a poner.
     */
    public static <L extends LineaImporte> void moverTasa(ConLineas<L> orden, List<ReglaCobro> reglas,
                                                          L lineaPieza, BigDecimal diferencia) {
        if (lineaPieza.getPieza() == null || diferencia.signum() == 0) {
            return;
        }
        ReglaCobro.laQueManda(reglas, ReglaCobro.Tipo.TASA, lineaPieza.getPieza())
                .map(regla -> lineaDeTasa(orden, regla, regla.importeDeTasa(lineaPieza.precioNeto())))
                .ifPresent(tasa -> {
                    BigDecimal nueva = tasa.getCantidad().add(diferencia);
                    if (nueva.signum() > 0) {
                        tasa.cambiarCantidad(nueva, null);
                    } else {
                        orden.quitarLinea(tasa);
                    }
                });
    }

    /**
     * La linea de la orden que ya cobra esa tasa: se reconoce por su concepto.
     * Una en tanto por ciento, ademas, por su importe: dos neumaticos de precio
     * distinto pagan tasas distintas y no caben en una linea.
     */
    private static <L extends LineaImporte> L lineaDeTasa(ConLineas<L> orden, ReglaCobro regla, BigDecimal importe) {
        return orden.getLineas().stream()
                .filter(l -> l.getTipo() == TipoLinea.TASA
                        && l.getDescripcion().equalsIgnoreCase(regla.getConcepto())
                        && (!regla.enPorcentaje() || l.getPrecioUnitario().compareTo(importe) == 0))
                .findFirst()
                .orElse(null);
    }
}
