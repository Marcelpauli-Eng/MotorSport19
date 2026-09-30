package com.motorsport19.taller.fichaje.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.fichaje.domain.CambioFichaje;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONA);
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZONA);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONA);

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

    /**
     * Que horas tenia antes de cada cambio a mano, y quien lo hizo.
     *
     * @param soloDe si no es nulo, solo si la jornada es de esa persona
     */
    @Transactional(readOnly = true)
    public List<CambioFichaje> cambiosDe(Long fichajeId, Long soloDe) {
        return repositorio.cambiosDe(fichajeId, soloDe);
    }

    /**
     * El registro de jornada de un periodo en CSV, con el historial de cambios.
     *
     * <p>Una fila por jornada con las horas que cuentan, y en la ultima columna
     * cada cambio a mano con lo que habia antes: es lo que hay que poder
     * entregar a una inspeccion o al propio trabajador. Punto y coma y BOM
     * UTF-8, igual que el libro de facturas, para que Excel lo abra con tildes.
     */
    @Transactional(readOnly = true)
    public byte[] exportarCsv(LocalDate desde, LocalDate hasta, Long usuarioId) {
        List<Fichaje> jornadas = delPeriodo(desde, hasta, usuarioId);
        Map<Long, List<CambioFichaje>> cambios = jornadas.isEmpty() ? Map.of()
                : repositorio.cambiosDe(jornadas).stream()
                        .collect(Collectors.groupingBy(c -> c.getFichaje().getId()));

        StringBuilder csv = new StringBuilder("\uFEFFtrabajador;entrada;salida;duracion;horas;estado;cambios\n");
        for (Fichaje f : jornadas) {
            Duration d = f.duracion();
            csv.append(campo(f.getUsuario().getNombreCompleto())).append(';')
                    .append(FECHA_HORA.format(f.inicioReal())).append(';')
                    .append(f.finReal() == null ? "" : FECHA_HORA.format(f.finReal())).append(';')
                    .append("%d:%02d:%02d".formatted(d.toHours(), d.toMinutesPart(), d.toSecondsPart())).append(';')
                    // Horas con decimales y coma, que es lo que suma una hoja en espanol.
                    .append(String.format(Locale.ROOT, "%.2f", d.toSeconds() / 3600.0).replace('.', ',')).append(';')
                    .append(estado(f)).append(';')
                    .append(campo(cambios.getOrDefault(f.getId(), List.of()).stream()
                            .map(FichajeService::describir)
                            .collect(Collectors.joining(" | "))))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
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

    private static String estado(Fichaje f) {
        List<String> marcas = new ArrayList<>();
        if (f.estaAbierta()) marcas.add("Sin cerrar");
        if (f.isCerradaPorOlvido()) marcas.add("Cerrada a mano");
        if (f.fueCorregida()) marcas.add("Cambiada");
        return String.join(" + ", marcas);
    }

    /** «30/09/2026 23:12 Direccion: 29/09 08:05 – sin salida → 29/09 08:05 – 18:00 (motivo)». */
    static String describir(CambioFichaje c) {
        return "%s %s: %s → %s (%s)".formatted(
                FECHA_HORA.format(c.getFecha()),
                c.getUsuario() == null ? "?" : c.getUsuario().getNombreCompleto(),
                tramo(c.getInicioAnterior(), c.getFinAnterior()),
                tramo(c.getInicioNuevo(), c.getFinNuevo()),
                c.getMotivo());
    }

    /** La salida lleva fecha solo si cae en otro dia que la entrada. */
    private static String tramo(Instant inicio, Instant fin) {
        if (fin == null) return DIA_HORA.format(inicio) + " – sin salida";
        boolean mismoDia = inicio.atZone(ZONA).toLocalDate().equals(fin.atZone(ZONA).toLocalDate());
        return DIA_HORA.format(inicio) + " – " + (mismoDia ? HORA : DIA_HORA).format(fin);
    }

    private static String campo(String valor) {
        if (valor == null) return "";
        if (valor.indexOf(';') >= 0 || valor.indexOf('"') >= 0 || valor.indexOf('\n') >= 0) {
            return '"' + valor.replace("\"", "\"\"") + '"';
        }
        return valor;
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
