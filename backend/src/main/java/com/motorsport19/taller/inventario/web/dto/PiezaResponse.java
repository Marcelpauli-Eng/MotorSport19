package com.motorsport19.taller.inventario.web.dto;

import com.motorsport19.taller.inventario.domain.Pieza;

import java.math.BigDecimal;
import java.time.Instant;

public record PiezaResponse(
        Long id,
        String sku,
        String descripcion,
        String marca,
        String ubicacion,
        String familia,
        BigDecimal stockActual,
        BigDecimal stockMinimo,
        boolean bajoMinimo,
        boolean sinExistencias,
        BigDecimal precioCoste,
        BigDecimal precioVenta,
        String tipoIva,
        Long proveedorId,
        String proveedorNombre,
        String unidadMedida,
        String observaciones,
        boolean activo,
        Instant fechaBaja
) {

    public static PiezaResponse de(Pieza pieza) {
        return new PiezaResponse(
                pieza.getId(),
                pieza.getSku(),
                pieza.getDescripcion(),
                pieza.getMarca(),
                pieza.getUbicacion(),
                pieza.getFamilia(),
                pieza.existencias(),
                pieza.getStockMinimo(),
                pieza.estaBajoMinimo(),
                pieza.sinExistencias(),
                pieza.getPrecioCoste(),
                pieza.getPrecioVenta(),
                pieza.getTipoIva(),
                pieza.getProveedor() == null ? null : pieza.getProveedor().getId(),
                pieza.getProveedor() == null ? null : pieza.getProveedor().getNombre(),
                pieza.getUnidadMedida(),
                pieza.getObservaciones(),
                pieza.isActivo(),
                pieza.getFechaBaja());
    }
}
