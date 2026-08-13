package com.motorsport19.taller.servicio.web;

import com.motorsport19.taller.servicio.service.ServicioTipoService;
import com.motorsport19.taller.servicio.web.dto.GuardarServicioTipoRequest;
import com.motorsport19.taller.servicio.web.dto.ServicioTipoResponse;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * Plantillas de servicio.
 *
 * <p>Quien las mantiene es direccion (lo controla {@code ConfiguracionSeguridad});
 * leerlas puede cualquiera con sesion, porque el desplegable de la OT lo usa
 * tambien el mostrador.
 */
@RestController
@RequestMapping("/servicios-tipo")
public class ServicioTipoController {

    private final ServicioTipoService servicioTipoService;

    public ServicioTipoController(ServicioTipoService servicioTipoService) {
        this.servicioTipoService = servicioTipoService;
    }

    /**
     * @param soloActivos por defecto true: el desplegable de la OT no debe
     *                    ofrecer plantillas retiradas. La pantalla de
     *                    mantenimiento pide false para poder reactivarlas.
     */
    @GetMapping
    public List<ServicioTipoResponse> listar(
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        return servicioTipoService.listar(soloActivos).stream()
                .map(ServicioTipoResponse::de)
                .toList();
    }

    @GetMapping("/{id}")
    public ServicioTipoResponse obtener(@PathVariable Long id) {
        return ServicioTipoResponse.de(servicioTipoService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<ServicioTipoResponse> crear(
            @Valid @RequestBody GuardarServicioTipoRequest peticion,
            UriComponentsBuilder uri) {

        ServicioTipoResponse creado = ServicioTipoResponse.de(servicioTipoService.crear(
                peticion.nombre(), peticion.descripcion(), peticion.aLineasPedidas()));

        return ResponseEntity
                .created(uri.path("/servicios-tipo/{id}").buildAndExpand(creado.id()).toUri())
                .body(creado);
    }

    @PutMapping("/{id}")
    public ServicioTipoResponse actualizar(@PathVariable Long id,
                                           @Valid @RequestBody GuardarServicioTipoRequest peticion) {
        return ServicioTipoResponse.de(servicioTipoService.actualizar(
                id, peticion.nombre(), peticion.descripcion(), peticion.aLineasPedidas()));
    }

    /**
     * Alta o baja logica. No hay DELETE: una plantilla retirada sigue
     * explicando por que una OT de hace dos anos tiene esas lineas.
     */
    @PutMapping("/{id}/activo")
    public ServicioTipoResponse cambiarActivo(@PathVariable Long id,
                                              @RequestParam boolean activo) {
        return ServicioTipoResponse.de(servicioTipoService.cambiarActivo(id, activo));
    }
}
