package com.motorsport19.taller.factura.web;

import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.factura.domain.EventoFactura;
import com.motorsport19.taller.factura.domain.TipoEventoFactura;
import com.motorsport19.taller.factura.service.RegistroEventosService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Consulta del registro de eventos de facturacion.
 *
 * <p>Solo lectura: la bitacora es append-only y se escribe desde las operaciones
 * que la generan, nunca a mano.
 */
@RestController
@RequestMapping("/facturacion/eventos")
public class EventoFacturaController {

    private final RegistroEventosService registroEventos;

    public EventoFacturaController(RegistroEventosService registroEventos) {
        this.registroEventos = registroEventos;
    }

    @GetMapping
    public PaginaResponse<EventoResponse> buscar(
            @RequestParam(required = false) TipoEventoFactura tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @PageableDefault(size = 50) Pageable pageable) {

        return PaginaResponse.de(registroEventos.buscar(tipo, desde, hasta, pageable), EventoResponse::de);
    }

    @GetMapping("/factura/{facturaId}")
    public List<EventoResponse> deFactura(@PathVariable Long facturaId) {
        return registroEventos.historialDe(facturaId).stream().map(EventoResponse::de).toList();
    }

    public record EventoResponse(
            Long id,
            Long facturaId,
            String numeroFactura,
            TipoEventoFactura tipoEvento,
            String tipoDescripcion,
            Instant fecha,
            String usuarioNombre,
            String descripcion,
            String detalle
    ) {
        static EventoResponse de(EventoFactura e) {
            return new EventoResponse(
                    e.getId(),
                    e.getFactura() == null ? null : e.getFactura().getId(),
                    e.getFactura() == null ? null : e.getFactura().getNumeroCompleto(),
                    e.getTipoEvento(),
                    e.getTipoEvento().getDescripcion(),
                    e.getFecha(),
                    e.getUsuario() == null ? null : e.getUsuario().getNombreCompleto(),
                    e.getDescripcion(),
                    e.getDetalle());
        }
    }
}
