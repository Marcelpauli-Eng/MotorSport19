package com.motorsport19.taller.solicitud.web;

import com.motorsport19.taller.solicitud.service.SolicitudWebService;
import com.motorsport19.taller.solicitud.web.dto.NuevaSolicitudRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Donde la web publica deja las solicitudes. La clave la comprueba
 * {@link FiltroClaveWeb} antes de llegar aqui.
 */
@RestController
@RequestMapping("/publico/solicitudes-web")
public class EntradaSolicitudesController {

    private final SolicitudWebService servicio;

    public EntradaSolicitudesController(SolicitudWebService servicio) {
        this.servicio = servicio;
    }

    public record Recibida(Long id, String referencia) {
    }

    @PostMapping
    public ResponseEntity<Recibida> recibir(@Valid @RequestBody NuevaSolicitudRequest peticion) {
        var solicitud = servicio.recibir(peticion.entrada());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Recibida(solicitud.getId(), solicitud.getReferencia()));
    }
}
