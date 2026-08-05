package com.motorsport19.taller.inventario.web.dto;

import com.motorsport19.taller.inventario.domain.MovimientoStock;
import com.motorsport19.taller.inventario.domain.TipoMovimiento;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Asiento del libro de movimientos.
 *
 * @param stockAnterior   existencias antes del movimiento
 * @param stockResultante existencias despues; ambos los calcula la base de datos
 *                        y permiten auditar la trazabilidad sin recalcular toda
 *                        la serie de movimientos
 */
public record MovimientoStockResponse(
        Long id,
        Long piezaId,
        String piezaSku,
        String piezaDescripcion,
        TipoMovimiento tipo,
        String tipoDescripcion,
        BigDecimal cantidad,
        BigDecimal stockAnterior,
        BigDecimal stockResultante,
        Instant fecha,
        Long usuarioId,
        String usuarioNombre,
        Long ordenTrabajoId,
        String motivo,
        String documentoProveedor,
        BigDecimal precioCosteUnitario
) {

    public static MovimientoStockResponse de(MovimientoStock movimiento) {
        return new MovimientoStockResponse(
                movimiento.getId(),
                movimiento.getPieza().getId(),
                movimiento.getPieza().getSku(),
                movimiento.getPieza().getDescripcion(),
                movimiento.getTipo(),
                movimiento.getTipo().getDescripcion(),
                movimiento.getCantidad(),
                movimiento.getStockAnterior(),
                movimiento.getStockResultante(),
                movimiento.getFecha(),
                movimiento.getUsuario() == null ? null : movimiento.getUsuario().getId(),
                movimiento.getUsuario() == null ? null : movimiento.getUsuario().getNombreCompleto(),
                movimiento.getOrdenTrabajo() == null ? null : movimiento.getOrdenTrabajo().getId(),
                movimiento.getMotivo(),
                movimiento.getDocumentoProveedor(),
                movimiento.getPrecioCosteUnitario());
    }
}
