package com.motorsport19.taller.fichaje.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.fichaje.domain.Fichaje;
import com.motorsport19.taller.fichaje.repository.FichajeRepository;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Empezar y terminar la jornada, y consultarla despues.
 *
 * <p>El registro de jornada es obligatorio (art. 34.9 ET) y se conserva cuatro
 * anos, asi que aqui no hay ningun borrado: una jornada mal fichada se corrige
 * dejando rastro, nunca se elimina.
 */
@Service
public class FichajeService {

    private static final Logger log = LoggerFactory.getLogger(FichajeService.class);

    /** El taller trabaja en hora local: los dias del listado son los de aqui. */
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private final FichajeRepository repositorio;
    private final UsuarioRepository usuarios;

    public FichajeService(FichajeRepository repositorio, UsuarioRepository usuarios) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
    }

    // ==================================================================
    // Lo que hace el trabajador
    // ==================================================================

    /**
     * Empieza la jornada.
     *
     * <p>La carrera del doble clic la cierra el indice unico de la base, no una
     * comprobacion previa: entre mirar si hay una abierta y crearla cabe otra
     * peticion, y saldrian dos jornadas simultaneas de la misma persona.
     */
    @Transactional
    public Fichaje empezar(Long usuarioId) {
        Usuario usuario = cargarUsuario(usuarioId);
        try {
            Fichaje jornada = repositorio.saveAndFlush(Fichaje.empezar(usuario));
            log.info("{} ha empezado su jornada", usuario.getUsername());
            return jornada;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException(
                    "Ya tienes una jornada empezada. Si la pantalla no se ha enterado, recargala.");
        }
    }

    /** Termina la jornada abierta. */
    @Transactional
    public Fichaje terminar(Long usuarioId) {
        Fichaje jornada = repositorio.buscarAbiertaDe(usuarioId)
                .orElseThrow(() -> new ConflictoException(
                        "No tienes ninguna jornada empezada, asi que no hay nada que cerrar."));
        jornada.terminar();
        log.info("{} ha terminado su jornada ({} min)",
                jornada.getUsuario().getUsername(), jornada.duracion().toMinutes());
        return jornada;
    }

    /** La jornada abierta de alguien, si la tiene. */
    @Transactional(readOnly = true)
    public Optional<Fichaje> abiertaDe(Long usuarioId) {
        return repositorio.buscarAbiertaDe(usuarioId);
    }

    /** Sus ultimas jornadas: todo el mundo puede ver las suyas. */
    @Transactional(readOnly = true)
    public Page<Fichaje> misJornadas(Long usuarioId, LocalDate desde, LocalDate hasta, Pageable pagina) {
        return repositorio.buscar(alInstante(desde), alInstanteFin(hasta), usuarioId, pagina);
    }

    // ==================================================================
    // Lo que ve y hace la direccion
    // ==================================================================

    @Transactional(readOnly = true)
    public List<Fichaje> delPeriodo(LocalDate desde, LocalDate hasta, Long usuarioId) {
        return repositorio.buscarTodas(alInstante(desde), alInstanteFin(hasta), usuarioId);
    }

    /** Las que se quedaron abiertas. Lo primero que mira quien revisa las horas. */
    @Transactional(readOnly = true)
    public List<Fichaje> abiertas() {
        return repositorio.abiertas();
    }

    /**
     * Cierra a mano una jornada que alguien se dejo abierta.
     *
     * <p>Sin esto, quien se fue sin fichar salida se queda con una jornada de
     * treinta horas y, peor, bloqueado: no puede empezar la del dia siguiente
     * porque ya tiene una abierta.
     */
    @Transactional
    public Fichaje cerrarPorOlvido(Long fichajeId, Instant finReal, String motivo, Long administradorId) {
        Fichaje jornada = cargar(fichajeId);
        jornada.cerrarPorOlvido(finReal, motivo, cargarUsuario(administradorId));
        log.info("Jornada {} de {} cerrada a mano por olvido de salida",
                fichajeId, jornada.getUsuario().getUsername());
        return jornada;
    }

    /** Corrige las horas de una jornada cerrada, sin borrar las que se ficharon. */
    @Transactional
    public Fichaje corregir(Long fichajeId, Instant inicio, Instant fin, String motivo, Long administradorId) {
        Fichaje jornada = cargar(fichajeId);
        jornada.corregir(inicio, fin, motivo, cargarUsuario(administradorId));
        log.info("Jornada {} de {} corregida", fichajeId, jornada.getUsuario().getUsername());
        return jornada;
    }

    /** Una jornada concreta. La usa la pantalla de horas al desplegar una fila. */
    @Transactional(readOnly = true)
    public Fichaje obtener(Long id) {
        return cargar(id);
    }

    // ==================================================================

    private Fichaje cargar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la jornada", id));
    }

    private Usuario cargarUsuario(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el usuario", id));
    }

    /** Las 00:00 de ese dia en hora del taller. */
    private static Instant alInstante(LocalDate dia) {
        return dia.atStartOfDay(ZONA).toInstant();
    }

    /** Las 00:00 del dia siguiente: el filtro es «hasta» incluido. */
    private static Instant alInstanteFin(LocalDate dia) {
        return dia.plusDays(1).atStartOfDay(ZONA).toInstant();
    }
}
