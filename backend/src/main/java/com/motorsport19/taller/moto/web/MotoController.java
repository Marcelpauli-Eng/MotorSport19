package com.motorsport19.taller.moto.web;

import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.moto.service.MotoService;
import com.motorsport19.taller.moto.web.dto.ActualizarMotoRequest;
import com.motorsport19.taller.moto.web.dto.CambioPropietarioRequest;
import com.motorsport19.taller.moto.web.dto.CrearMotoRequest;
import com.motorsport19.taller.moto.web.dto.KilometrajeRequest;
import com.motorsport19.taller.moto.web.dto.MotoResponse;
import com.motorsport19.taller.moto.web.dto.MotoResumenResponse;
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
@RequestMapping("/motos")
public class MotoController {

    private final MotoService motoService;

    public MotoController(MotoService motoService) {
        this.motoService = motoService;
    }

    @GetMapping
    public PaginaResponse<MotoResumenResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "true") boolean soloActivas,
            @PageableDefault(size = 20, sort = "matricula", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<Moto> pagina = motoService.buscar(texto, soloActivas, pageable);
        return PaginaResponse.de(pagina, MotoResumenResponse::de);
    }

    @GetMapping("/{id}")
    public MotoResponse obtener(@PathVariable Long id) {
        return MotoResponse.de(motoService.obtener(id));
    }

    /** Busqueda directa por matricula: es como llega la moto al mostrador. */
    @GetMapping("/matricula/{matricula}")
    public MotoResponse obtenerPorMatricula(@PathVariable String matricula) {
        return MotoResponse.de(motoService.obtenerPorMatricula(matricula));
    }

    @PostMapping
    public ResponseEntity<MotoResponse> crear(@Valid @RequestBody CrearMotoRequest peticion,
                                              UriComponentsBuilder uriBuilder) {
        Moto moto = motoService.crear(
                peticion.clienteId(), peticion.matricula(), peticion.marca(), peticion.modelo(),
                peticion.anio(), peticion.cilindrada(), peticion.color(), peticion.numeroBastidor(),
                peticion.kmActual(), peticion.observaciones());

        return ResponseEntity
                .created(uriBuilder.path("/motos/{id}").build(moto.getId()))
                .body(MotoResponse.de(moto));
    }

    @PutMapping("/{id}")
    public MotoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarMotoRequest peticion) {
        return MotoResponse.de(motoService.actualizar(
                id, peticion.matricula(), peticion.marca(), peticion.modelo(), peticion.anio(),
                peticion.cilindrada(), peticion.color(), peticion.numeroBastidor(), peticion.observaciones()));
    }

    /** El cuentakilometros no retrocede: una lectura menor se rechaza. */
    @PutMapping("/{id}/kilometraje")
    public MotoResponse registrarKilometraje(@PathVariable Long id,
                                             @Valid @RequestBody KilometrajeRequest peticion) {
        return MotoResponse.de(motoService.registrarKilometraje(id, peticion.km()));
    }

    /** Cambio de propietario. El historial de la moto se queda con la moto. */
    @PutMapping("/{id}/propietario")
    public MotoResponse cambiarPropietario(@PathVariable Long id,
                                           @Valid @RequestBody CambioPropietarioRequest peticion) {
        return MotoResponse.de(motoService.cambiarPropietario(id, peticion.nuevoClienteId()));
    }

    @PostMapping("/{id}/baja")
    public MotoResponse darDeBaja(@PathVariable Long id) {
        return MotoResponse.de(motoService.darDeBaja(id));
    }

    @PostMapping("/{id}/reactivacion")
    public MotoResponse reactivar(@PathVariable Long id) {
        return MotoResponse.de(motoService.reactivar(id));
    }
}
