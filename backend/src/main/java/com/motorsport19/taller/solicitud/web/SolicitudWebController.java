package com.motorsport19.taller.solicitud.web;

import com.motorsport19.taller.agenda.web.dto.GuardarCitaRequest;
import com.motorsport19.taller.seguridad.UsuarioActual;
import com.motorsport19.taller.solicitud.domain.EstadoSolicitud;
import com.motorsport19.taller.solicitud.domain.FotoSolicitud;
import com.motorsport19.taller.solicitud.service.SolicitudWebService;
import com.motorsport19.taller.solicitud.domain.SolicitudWeb;
import com.motorsport19.taller.solicitud.web.dto.NotaRequest;
import com.motorsport19.taller.solicitud.web.dto.PresupuestoRequest;
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
        return servicio.bandeja(estado).stream().map(this::respuesta).toList();
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
        return respuesta(servicio.obtener(id));
    }

    /**
     * La foto tal cual la mando el cliente.
     *
     * <p>Sin cache a proposito. La direccion lleva el numero de la solicitud, y
     * tras restaurar una copia de seguridad ese numero puede volver a usarse: el
     * navegador enseñaria la foto de otro cliente. Ademas son fotos de clientes
     * y no tienen por que quedarse en el disco del ordenador del mostrador.
     */
    @GetMapping("/{id}/fotos/{orden}")
    public ResponseEntity<byte[]> foto(@PathVariable Long id, @PathVariable int orden) {
        FotoSolicitud foto = servicio.foto(id, orden);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.getTipoContenido()))
                .cacheControl(CacheControl.noStore())
                .body(foto.getDatos());
    }

    /** Le da cita en la agenda con los datos del formulario de la cita. */
    @PostMapping("/{id}/cita")
    public SolicitudWebResponse darCita(@PathVariable Long id, @Valid @RequestBody GuardarCitaRequest p) {
        return respuesta(servicio.darCita(id, p.fechaHora(), p.duracionEstimada(),
                p.motoId(), p.clienteId(), p.contactoNombre(), p.contactoTelefono(),
                p.descripcionMoto(), p.motivo(), p.tecnicoId(), p.observaciones(), usuarioActual.id()));
    }

    /**
     * Apunta el presupuesto que se le manda. El mensaje lo envia quien lo atiende,
     * desde su WhatsApp o su correo; la solicitud queda presupuestada, a la espera
     * de que el cliente conteste.
     */
    @PostMapping("/{id}/presupuesto")
    public SolicitudWebResponse enviarPresupuesto(@PathVariable Long id,
                                                  @Valid @RequestBody PresupuestoRequest p) {
        return respuesta(servicio.enviarPresupuesto(id, p.importe(), p.detalle(), p.canal(), usuarioActual.id()));
    }

    @PostMapping("/{id}/atencion")
    public SolicitudWebResponse marcarAtendida(@PathVariable Long id,
                                               @Valid @RequestBody(required = false) NotaRequest p) {
        return respuesta(servicio.marcarAtendida(id, p == null ? null : p.nota(), usuarioActual.id()));
    }

    @PostMapping("/{id}/descarte")
    public SolicitudWebResponse descartar(@PathVariable Long id,
                                          @Valid @RequestBody(required = false) NotaRequest p) {
        return respuesta(servicio.descartar(id, p == null ? null : p.nota(), usuarioActual.id()));
    }

    /** Quien no puede ver dinero ve la ficha sin el importe del presupuesto. */
    private SolicitudWebResponse respuesta(SolicitudWeb s) {
        SolicitudWebResponse r = SolicitudWebResponse.de(s);
        return usuarioActual.sinImportes() ? r.sinImportes() : r;
    }
}
