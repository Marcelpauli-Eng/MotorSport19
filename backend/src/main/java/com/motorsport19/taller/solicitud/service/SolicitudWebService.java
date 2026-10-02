package com.motorsport19.taller.solicitud.service;

import com.motorsport19.taller.agenda.domain.Cita;
import com.motorsport19.taller.agenda.service.CitaService;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.solicitud.domain.EstadoSolicitud;
import com.motorsport19.taller.solicitud.domain.FotoSolicitud;
import com.motorsport19.taller.solicitud.domain.SolicitudWeb;
import com.motorsport19.taller.solicitud.domain.TipoSolicitud;
import com.motorsport19.taller.solicitud.repository.FotoSolicitudRepository;
import com.motorsport19.taller.solicitud.repository.SolicitudWebRepository;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

/**
 * Bandeja de lo que piden los clientes desde la web.
 *
 * <p>La web entrega cada solicitud una vez; si se corta la respuesta y la
 * reenvia, llega con la misma referencia y se devuelve la que ya habia en vez de
 * duplicarla.
 */
@Service
public class SolicitudWebService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudWebService.class);

    private final SolicitudWebRepository solicitudes;
    private final FotoSolicitudRepository fotos;
    private final CitaService citaService;
    private final UsuarioRepository usuarios;

    public SolicitudWebService(SolicitudWebRepository solicitudes, FotoSolicitudRepository fotos,
                               CitaService citaService, UsuarioRepository usuarios) {
        this.solicitudes = solicitudes;
        this.fotos = fotos;
        this.citaService = citaService;
        this.usuarios = usuarios;
    }

    /** Lo que llega de la web, con las fotos en base64. */
    public record Entrada(String referencia, TipoSolicitud tipo, String idioma, String nombre,
                          String telefono, String email, String marca, String modelo,
                          String matricula, String necesita, LocalDate fechaPreferida,
                          List<String> fotos, Integer anio) {
    }

    // ------------------------------------------------------------------
    // Llegada
    // ------------------------------------------------------------------

    @Transactional
    public SolicitudWeb recibir(Entrada e) {
        var repetida = solicitudes.findByReferencia(e.referencia());
        if (repetida.isPresent()) {
            log.info("Solicitud web {} repetida: se devuelve la que ya estaba", e.referencia());
            return repetida.get();
        }

        List<byte[]> imagenes = decodificar(e.fotos());
        SolicitudWeb nueva = SolicitudWeb.recibir(
                e.referencia(), e.tipo(), e.idioma(), e.nombre(), e.telefono(), e.email(),
                e.marca(), e.modelo(), e.matricula(), e.necesita(), e.fechaPreferida(), imagenes.size());
        // Antes de guardarla: el año no se cambia despues.
        nueva.anotarAnio(e.anio());
        SolicitudWeb solicitud = solicitudes.save(nueva);

        for (int i = 0; i < imagenes.size(); i++) {
            fotos.save(FotoSolicitud.de(solicitud, i + 1, imagenes.get(i)));
        }
        log.info("Solicitud web {} recibida ({}, {} fotos)", solicitud.getId(), e.tipo(), imagenes.size());
        return solicitud;
    }

    private static List<byte[]> decodificar(List<String> base64) {
        if (base64 == null) {
            return List.of();
        }
        if (base64.size() > SolicitudWeb.MAXIMO_FOTOS) {
            throw new ReglaNegocioException(
                    "Una solicitud trae como mucho %d fotos.".formatted(SolicitudWeb.MAXIMO_FOTOS));
        }
        try {
            return base64.stream().map(f -> Base64.getDecoder().decode(f)).toList();
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("Una de las fotos no llego bien codificada.");
        }
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SolicitudWeb> bandeja(EstadoSolicitud estado) {
        return solicitudes.findTop200ByEstadoOrderByRecibidaEnDesc(estado);
    }

    @Transactional(readOnly = true)
    public long pendientes() {
        return solicitudes.countByEstado(EstadoSolicitud.PENDIENTE);
    }

    @Transactional(readOnly = true)
    public SolicitudWeb obtener(Long id) {
        return solicitudes.findConRelacionesById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud " + id));
    }

    @Transactional(readOnly = true)
    public FotoSolicitud foto(Long id, int orden) {
        return fotos.findBySolicitudIdAndOrden(id, (short) orden)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La solicitud %d no tiene foto %d".formatted(id, orden)));
    }

    // ------------------------------------------------------------------
    // Atenderla
    // ------------------------------------------------------------------

    /**
     * Le da cita en la agenda y cierra la solicitud, todo o nada.
     *
     * <p>Los datos de la cita los trae el formulario de la agenda, ya rellenado
     * con los de la solicitud: mostrador los revisa antes de guardar, y puede
     * elegir una moto que ya estuviera dada de alta.
     */
    @Transactional
    public SolicitudWeb darCita(Long id, Instant fechaHora, BigDecimal duracionEstimada, Long motoId,
                                Long clienteId, String contactoNombre, String contactoTelefono,
                                String descripcionMoto, String motivo, Long tecnicoId,
                                String observaciones, Long usuarioId) {
        SolicitudWeb solicitud = obtener(id);
        // Antes de crear la cita, y no despues: si ya estaba atendida no debe
        // quedar una cita suelta en la agenda.
        solicitud.exigirAbierta();
        Cita cita = citaService.agendar(fechaHora, duracionEstimada, motoId, clienteId,
                contactoNombre, contactoTelefono, descripcionMoto, motivo, tecnicoId,
                observaciones, usuarioId);
        solicitud.darCita(cita, usuario(usuarioId));
        return solicitud;
    }

    @Transactional
    public SolicitudWeb marcarAtendida(Long id, String nota, Long usuarioId) {
        SolicitudWeb solicitud = obtener(id);
        solicitud.marcarAtendida(nota, usuario(usuarioId));
        return solicitud;
    }

    @Transactional
    public SolicitudWeb descartar(Long id, String nota, Long usuarioId) {
        SolicitudWeb solicitud = obtener(id);
        solicitud.descartar(nota, usuario(usuarioId));
        return solicitud;
    }

    private Usuario usuario(Long id) {
        return id == null ? null : usuarios.findById(id).orElse(null);
    }
}
