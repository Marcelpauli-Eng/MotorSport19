package com.motorsport19.taller.agenda.web;

import com.motorsport19.taller.agenda.service.AgendaSemanal;
import com.motorsport19.taller.agenda.service.CargaDiaria;
import com.motorsport19.taller.agenda.service.CitaService;
import com.motorsport19.taller.agenda.service.SeguimientoAusencias;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.agenda.web.dto.AtenderCitaRequest;
import com.motorsport19.taller.agenda.web.dto.CitaResponse;
import com.motorsport19.taller.agenda.web.dto.GuardarCitaRequest;
import com.motorsport19.taller.agenda.web.dto.ReprogramarRequest;
import com.motorsport19.taller.orden.web.dto.MotivoRequest;
import com.motorsport19.taller.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Agenda del taller: que motos entran y cuando.
 *
 * <p>La consulta va por rango de dias y sin paginar, porque asi es como se mira
 * un calendario: «ensename esta semana». Una semana de taller son unas decenas
 * de citas.
 */
@RestController
@RequestMapping("/citas")
public class CitaController {

    /** Tope del rango de las consultas que agrupan en memoria. */
    private static final int MAXIMO_DIAS = 366;

    private final CitaService citaService;
    private final UsuarioActual usuarioActual;

    public CitaController(CitaService citaService, UsuarioActual usuarioActual) {
        this.citaService = citaService;
        this.usuarioActual = usuarioActual;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /** Citas del rango, ambos dias incluidos. */
    @GetMapping
    public List<CitaResponse> agenda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return citaService.agenda(desde, hasta).stream().map(CitaResponse::de).toList();
    }

    /**
     * Trabajo comprometido dia a dia, para ver de un vistazo donde queda hueco.
     *
     * <p>Incluye los dias vacios: un dia libre es justo la informacion que se
     * busca cuando hay que decidir cuando meter una moto.
     */
    @GetMapping("/carga")
    public List<CargaDiariaResponse> carga(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return citaService.carga(desde, hasta).stream().map(CargaDiariaResponse::de).toList();
    }

    /**
     * La semana repartida por tecnico, con el hueco que le queda a cada uno.
     *
     * <p>Es la vista con la que se coge el telefono: dice a la vez cuando cabe
     * la moto y quien la puede coger.
     */
    @GetMapping("/semana")
    public AgendaSemanal semana(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        exigirRango(desde, hasta);
        return citaService.semana(desde, hasta);
    }

    /** Plantones del periodo: cuantos, cuantas horas se perdieron y quien repite. */
    @GetMapping("/ausencias")
    public SeguimientoAusencias ausencias(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        exigirRango(desde, hasta);
        return citaService.ausencias(desde, hasta);
    }

    /**
     * Corta los rangos absurdos o gigantescos.
     *
     * <p>Estas dos consultas se traen las citas del periodo a memoria para
     * agruparlas; un rango de diez años las traeria todas.
     */
    private void exigirRango(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new ReglaNegocioException("La fecha final del periodo es anterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > MAXIMO_DIAS) {
            throw new ReglaNegocioException(
                    "El periodo no puede pasar de %d dias.".formatted(MAXIMO_DIAS));
        }
    }

    @GetMapping("/{id}")
    public CitaResponse obtener(@PathVariable Long id) {
        return CitaResponse.de(citaService.obtener(id));
    }

    /** Historial de citas de una moto, de la mas reciente a la mas antigua. */
    @GetMapping("/moto/{motoId}")
    public List<CitaResponse> historialDeMoto(@PathVariable Long motoId) {
        return citaService.historialDeMoto(motoId).stream().map(CitaResponse::de).toList();
    }

    // ------------------------------------------------------------------
    // Alta y modificacion
    // ------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<CitaResponse> agendar(@Valid @RequestBody GuardarCitaRequest peticion,
                                                UriComponentsBuilder uriBuilder) {
        var cita = citaService.agendar(
                peticion.fechaHora(), peticion.duracionEstimada(), peticion.motoId(),
                peticion.clienteId(), peticion.contactoNombre(), peticion.contactoTelefono(),
                peticion.descripcionMoto(), peticion.motivo(), peticion.tecnicoId(),
                peticion.observaciones());

        return ResponseEntity
                .created(uriBuilder.path("/citas/{id}").build(cita.getId()))
                .body(CitaResponse.de(cita));
    }

    @PutMapping("/{id}")
    public CitaResponse actualizar(@PathVariable Long id,
                                   @Valid @RequestBody GuardarCitaRequest peticion) {
        return CitaResponse.de(citaService.actualizar(
                id, peticion.fechaHora(), peticion.duracionEstimada(), peticion.motoId(),
                peticion.clienteId(), peticion.contactoNombre(), peticion.contactoTelefono(),
                peticion.descripcionMoto(), peticion.motivo(), peticion.tecnicoId(),
                peticion.observaciones()));
    }

    /** Mueve la cita de fecha sin tocar el resto de la ficha. */
    @PutMapping("/{id}/fecha")
    public CitaResponse reprogramar(@PathVariable Long id,
                                    @Valid @RequestBody ReprogramarRequest peticion) {
        return CitaResponse.de(citaService.reprogramar(id, peticion.fechaHora()));
    }

    // ------------------------------------------------------------------
    // Cambios de estado
    // ------------------------------------------------------------------

    @PostMapping("/{id}/confirmacion")
    public CitaResponse confirmar(@PathVariable Long id) {
        return CitaResponse.de(citaService.confirmar(id));
    }

    /** La moto ha llegado: abre su orden de trabajo y cierra la cita. */
    @PostMapping("/{id}/entrada")
    public CitaResponse atender(@PathVariable Long id,
                                @Valid @RequestBody AtenderCitaRequest peticion) {
        return CitaResponse.de(citaService.atender(
                id, peticion.motoId(), peticion.kmEntrada(), peticion.problemaReportado(),
                usuarioActual.id()));
    }

    @PostMapping("/{id}/cancelacion")
    public CitaResponse cancelar(@PathVariable Long id,
                                 @RequestBody(required = false) MotivoRequest peticion) {
        return CitaResponse.de(citaService.cancelar(id, peticion == null ? null : peticion.motivo()));
    }

    /** El cliente no aparecio. Se distingue de cancelar: el hueco se perdio. */
    @PostMapping("/{id}/ausencia")
    public CitaResponse marcarNoPresentado(@PathVariable Long id,
                                           @RequestBody(required = false) MotivoRequest peticion) {
        return CitaResponse.de(
                citaService.marcarNoPresentado(id, peticion == null ? null : peticion.motivo()));
    }

    // ------------------------------------------------------------------

    /** Carga de un dia, con el porcentaje ya calculado para la barra. */
    public record CargaDiariaResponse(
            LocalDate dia,
            int citas,
            BigDecimal horasComprometidas,
            BigDecimal capacidad,
            int porcentaje,
            boolean saturado
    ) {
        static CargaDiariaResponse de(CargaDiaria carga) {
            return new CargaDiariaResponse(carga.dia(), carga.citas(), carga.horasComprometidas(),
                    carga.capacidad(), carga.porcentaje(), carga.saturado());
        }
    }
}
