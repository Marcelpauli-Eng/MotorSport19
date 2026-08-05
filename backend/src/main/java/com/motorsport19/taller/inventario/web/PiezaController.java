package com.motorsport19.taller.inventario.web;

import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.inventario.service.PiezaService;
import com.motorsport19.taller.inventario.web.dto.ActualizarPiezaRequest;
import com.motorsport19.taller.inventario.web.dto.ActualizarPreciosRequest;
import com.motorsport19.taller.inventario.web.dto.CrearPiezaRequest;
import com.motorsport19.taller.inventario.web.dto.PiezaResponse;
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
@RequestMapping("/piezas")
public class PiezaController {

    private final PiezaService piezaService;

    public PiezaController(PiezaService piezaService) {
        this.piezaService = piezaService;
    }

    @GetMapping
    public PaginaResponse<PiezaResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(defaultValue = "true") boolean soloActivas,
            @RequestParam(defaultValue = "false") boolean soloBajoMinimo,
            @PageableDefault(size = 20, sort = "sku", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<Pieza> pagina = piezaService.buscar(texto, proveedorId, soloActivas, soloBajoMinimo, pageable);
        return PaginaResponse.de(pagina, PiezaResponse::de);
    }

    @GetMapping("/{id}")
    public PiezaResponse obtener(@PathVariable Long id) {
        return PiezaResponse.de(piezaService.obtener(id));
    }

    /** Busqueda por SKU: es como se pide una pieza en el almacen. */
    @GetMapping("/sku/{sku}")
    public PiezaResponse obtenerPorSku(@PathVariable String sku) {
        return PiezaResponse.de(piezaService.obtenerPorSku(sku));
    }

    @PostMapping
    public ResponseEntity<PiezaResponse> crear(@Valid @RequestBody CrearPiezaRequest peticion,
                                               @RequestParam(required = false) Long usuarioId,
                                               UriComponentsBuilder uriBuilder) {
        Pieza pieza = piezaService.crear(
                peticion.sku(), peticion.descripcion(), peticion.marca(), peticion.ubicacion(),
                peticion.stockMinimo(), peticion.precioCoste(), peticion.precioVenta(), peticion.tipoIva(),
                peticion.proveedorId(), peticion.unidadMedida(), peticion.observaciones(),
                peticion.stockInicial(), usuarioId);

        return ResponseEntity
                .created(uriBuilder.path("/piezas/{id}").build(pieza.getId()))
                .body(PiezaResponse.de(pieza));
    }

    @PutMapping("/{id}")
    public PiezaResponse actualizar(@PathVariable Long id,
                                    @Valid @RequestBody ActualizarPiezaRequest peticion) {
        return PiezaResponse.de(piezaService.actualizar(
                id, peticion.sku(), peticion.descripcion(), peticion.marca(), peticion.ubicacion(),
                peticion.stockMinimo(), peticion.tipoIva(), peticion.proveedorId(), peticion.unidadMedida(),
                peticion.observaciones()));
    }

    @PutMapping("/{id}/precios")
    public PiezaResponse actualizarPrecios(@PathVariable Long id,
                                           @Valid @RequestBody ActualizarPreciosRequest peticion) {
        return PiezaResponse.de(piezaService.actualizarPrecios(
                id, peticion.precioCoste(), peticion.precioVenta()));
    }

    @PostMapping("/{id}/baja")
    public PiezaResponse darDeBaja(@PathVariable Long id) {
        return PiezaResponse.de(piezaService.darDeBaja(id));
    }

    @PostMapping("/{id}/reactivacion")
    public PiezaResponse reactivar(@PathVariable Long id) {
        return PiezaResponse.de(piezaService.reactivar(id));
    }
}
