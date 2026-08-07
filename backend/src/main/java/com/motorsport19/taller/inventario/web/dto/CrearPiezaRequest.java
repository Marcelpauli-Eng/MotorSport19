package com.motorsport19.taller.inventario.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Alta de pieza.
 *
 * @param stockInicial existencias con las que arranca. No se escribe en la
 *                     pieza: genera un movimiento de ENTRADA, para que el libro
 *                     de movimientos explique tambien la primera unidad.
 */
public record CrearPiezaRequest(

        @NotBlank(message = "El SKU es obligatorio")
        @Size(max = 50, message = "El SKU no puede superar los 50 caracteres")
        String sku,

        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 200, message = "La descripcion no puede superar los 200 caracteres")
        String descripcion,

        @Size(max = 60, message = "La marca no puede superar los 60 caracteres")
        String marca,

        @Size(max = 50, message = "La ubicacion no puede superar los 50 caracteres")
        String ubicacion,

        @Size(max = 60, message = "La familia no puede superar los 60 caracteres")
        String familia,

        @NotNull(message = "El stock minimo es obligatorio")
        @DecimalMin(value = "0.0", message = "El stock minimo no puede ser negativo")
        BigDecimal stockMinimo,

        @NotNull(message = "El precio de coste es obligatorio")
        @DecimalMin(value = "0.0", message = "El precio de coste no puede ser negativo")
        BigDecimal precioCoste,

        @NotNull(message = "El precio de venta es obligatorio")
        @DecimalMin(value = "0.0", message = "El precio de venta no puede ser negativo")
        BigDecimal precioVenta,

        @Size(max = 20, message = "El tipo de IVA no puede superar los 20 caracteres")
        String tipoIva,

        Long proveedorId,

        @Size(max = 10, message = "La unidad de medida no puede superar los 10 caracteres")
        String unidadMedida,

        String observaciones,

        @DecimalMin(value = "0.0", message = "El stock inicial no puede ser negativo")
        BigDecimal stockInicial
) {
}
