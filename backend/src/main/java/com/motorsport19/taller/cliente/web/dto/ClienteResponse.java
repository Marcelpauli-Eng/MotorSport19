package com.motorsport19.taller.cliente.web.dto;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.cliente.domain.TipoDocumento;

import java.time.Instant;

/**
 * Ficha completa del cliente.
 *
 * @param facturable indica si reune los datos fiscales que exige una factura;
 *                   se calcula aqui para que el mostrador no tenga que
 *                   deducirlo campo a campo
 */
public record ClienteResponse(
        Long id,
        String nombre,
        String apellidos,
        String nombreCompleto,
        TipoDocumento tipoDocumento,
        String documento,
        String direccion,
        String codigoPostal,
        String ciudad,
        String provincia,
        String pais,
        String email,
        String telefono,
        String observaciones,
        boolean activo,
        Instant fechaBaja,
        boolean facturable
) {

    public static ClienteResponse de(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNombre(),
                cliente.getApellidos(),
                cliente.nombreCompleto(),
                cliente.getTipoDocumento(),
                cliente.getDocumento(),
                cliente.getDireccion(),
                cliente.getCodigoPostal(),
                cliente.getCiudad(),
                cliente.getProvincia(),
                cliente.getPais(),
                cliente.getEmail(),
                cliente.getTelefono(),
                cliente.getObservaciones(),
                cliente.isActivo(),
                cliente.getFechaBaja(),
                cliente.tieneDatosFiscalesCompletos());
    }
}
