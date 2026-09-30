package com.motorsport19.taller.solicitud.web;

import com.motorsport19.taller.agenda.web.dto.GuardarCitaRequest;
import com.motorsport19.taller.seguridad.UsuarioActual;
import com.motorsport19.taller.solicitud.domain.EstadoSolicitud;
import com.motorsport19.taller.solicitud.domain.FotoSolicitud;
import com.motorsport19.taller.solicitud.service.SolicitudWebService;
import com.motorsport19.taller.solicitud.web.dto.NotaRequest;
import com.motorsport19.taller.solicitud.web.dto.SolicitudWebResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Bandeja de solicitudes de la web, para quien atiende mostrador. */
@RestController
@RequestMapping("/solicitudes-web")
public class SolicitudWebController {

    private final SolicitudWebService servicio;
    private final UsuarioActual usuarioActual;

    public SolicitudWebController(SolicitudWebService servicio, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    public List<SolicitudWebResponse> bandeja(
            @RequestParam(defaultValue = "PENDIENTE") EstadoSolicitud estado) {
        return servicio.bandeja(estado).stream().map(SolicitudWebResponse::de).toList();
    }

    /** Para el numero del menu. */
    @GetMapping("/pendientes")
    public Pendientes pendientes() {
        return new Pendientes(servicio.pendientes());
    }

    public record Pendientes(long total) {
    }

    @GetMapping("/{id}")
    public SolicitudWebResponse obtener(@PathVariable Long id) {
        return SolicitudWebResponse.de(servicio.obtener(id));
    }

    /** La foto tal cual la mando el cliente. No cambia nunca: se puede guardar en cache. */
    @GetMapping("/{id}/fotos/{orden}")
    public ResponseEntity<byte[]> foto(@PathVariable Long id, @PathVariable int orden) {
        FotoSolicitud foto = servicio.foto(id, orden);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.getTipoContenido()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate())
                .body(foto.getDatos());
    }

    /** Le da cita en la agenda con los datos del formulario de la cita. */
    @PostMapping("/{id}/cita")
    public SolicitudWebResponse darCita(@PathVariable Long id, @Valid @RequestBody GuardarCitaRequest p) {
        return SolicitudWebResponse.de(servicio.darCita(id, p.fechaHora(), p.duracionEstimada(),
                p.motoId(), p.clienteId(), p.contactoNombre(), p.contactoTelefono(),
                p.descripcionMoto(), p.motivo(), p.tecnicoId(), p.observaciones(), usuarioActual.id()));
    }

    @PostMapping("/{id}/atencion")
    public SolicitudWebResponse marcarAtendida(@PathVariable Long id,
                                               @Valid @RequestBody(required = false) NotaRequest p) {
        return SolicitudWebResponse.de(servicio.marcarAtendida(id, p == null ? null : p.nota(), usuarioActual.id()));
    }

    @PostMapping("/{id}/descarte")
    public SolicitudWebResponse descartar(@PathVariable Long id,
                                          @Valid @RequestBody(required = false) NotaRequest p) {
        return SolicitudWebResponse.de(servicio.descartar(id, p == null ? null : p.nota(), usuarioActual.id()));
    }
}
