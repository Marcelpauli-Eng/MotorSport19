package com.motorsport19.taller.inventario.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.util.ValidadorDocumento;
import com.motorsport19.taller.inventario.domain.Proveedor;
import com.motorsport19.taller.inventario.repository.ProveedorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    public ProveedorService(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    @Transactional(readOnly = true)
    public Proveedor obtener(Long id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el proveedor", id));
    }

    @Transactional(readOnly = true)
    public Page<Proveedor> buscar(String texto, boolean soloActivos, Pageable pageable) {
        String filtro = (texto == null || texto.isBlank()) ? null : texto.trim();
        return proveedorRepository.buscar(filtro, soloActivos, pageable);
    }

    @Transactional
    public Proveedor crear(String nombre, String nif, String direccion, String codigoPostal, String ciudad,
                           String provincia, String telefono, String email, String observaciones) {
        String nifNormalizado = ValidadorDocumento.normalizar(nif);
        if (nifNormalizado != null && proveedorRepository.existeConNif(nifNormalizado)) {
            throw new ConflictoException("Ya existe un proveedor con el NIF %s.".formatted(nifNormalizado));
        }
        Proveedor proveedor = Proveedor.registrar(nombre, nif, direccion, codigoPostal, ciudad, provincia,
                telefono, email, observaciones);
        return proveedorRepository.save(proveedor);
    }

    @Transactional
    public Proveedor actualizar(Long id, String nombre, String nif, String direccion, String codigoPostal,
                                String ciudad, String provincia, String telefono, String email,
                                String observaciones) {
        Proveedor proveedor = obtener(id);
        String nifNormalizado = ValidadorDocumento.normalizar(nif);
        if (nifNormalizado != null && proveedorRepository.existeOtroConNif(nifNormalizado, id)) {
            throw new ConflictoException("Ya existe otro proveedor con el NIF %s.".formatted(nifNormalizado));
        }
        proveedor.actualizarDatos(nombre, nif, direccion, codigoPostal, ciudad, provincia, telefono, email,
                observaciones);
        return proveedor;
    }

    @Transactional
    public Proveedor darDeBaja(Long id) {
        Proveedor proveedor = obtener(id);
        proveedor.darDeBaja();
        return proveedor;
    }

    @Transactional
    public Proveedor reactivar(Long id) {
        Proveedor proveedor = obtener(id);
        proveedor.reactivar();
        return proveedor;
    }
}
