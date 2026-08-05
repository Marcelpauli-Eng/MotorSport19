package com.motorsport19.taller.inventario.web;

import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.inventario.domain.Proveedor;
import com.motorsport19.taller.inventario.service.ProveedorService;
import com.motorsport19.taller.inventario.web.dto.ProveedorRequest;
import com.motorsport19.taller.inventario.web.dto.ProveedorResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    public PaginaResponse<ProveedorResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "true") boolean soloActivos,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<Proveedor> pagina = proveedorService.buscar(texto, soloActivos, pageable);
        return PaginaResponse.de(pagina, ProveedorResponse::de);
    }

    @GetMapping("/{id}")
    public ProveedorResponse obtener(@PathVariable Long id) {
        return ProveedorResponse.de(proveedorService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<ProveedorResponse> crear(@Valid @RequestBody ProveedorRequest peticion,
                                                   UriComponentsBuilder uriBuilder) {
        Proveedor proveedor = proveedorService.crear(
                peticion.nombre(), peticion.nif(), peticion.direccion(), peticion.codigoPostal(),
                peticion.ciudad(), peticion.provincia(), peticion.telefono(), peticion.email(),
                peticion.observaciones());

        return ResponseEntity
                .created(uriBuilder.path("/proveedores/{id}").build(proveedor.getId()))
                .body(ProveedorResponse.de(proveedor));
    }

    @PutMapping("/{id}")
    public ProveedorResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProveedorRequest peticion) {
        return ProveedorResponse.de(proveedorService.actualizar(
                id, peticion.nombre(), peticion.nif(), peticion.direccion(), peticion.codigoPostal(),
                peticion.ciudad(), peticion.provincia(), peticion.telefono(), peticion.email(),
                peticion.observaciones()));
    }

    @PostMapping("/{id}/baja")
    public ProveedorResponse darDeBaja(@PathVariable Long id) {
        return ProveedorResponse.de(proveedorService.darDeBaja(id));
    }

    @PostMapping("/{id}/reactivacion")
    public ProveedorResponse reactivar(@PathVariable Long id) {
        return ProveedorResponse.de(proveedorService.reactivar(id));
    }
}
