package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.TipoLinea;

import java.math.BigDecimal;

/**
 * Linea que va a copiarse a una factura.
 *
 * <p>Es una copia, no una referencia: se toma de la orden de trabajo (o la aporta
 * quien emite una rectificativa) y a partir de ahi vive por su cuenta. El SKU
 * viaja como texto, sin clave ajena al catalogo, para que la factura siga siendo
 * legible dentro de veinte anos aunque la pieza ya no exista.
 *
 * @param cantidad puede ser negativa en una rectificativa por diferencias
 */
public record LineaAFacturar(
        TipoLinea tipo,
        String descripcion,
        String piezaSku,
        BigDecimal cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuentoPct,
        String tipoIva,
        BigDecimal porcentajeIva
) {

    public LineaAFacturar {
        if (tipo == null) {
            throw new ReglaNegocioException("La linea de factura necesita un tipo.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new ReglaNegocioException("La linea de factura necesita una descripcion.");
        }
        if (cantidad == null || cantidad.signum() == 0) {
            throw new ReglaNegocioException("La cantidad de una linea de factura no puede ser cero.");
        }
        if (precioUnitario == null || precioUnitario.signum() < 0) {
            throw new ReglaNegocioException("El precio unitario no puede ser negativo.");
        }
        if (porcentajeIva == null || porcentajeIva.signum() < 0) {
            throw new ReglaNegocioException("El porcentaje de IVA no puede ser negativo.");
        }
        descuentoPct = descuentoPct == null ? BigDecimal.ZERO : descuentoPct;
    }

    /** Copia una linea de orden de trabajo tal y como quedo congelada en ella. */
    public static LineaAFacturar copiaDe(LineaOT linea) {
        return new LineaAFacturar(
                linea.getTipo(),
                linea.getDescripcion(),
                linea.skuPieza(),
                linea.getCantidad(),
                linea.getPrecioUnitario(),
                linea.getDescuentoPct(),
                linea.getTipoIva(),
                linea.getPorcentajeIva());
    }

    /** La misma linea con la cantidad cambiada de signo, para rectificar. */
    public LineaAFacturar negada() {
        return new LineaAFacturar(tipo, descripcion, piezaSku, cantidad.negate(), precioUnitario,
                descuentoPct, tipoIva, porcentajeIva);
    }

    public ImporteLinea importes() {
        return ImporteLinea.de(cantidad, precioUnitario, descuentoPct, porcentajeIva);
    }
}
