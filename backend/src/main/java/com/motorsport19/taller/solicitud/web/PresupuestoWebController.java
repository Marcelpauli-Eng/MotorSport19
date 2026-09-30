package com.motorsport19.taller.solicitud.web;

import com.motorsport19.taller.configuracion.service.ConfiguracionTallerService;
import com.motorsport19.taller.documento.ArmadorDocumento;
import com.motorsport19.taller.documento.GeneradorPdfDocumento;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.web.dto.CantidadRequest;
import com.motorsport19.taller.orden.web.dto.DescuentoRequest;
import com.motorsport19.taller.orden.web.dto.LineaOTResponse;
import com.motorsport19.taller.orden.web.dto.ManoDeObraRequest;
import com.motorsport19.taller.orden.web.dto.PiezaLineaRequest;
import com.motorsport19.taller.orden.web.dto.PrecioLineaRequest;
import com.motorsport19.taller.orden.web.dto.TarifaHoraRequest;
import com.motorsport19.taller.orden.web.dto.TipoIvaRequest;
import com.motorsport19.taller.seguridad.UsuarioActual;
import com.motorsport19.taller.solicitud.domain.SolicitudWeb;
import com.motorsport19.taller.solicitud.service.PresupuestoWebService;
import com.motorsport19.taller.solicitud.web.dto.AceptarPresupuestoRequest;
import com.motorsport19.taller.solicitud.web.dto.EnvioPresupuestoRequest;
import com.motorsport19.taller.solicitud.web.dto.NotaRequest;
import com.motorsport19.taller.solicitud.web.dto.PresupuestoWebResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Presupuesto de una solicitud web. Las rutas de las lineas son las mismas que
 * las de una orden, colgando de /solicitudes-web/{id}/presupuesto.
 */
@RestController
@RequestMapping("/solicitudes-web/{id}/presupuesto")
public class PresupuestoWebController {

    private final PresupuestoWebService servicio;
    private final UsuarioActual usuarioActual;
    private final ArmadorDocumento armador;
    private final GeneradorPdfDocumento generadorDocumento;
    private final ConfiguracionTallerService configuracion;

    public PresupuestoWebController(PresupuestoWebService servicio, UsuarioActual usuarioActual,
                                    ArmadorDocumento armador, GeneradorPdfDocumento generadorDocumento,
                                    ConfiguracionTallerService configuracion) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
        this.armador = armador;
        this.generadorDocumento = generadorDocumento;
        this.configuracion = configuracion;
    }

    /** El presupuesto; si aun no estaba empezado, lo empieza. */
    @GetMapping
    public PresupuestoWebResponse obtener(@PathVariable Long id) {
        return PresupuestoWebResponse.de(servicio.abrir(id));
    }

    /** El mismo PDF que el presupuesto de una orden. */
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> pdf(@PathVariable Long id) {
        SolicitudWeb s = servicio.cargar(id);
        byte[] pdf = generadorDocumento.generar(
                armador.presupuestoWeb(s, s.getLineas(), configuracion.obligatoria()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"presupuesto-web-%d.pdf\"".formatted(id))
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(pdf));
    }

    @PutMapping("/tarifa-hora")
    public PresupuestoWebResponse cambiarTarifaHora(@PathVariable Long id, @Valid @RequestBody TarifaHoraRequest p) {
        return PresupuestoWebResponse.de(servicio.cambiarTarifaHora(id, p.tarifaHora()));
    }

    @PutMapping("/descuento-general")
    public PresupuestoWebResponse aplicarDescuentoGeneral(@PathVariable Long id,
                                                          @Valid @RequestBody DescuentoRequest p) {
        return PresupuestoWebResponse.de(servicio.aplicarDescuentoGeneral(id, p.descuentoPct()));
    }

    @PutMapping("/tipo-iva")
    public PresupuestoWebResponse aplicarTipoIva(@PathVariable Long id, @Valid @RequestBody TipoIvaRequest p) {
        return PresupuestoWebResponse.de(servicio.aplicarTipoIva(id, p.tipoIva()));
    }

    @PostMapping("/lineas/mano-de-obra")
    @ResponseStatus(HttpStatus.CREATED)
    public LineaOTResponse anadirManoDeObra(@PathVariable Long id, @Valid @RequestBody ManoDeObraRequest p) {
        return LineaOTResponse.de(servicio.anadirManoDeObra(id, p.descripcion(), p.horas(), p.descuentoPct(),
                p.tipoIva()));
    }

    @PostMapping("/lineas/piezas")
    @ResponseStatus(HttpStatus.CREATED)
    public LineaOTResponse anadirPieza(@PathVariable Long id, @Valid @RequestBody PiezaLineaRequest p) {
        return LineaOTResponse.de(servicio.anadirPieza(id, p.piezaId(), p.cantidad(), p.descuentoPct()));
    }

    @PostMapping("/servicios-tipo/{servicioTipoId}")
    @ResponseStatus(HttpStatus.CREATED)
    public List<LineaOTResponse> aplicarServicioTipo(@PathVariable Long id, @PathVariable Long servicioTipoId) {
        return servicio.aplicarServicioTipo(id, servicioTipoId).stream().map(LineaOTResponse::de).toList();
    }

    @PutMapping("/lineas/{lineaId}/cantidad")
    public LineaOTResponse cambiarCantidad(@PathVariable Long id, @PathVariable Long lineaId,
                                           @Valid @RequestBody CantidadRequest p) {
        return LineaOTResponse.de(servicio.cambiarCantidad(id, lineaId, p.cantidad()));
    }

    @PutMapping("/lineas/{lineaId}/precio")
    public LineaOTResponse cambiarPrecio(@PathVariable Long id, @PathVariable Long lineaId,
                                         @Valid @RequestBody PrecioLineaRequest p) {
        return LineaOTResponse.de(servicio.cambiarPrecio(id, lineaId, p.precioUnitario()));
    }

    @PutMapping("/lineas/{lineaId}/descuento")
    public LineaOTResponse cambiarDescuento(@PathVariable Long id, @PathVariable Long lineaId,
                                            @Valid @RequestBody DescuentoRequest p) {
        return LineaOTResponse.de(servicio.cambiarDescuento(id, lineaId, p.descuentoPct()));
    }

    @DeleteMapping("/lineas/{lineaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarLinea(@PathVariable Long id, @PathVariable Long lineaId) {
        servicio.quitarLinea(id, lineaId);
    }

    /** Apunta que se le ha mandado (el mensaje lo manda quien lo atiende). */
    @PostMapping("/envio")
    public PresupuestoWebResponse enviar(@PathVariable Long id, @Valid @RequestBody EnvioPresupuestoRequest p) {
        return PresupuestoWebResponse.de(servicio.enviar(id, p.canal(), usuarioActual.id()));
    }

    /** Vuelve a pendiente para corregirlo. */
    @PostMapping("/reescritura")
    public PresupuestoWebResponse reescribir(@PathVariable Long id) {
        return PresupuestoWebResponse.de(servicio.reescribir(id));
    }

    @PostMapping("/rechazo")
    public PresupuestoWebResponse rechazar(@PathVariable Long id,
                                           @Valid @RequestBody(required = false) NotaRequest p) {
        return PresupuestoWebResponse.de(servicio.rechazar(id, p == null ? null : p.nota(), usuarioActual.id()));
    }

    public record OrdenAbierta(Long id, String codigo) {
    }

    /** El cliente acepta: se abre la orden, ya aprobada, con estas lineas. */
    @PostMapping("/aceptacion")
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenAbierta aceptar(@PathVariable Long id, @Valid @RequestBody AceptarPresupuestoRequest p) {
        OrdenTrabajo orden = servicio.aceptar(id, p.motoId(), p.kmEntrada(), p.fechaEstimadaSalida(),
                p.tecnicoId(), p.observaciones(), usuarioActual.id());
        return new OrdenAbierta(orden.getId(), orden.codigoVisible());
    }
}
