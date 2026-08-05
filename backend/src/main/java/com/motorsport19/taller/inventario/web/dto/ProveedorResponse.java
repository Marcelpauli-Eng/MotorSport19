package com.motorsport19.taller.inventario.web.dto;

import com.motorsport19.taller.inventario.domain.Proveedor;

import java.time.Instant;

public record ProveedorResponse(
        Long id,
        String nombre,
        String nif,
        String direccion,
        String codigoPostal,
        String ciudad,
        String provincia,
        String telefono,
        String email,
        String observaciones,
        boolean activo,
        Instant fechaBaja
) {

    public static ProveedorResponse de(Proveedor proveedor) {
        return new ProveedorResponse(
                proveedor.getId(),
                proveedor.getNombre(),
                proveedor.getNif(),
                proveedor.getDireccion(),
                proveedor.getCodigoPostal(),
                proveedor.getCiudad(),
                proveedor.getProvincia(),
                proveedor.getTelefono(),
                proveedor.getEmail(),
                proveedor.getObservaciones(),
                proveedor.isActivo(),
                proveedor.getFechaBaja());
    }
}
