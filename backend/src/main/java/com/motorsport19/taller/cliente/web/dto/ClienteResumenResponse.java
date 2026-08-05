package com.motorsport19.taller.cliente.web.dto;

import com.motorsport19.taller.cliente.domain.Cliente;

/**
 * Version reducida para listados y desplegables: lo justo para identificar al
 * cliente sin arrastrar toda la ficha en cada fila de la tabla.
 */
public record ClienteResumenResponse(
        Long id,
        String nombreCompleto,
        String documento,
        String telefono,
        String email,
        boolean activo,
        boolean facturable
) {

    public static ClienteResumenResponse de(Cliente cliente) {
        return new ClienteResumenResponse(
                cliente.getId(),
                cliente.nombreCompleto(),
                cliente.getDocumento(),
                cliente.getTelefono(),
                cliente.getEmail(),
                cliente.isActivo(),
                cliente.tieneDatosFiscalesCompletos());
    }
}
