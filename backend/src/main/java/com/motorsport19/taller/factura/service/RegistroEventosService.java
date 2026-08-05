package com.motorsport19.taller.factura.service;

import com.motorsport19.taller.factura.domain.EventoFactura;
import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.TipoEventoFactura;
import com.motorsport19.taller.factura.repository.EventoFacturaRepository;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Bitacora del subsistema de facturacion.
 *
 * <p>Anota emisiones, rectificaciones, PDF generados, exportaciones,
 * verificaciones de la cadena e incidencias. La tabla es append-only y los
 * triggers de la base de datos rechazan UPDATE y DELETE, asi que lo que se
 * escribe aqui no se puede maquillar despues.
 */
@Service
public class RegistroEventosService {

    private static final Logger log = LoggerFactory.getLogger(RegistroEventosService.class);

    private final EventoFacturaRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;

    public RegistroEventosService(EventoFacturaRepository eventoRepository,
                                  UsuarioRepository usuarioRepository) {
        this.eventoRepository = eventoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Anota un evento dentro de la transaccion en curso.
     *
     * <p>Se usa para lo que forma parte de la operacion: si la emision de la
     * factura se deshace, su anotacion tambien.
     */
    @Transactional
    public EventoFactura anotar(Factura factura, TipoEventoFactura tipo, Long usuarioId,
                                String descripcion, String detalleJson, String ipOrigen) {
        Usuario usuario = usuarioId == null ? null : usuarioRepository.findById(usuarioId).orElse(null);
        return eventoRepository.save(
                EventoFactura.registrar(factura, tipo, usuario, descripcion, detalleJson, ipOrigen));
    }

    /**
     * Anota un evento en su propia transaccion, pase lo que pase con la actual.
     *
     * <p>Para incidencias: si algo falla y la operacion se deshace, el rastro del
     * fallo debe quedar igualmente. Sin esto, el unico caso en que de verdad
     * interesa tener bitacora seria justo el que no la deja.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventoFactura anotarIncidencia(Factura factura, Long usuarioId, String descripcion,
                                          String detalleJson) {
        log.warn("Incidencia de facturacion: {}", descripcion);
        return anotar(factura, TipoEventoFactura.INCIDENCIA, usuarioId, descripcion, detalleJson, null);
    }

    @Transactional(readOnly = true)
    public List<EventoFactura> historialDe(Long facturaId) {
        return eventoRepository.buscarPorFactura(facturaId);
    }

    @Transactional(readOnly = true)
    public Page<EventoFactura> buscar(TipoEventoFactura tipo, Instant desde, Instant hasta,
                                      Pageable pageable) {
        return eventoRepository.buscar(tipo, desde, hasta, pageable);
    }
}
