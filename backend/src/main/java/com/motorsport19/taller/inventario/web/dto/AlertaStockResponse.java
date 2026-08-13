package com.motorsport19.taller.inventario.web.dto;

import com.motorsport19.taller.inventario.domain.Pieza;

import java.math.BigDecimal;

/**
 * Fila del panel de alertas de reposicion.
 *
 * @param unidadesAReponer cuantas faltan para volver al minimo; es el dato con
 *                         el que se prepara el pedido al proveedor
 */
public record AlertaStockResponse(
        Long piezaId,
        String sku,
        String descripcion,
        String marca,
        String ubicacion,
        BigDecimal stockActual,
        BigDecimal stockMinimo,
        BigDecimal unidadesAReponer,
        boolean sinExistencias,
        Long proveedorId,
        String proveedorNombre,
        BigDecimal precioCoste
) {

    public static AlertaStockResponse de(Pieza pieza) {
        return new AlertaStockResponse(
                pieza.getId(),
                pieza.getSku(),
                pieza.getDescripcion(),
                pieza.getMarca(),
                pieza.getUbicacion(),
                pieza.existencias(),
                pieza.getStockMinimo(),
                pieza.getStockMinimo().subtract(pieza.existencias()).max(BigDecimal.ZERO),
                pieza.sinExistencias(),
                pieza.getProveedor() == null ? null : pieza.getProveedor().getId(),
                pieza.getProveedor() == null ? null : pieza.getProveedor().getNombre(),
                pieza.getPrecioCoste());
    }

    /** La alerta sin el coste: el panel del tecnico dice que falta, no cuanto vale. */
    public AlertaStockResponse sinPrecio() {
        return new AlertaStockResponse(
                piezaId, sku, descripcion, marca, ubicacion,
                stockActual, stockMinimo, unidadesAReponer, sinExistencias,
                proveedorId, proveedorNombre, null);
    }
}
