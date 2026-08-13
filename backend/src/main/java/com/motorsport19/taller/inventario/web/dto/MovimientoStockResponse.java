package com.motorsport19.taller.inventario.web.dto;

import com.motorsport19.taller.inventario.domain.MovimientoStock;
import com.motorsport19.taller.inventario.domain.TipoMovimiento;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Asiento del libro de movimientos.
 *
 * @param stockAnterior   existencias antes del movimiento
 * @param stockResultante existencias despues; ambos los calcula la base de datos
 *                        y permiten auditar la trazabilidad sin recalcular toda
 *                        la serie de movimientos
 * @param ordenCodigo     orden a la que se fue el material, si salio por una
 *                        reparacion. Con la matricula y la moto al lado, porque
 *                        cuando un recuento no cuadra lo primero que se pregunta
 *                        no es cuantas salieron sino para quien
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
        String ordenCodigo,
        String matricula,
        String descripcionMoto,
        String motivo,
        String documentoProveedor,
        BigDecimal precioCosteUnitario
) {

    public static MovimientoStockResponse de(MovimientoStock movimiento) {
        OrdenTrabajo orden = movimiento.getOrdenTrabajo();
        Moto moto = orden == null ? null : orden.getMoto();

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
                orden == null ? null : orden.getId(),
                orden == null ? null : orden.codigoVisible(),
                moto == null ? null : moto.getMatricula(),
                moto == null ? null : moto.descripcion(),
                movimiento.getMotivo(),
                movimiento.getDocumentoProveedor(),
                movimiento.getPrecioCosteUnitario());
    }

    /** El asiento sin el coste, para quien no tiene por que ver lo que paga el taller. */
    public MovimientoStockResponse sinPrecio() {
        return new MovimientoStockResponse(
                id, piezaId, piezaSku, piezaDescripcion, tipo, tipoDescripcion,
                cantidad, stockAnterior, stockResultante, fecha,
                usuarioId, usuarioNombre, ordenTrabajoId, ordenCodigo, matricula, descripcionMoto,
                motivo, documentoProveedor, null);
    }
}
