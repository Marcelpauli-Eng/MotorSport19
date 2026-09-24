package com.motorsport19.taller.fichaje.web;

import com.motorsport19.taller.fichaje.domain.Fichaje;
import com.motorsport19.taller.fichaje.service.ActividadJornadaService;
import com.motorsport19.taller.fichaje.service.FichajeService;
import com.motorsport19.taller.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fichar la jornada y consultarla.
 *
 * <p>Dos publicos muy distintos comparten este controlador. El trabajador solo
 * usa tres cosas: empezar, terminar y ver lo suyo. La direccion usa el resto.
 * La linea que los separa es el permiso {@code FICHAJES_VER}: sin el, se ven
 * las horas propias y ninguna mas.
 */
@RestController
@RequestMapping("/fichajes")
public class FichajeController {

    private final FichajeService servicio;
    private final ActividadJornadaService actividad;
    private final UsuarioActual usuarioActual;

    public FichajeController(FichajeService servicio, ActividadJornadaService actividad,
                             UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.actividad = actividad;
        this.usuarioActual = usuarioActual;
    }

    // ==================================================================
    // El trabajador
    // ==================================================================

    /**
     * Como esta mi jornada ahora mismo.
     *
     * <p>Lo pregunta la pantalla nada mas entrar, para saber si enseña el boton
     * de empezar o el de terminar. Nunca se bloquea: es justo la ruta que tiene
     * que funcionar cuando todavia no se ha fichado.
     */
    @GetMapping("/jornada")
    public JornadaResponse miJornada() {
        return servicio.abiertaDe(usuarioActual.id())
                .map(JornadaResponse::abierta)
                .orElseGet(JornadaResponse::sinEmpezar);
    }

    @PostMapping("/jornada")
    public JornadaResponse empezar() {
        return JornadaResponse.abierta(servicio.empezar(usuarioActual.id()));
    }

    @PostMapping("/jornada/cierre")
    public FichajeResponse terminar() {
        return FichajeResponse.de(servicio.terminar(usuarioActual.id()));
    }

    /** Las jornadas de uno mismo. Verlas es un derecho, no hace falta permiso. */
    @GetMapping("/mias")
    public ResumenResponse mias(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return ResumenResponse.de(servicio.delPeriodo(desde, hasta, usuarioActual.id()));
    }

    // ==================================================================
    // La direccion
    // ==================================================================

    /** Las horas de todo el mundo en un periodo, con sus totales. */
    @GetMapping
    public ResumenResponse periodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long usuarioId) {

        return ResumenResponse.de(servicio.delPeriodo(desde, hasta, usuarioId));
    }

    /** Las que se quedaron abiertas, de cualquiera. Lo que hay que resolver. */
    @GetMapping("/abiertas")
    public List<FichajeResponse> abiertas() {
        return servicio.abiertas().stream().map(FichajeResponse::de).toList();
    }

    /**
     * Que hizo esa persona durante esa jornada.
     *
     * <p>Se consulta al desplegar una jornada, no al listarlas: son seis tablas
     * y no tiene sentido pagarlo por cada fila de un mes entero cuando lo normal
     * es mirar una.
     */
    @GetMapping("/{id}/actividad")
    public List<ActividadResponse> actividad(@PathVariable Long id) {
        return actividad.de(servicio.obtener(id)).stream().map(ActividadResponse::de).toList();
    }

    @PutMapping("/{id}/cierre-manual")
    public FichajeResponse cerrarPorOlvido(@PathVariable Long id,
                                           @Valid @RequestBody CierreManualRequest peticion) {
        return FichajeResponse.de(servicio.cerrarPorOlvido(
                id, peticion.fin(), peticion.motivo(), usuarioActual.id()));
    }

    @PutMapping("/{id}/correccion")
    public FichajeResponse corregir(@PathVariable Long id,
                                    @Valid @RequestBody CorreccionRequest peticion) {
        return FichajeResponse.de(servicio.corregir(
                id, peticion.inicio(), peticion.fin(), peticion.motivo(), usuarioActual.id()));
    }

    // ------------------------------------------------------------------
    // Peticiones
    // ------------------------------------------------------------------

    public record CierreManualRequest(
            @NotNull(message = "Hay que indicar a que hora terminó")
            Instant fin,
            @NotBlank(message = "Hay que decir por que se cierra a mano")
            @Size(max = 300) String motivo) {
    }

    /** @param inicio y {@code fin} nulos dejan esa hora como estaba */
    public record CorreccionRequest(
            Instant inicio,
            Instant fin,
            @NotBlank(message = "Toda correccion tiene que decir por que")
            @Size(max = 300) String motivo) {
    }

    // ------------------------------------------------------------------
    // Respuestas
    // ------------------------------------------------------------------

    /** El estado de mi jornada, para el boton de la pantalla. */
    public record JornadaResponse(
            boolean abierta,
            Long fichajeId,
            Instant inicio,
            long minutosTrabajados,
            long segundosTrabajados) {

        static JornadaResponse abierta(Fichaje f) {
            return new JornadaResponse(true, f.getId(), f.inicioReal(),
                    f.duracion().toMinutes(), f.duracion().toSeconds());
        }

        static JornadaResponse sinEmpezar() {
            return new JornadaResponse(false, null, null, 0, 0);
        }
    }

    public record FichajeResponse(
            Long id,
            Long usuarioId,
            String usuarioNombre,
            Instant inicio,
            Instant fin,
            long minutos,
            /** Exacto al segundo. Los minutos van redondeados y una jornada corta sale «0». */
            long segundos,
            boolean abierta,
            boolean corregida,
            boolean cerradaPorOlvido,
            String motivoCorreccion,
            Instant inicioFichado,
            Instant finFichado) {

        static FichajeResponse de(Fichaje f) {
            return new FichajeResponse(
                    f.getId(),
                    f.getUsuario().getId(),
                    f.getUsuario().getNombreCompleto(),
                    f.inicioReal(),
                    f.finReal(),
                    f.duracion().toMinutes(),
                    f.duracion().toSeconds(),
                    f.estaAbierta(),
                    f.fueCorregida(),
                    f.isCerradaPorOlvido(),
                    f.getMotivoCorreccion(),
                    // Lo que se fichó de verdad viaja siempre, aunque se haya
                    // corregido: quien revisa horas tiene que poder comparar.
                    f.getInicio(),
                    f.getFin());
        }
    }

    /** Una cosa que se hizo, con a donde lleva al pulsarla. */
    public record ActividadResponse(
            Instant momento,
            String tipo,
            String texto,
            String enlaceTipo,
            Long enlaceId) {

        static ActividadResponse de(ActividadJornadaService.Apunte a) {
            return new ActividadResponse(
                    a.momento(), a.tipo(), a.texto(), a.enlaceTipo(), a.enlaceId());
        }
    }

    /**
     * Las jornadas de un periodo con lo que suma cada persona.
     *
     * <p>El total se calcula aqui y no en la pantalla: si lo sumara el navegador
     * habria dos sitios donde equivocarse con lo mismo.
     */
    public record ResumenResponse(
            List<PorTrabajador> trabajadores,
            long minutosTotales,
            long segundosTotales,
            int jornadas,
            int abiertas) {

        static ResumenResponse de(List<Fichaje> fichajes) {
            Map<Long, List<Fichaje>> porUsuario = fichajes.stream()
                    .collect(Collectors.groupingBy(f -> f.getUsuario().getId()));

            List<PorTrabajador> filas = porUsuario.values().stream()
                    .map(PorTrabajador::de)
                    .sorted(Comparator.comparing(PorTrabajador::usuarioNombre))
                    .toList();

            long segundos = filas.stream().mapToLong(PorTrabajador::segundos).sum();
            return new ResumenResponse(
                    filas,
                    segundos / 60,
                    segundos,
                    fichajes.size(),
                    (int) fichajes.stream().filter(Fichaje::estaAbierta).count());
        }
    }

    public record PorTrabajador(
            Long usuarioId,
            String usuarioNombre,
            long minutos,
            long segundos,
            String horasLegibles,
            int jornadas,
            List<FichajeResponse> detalle) {

        static PorTrabajador de(List<Fichaje> suyas) {
            Fichaje primera = suyas.get(0);
            // Se suman SEGUNDOS y de ahi salen los minutos. Al reves —redondear
            // cada jornada a minutos y luego sumar— se pierde hasta un minuto
            // por jornada, y en un mes eso es media hora que nadie sabe explicar.
            long segundos = suyas.stream().mapToLong(f -> f.duracion().toSeconds()).sum();
            long minutos = segundos / 60;
            return new PorTrabajador(
                    primera.getUsuario().getId(),
                    primera.getUsuario().getNombreCompleto(),
                    minutos,
                    segundos,
                    enHorasYMinutos(minutos),
                    suyas.size(),
                    suyas.stream().map(FichajeResponse::de).toList());
        }
    }

    /** 545 minutos se leen mucho mejor como «9 h 05 min». */
    static String enHorasYMinutos(long minutos) {
        Duration d = Duration.ofMinutes(minutos);
        return "%d h %02d min".formatted(d.toHours(), d.toMinutesPart());
    }
}
