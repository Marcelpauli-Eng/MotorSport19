package com.motorsport19.taller.servicio.web.dto;

import com.motorsport19.taller.servicio.service.ServicioTipoService.LineaPedida;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Alta y edicion de una plantilla. La misma forma para las dos: la pantalla
 * monta la lista de lineas entera y la manda de una vez.
 */
public record GuardarServicioTipoRequest(

        @NotBlank(message = "El servicio tipo necesita un nombre.")
        @Size(max = 120)
        String nombre,

        @Size(max = 400)
        String descripcion,

        @NotEmpty(message = "Un servicio tipo sin lineas no ahorra nada: anade al menos una.")
        @Valid
        List<LineaRequest> lineas
) {

    public List<LineaPedida> aLineasPedidas() {
        return lineas.stream()
                .map(l -> new LineaPedida(l.descripcion(), l.piezaId(), l.cantidad()))
                .toList();
    }

    /**
     * Una linea: o lleva {@code piezaId} (y es material), o lleva
     * {@code descripcion} (y es mano de obra). Que sea uno u otro lo comprueba
     * el dominio, que es donde vive la regla; aqui solo se validan formatos.
     */
    public record LineaRequest(

            @Size(max = 300)
            String descripcion,

            Long piezaId,

            @NotNull(message = "Cada linea necesita una cantidad.")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor que cero.")
            BigDecimal cantidad
    ) {}
}
