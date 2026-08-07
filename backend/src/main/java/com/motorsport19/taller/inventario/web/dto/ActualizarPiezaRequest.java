package com.motorsport19.taller.inventario.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Modificacion del catalogo. No incluye existencias ni precios: el stock solo
 * cambia con movimientos y los precios tienen su propio endpoint.
 */
public record ActualizarPiezaRequest(

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

        @Size(max = 20, message = "El tipo de IVA no puede superar los 20 caracteres")
        String tipoIva,

        Long proveedorId,

        @Size(max = 10, message = "La unidad de medida no puede superar los 10 caracteres")
        String unidadMedida,

        String observaciones
) {
}
