package com.motorsport19.taller.inventario.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Entrada de mercancia por compra a proveedor. */
public record EntradaStockRequest(

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.001", message = "La cantidad de una entrada debe ser mayor que cero")
        BigDecimal cantidad,

        @Size(max = 60, message = "El documento del proveedor no puede superar los 60 caracteres")
        String documentoProveedor,

        @DecimalMin(value = "0.0", message = "El precio de coste no puede ser negativo")
        BigDecimal precioCosteUnitario,

        @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
        String motivo
) {
}
