package com.motorsport19.taller.orden.web;

import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.orden.domain.EstadoOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.service.OrdenTrabajoService;
import com.motorsport19.taller.orden.service.ResultadoConsumo;
import com.motorsport19.taller.orden.web.dto.AbrirOrdenRequest;
import com.motorsport19.taller.orden.web.dto.AprobacionRequest;
import com.motorsport19.taller.orden.web.dto.CantidadRequest;
import com.motorsport19.taller.orden.web.dto.DevolucionLineaRequest;
import com.motorsport19.taller.orden.web.dto.DiagnosticoRequest;
import com.motorsport19.taller.orden.web.dto.LineaOTResponse;
import com.motorsport19.taller.orden.web.dto.ManoDeObraRequest;
import com.motorsport19.taller.orden.web.dto.MotivoRequest;
import com.motorsport19.taller.orden.web.dto.OrdenTrabajoResponse;
import com.motorsport19.taller.orden.web.dto.OrdenTrabajoResumenResponse;
import com.motorsport19.taller.orden.web.dto.PiezaLineaRequest;
import com.motorsport19.taller.orden.web.dto.ResultadoConsumoResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Ciclo de vida de las ordenes de trabajo.
 *
 * <p>Cada transicion tiene su propio endpoint con nombre de negocio
 * ({@code /aprobacion}, {@code /reparacion}, {@code /entrega}) en vez de un
 * generico "cambiar estado". Asi la API refleja la maquina de estados en vez de
 * dejar que el cliente proponga cualquier salto.
 */
@RestController
@RequestMapping("/ordenes")
public class OrdenTrabajoController {

    private final OrdenTrabajoService ordenService;

    public OrdenTrabajoController(OrdenTrabajoService ordenService) {
        this.ordenService = ordenService;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /** Tablero del taller. Por defecto solo las ordenes sin cerrar. */
    @GetMapping
    public PaginaResponse<OrdenTrabajoResumenResponse> buscar(
            @RequestParam(required = false) EstadoOT estado,
            @RequestParam(required = false) Long tecnicoId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long motoId,
            @RequestParam(defaultValue = "true") boolean soloAbiertas,
            @PageableDefault(size = 20, sort = "fechaEntrada", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<OrdenTrabajo> pagina = ordenService.buscar(estado, tecnicoId, clienteId, motoId,
                soloAbiertas, pageable);
        return PaginaResponse.de(pagina, OrdenTrabajoResumenResponse::de);
    }

    @GetMapping("/{id}")
    public OrdenTrabajoResponse obtener(@PathVariable Long id) {
        return detalle(ordenService.obtener(id));
    }

    @GetMapping("/codigo/{codigo}")
    public OrdenTrabajoResponse obtenerPorCodigo(@PathVariable String codigo) {
        return detalle(ordenService.obtenerPorCodigo(codigo));
    }

    /** Historial de intervenciones de una moto, de la mas reciente a la mas antigua. */
    @GetMapping("/moto/{motoId}/historial")
    public List<OrdenTrabajoResumenResponse> historialDeMoto(@PathVariable Long motoId) {
        return ordenService.historialDeMoto(motoId).stream()
                .map(OrdenTrabajoResumenResponse::de)
                .toList();
    }

    // ------------------------------------------------------------------
    // Apertura y contenido
    // ------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<OrdenTrabajoResponse> abrir(@Valid @RequestBody AbrirOrdenRequest peticion,
                                                      @RequestParam(required = false) Long usuarioId,
                                                      UriComponentsBuilder uriBuilder) {
        OrdenTrabajo orden = ordenService.abrir(
                peticion.motoId(), peticion.problemaReportado(), peticion.kmEntrada(),
                peticion.fechaEstimadaSalida(), peticion.tecnicoId(), peticion.observaciones(), usuarioId);

        return ResponseEntity
                .created(uriBuilder.path("/ordenes/{id}").build(orden.getId()))
                .body(detalle(orden));
    }

    @PutMapping("/{id}/diagnostico")
    public OrdenTrabajoResponse registrarDiagnostico(@PathVariable Long id,
                                                     @Valid @RequestBody DiagnosticoRequest peticion) {
        return detalle(ordenService.registrarDiagnostico(id, peticion.diagnostico()));
    }

    @GetMapping("/{id}/lineas")
    public List<LineaOTResponse> lineas(@PathVariable Long id) {
        return ordenService.lineasDe(id).stream().map(LineaOTResponse::de).toList();
    }

    @PostMapping("/{id}/lineas/mano-de-obra")
    @ResponseStatus(HttpStatus.CREATED)
    public LineaOTResponse anadirManoDeObra(@PathVariable Long id,
                                            @Valid @RequestBody ManoDeObraRequest peticion) {
        return LineaOTResponse.de(ordenService.anadirManoDeObra(
                id, peticion.descripcion(), peticion.horas(), peticion.descuentoPct(), peticion.tipoIva()));
    }

    @PostMapping("/{id}/lineas/piezas")
    @ResponseStatus(HttpStatus.CREATED)
    public LineaOTResponse anadirPieza(@PathVariable Long id,
                                       @Valid @RequestBody PiezaLineaRequest peticion) {
        return LineaOTResponse.de(ordenService.anadirPieza(
                id, peticion.piezaId(), peticion.cantidad(), peticion.descuentoPct()));
    }

    @PutMapping("/{id}/lineas/{lineaId}/cantidad")
    public LineaOTResponse cambiarCantidad(@PathVariable Long id, @PathVariable Long lineaId,
                                           @Valid @RequestBody CantidadRequest peticion) {
        return LineaOTResponse.de(ordenService.cambiarCantidadDeLinea(id, lineaId, peticion.cantidad()));
    }

    @DeleteMapping("/{id}/lineas/{lineaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarLinea(@PathVariable Long id, @PathVariable Long lineaId) {
        ordenService.quitarLinea(id, lineaId);
    }

    /** Devuelve al almacen piezas que la linea ya habia consumido. */
    @PostMapping("/{id}/lineas/{lineaId}/devoluciones")
    public LineaOTResponse devolverPieza(@PathVariable Long id, @PathVariable Long lineaId,
                                         @Valid @RequestBody DevolucionLineaRequest peticion,
                                         @RequestParam(required = false) Long usuarioId) {
        ordenService.devolverPiezaDeLinea(id, lineaId, peticion.cantidad(), peticion.motivo(), usuarioId);
        return ordenService.lineasDe(id).stream()
                .filter(l -> l.getId().equals(lineaId))
                .findFirst()
                .map(LineaOTResponse::de)
                .orElseThrow();
    }

    // ------------------------------------------------------------------
    // Transiciones de estado
    // ------------------------------------------------------------------

    @PostMapping("/{id}/diagnostico")
    public OrdenTrabajoResponse iniciarDiagnostico(@PathVariable Long id,
                                                   @RequestParam(required = false) Long tecnicoId,
                                                   @RequestParam(required = false) Long usuarioId) {
        return detalle(ordenService.iniciarDiagnostico(id, tecnicoId, usuarioId));
    }

    @PostMapping("/{id}/presupuesto")
    public OrdenTrabajoResponse presupuestar(@PathVariable Long id,
                                             @RequestParam(required = false) Long usuarioId) {
        return detalle(ordenService.presupuestar(id, usuarioId));
    }

    @PostMapping("/{id}/aprobacion")
    public OrdenTrabajoResponse aprobar(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) AprobacionRequest peticion,
                                        @RequestParam(required = false) Long usuarioId) {
        String aprobadoPor = peticion == null ? null : peticion.aprobadoPor();
        return detalle(ordenService.aprobar(id, aprobadoPor, usuarioId));
    }

    @PostMapping("/{id}/rechazo")
    public OrdenTrabajoResponse rechazar(@PathVariable Long id,
                                         @Valid @RequestBody MotivoRequest peticion,
                                         @RequestParam(required = false) Long usuarioId) {
        return detalle(ordenService.rechazar(id, peticion.motivo(), usuarioId));
    }

    /**
     * Entra en reparacion consumiendo el material del almacen.
     *
     * <p>Si falta alguna pieza, la respuesta es 200 igualmente: la orden queda en
     * {@code ESPERANDO_PIEZAS} y el cuerpo detalla que hay que pedir. No es un
     * error, es el resultado normal de que el almacen este corto.
     */
    @PostMapping("/{id}/reparacion")
    public ResultadoConsumoResponse iniciarReparacion(@PathVariable Long id,
                                                      @RequestParam(required = false) Long usuarioId) {
        ResultadoConsumo resultado = ordenService.iniciarReparacion(id, usuarioId);
        return ResultadoConsumoResponse.de(resultado);
    }

    /** Reintenta el material que faltaba cuando llega el pedido del proveedor. */
    @PostMapping("/{id}/reanudacion")
    public ResultadoConsumoResponse reanudarReparacion(@PathVariable Long id,
                                                       @RequestParam(required = false) Long usuarioId) {
        return ResultadoConsumoResponse.de(ordenService.reanudarReparacion(id, usuarioId));
    }

    @PostMapping("/{id}/espera-piezas")
    public OrdenTrabajoResponse bloquearPorFaltaDePiezas(@PathVariable Long id,
                                                         @Valid @RequestBody MotivoRequest peticion,
                                                         @RequestParam(required = false) Long usuarioId) {
        return detalle(ordenService.bloquearPorFaltaDePiezas(id, peticion.motivo(), usuarioId));
    }

    @PostMapping("/{id}/lista")
    public OrdenTrabajoResponse marcarLista(@PathVariable Long id,
                                            @RequestParam(required = false) Long usuarioId) {
        return detalle(ordenService.marcarLista(id, usuarioId));
    }

    /** Entrega al cliente. A partir de aqui la orden queda congelada. */
    @PostMapping("/{id}/entrega")
    public OrdenTrabajoResponse entregar(@PathVariable Long id,
                                         @RequestParam(required = false) Long usuarioId) {
        return detalle(ordenService.entregar(id, usuarioId));
    }

    // ------------------------------------------------------------------

    /**
     * Compone la ficha completa.
     *
     * <p>Lineas e historial se piden aparte en vez de traerse con la cabecera:
     * cargar dos colecciones en la misma consulta multiplicaria las filas.
     */
    private OrdenTrabajoResponse detalle(OrdenTrabajo orden) {
        return OrdenTrabajoResponse.de(orden,
                ordenService.lineasDe(orden.getId()),
                ordenService.historialDe(orden.getId()));
    }
}
