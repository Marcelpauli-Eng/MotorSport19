package com.motorsport19.taller.orden.web;

import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.orden.domain.EstadoOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.service.OrdenTrabajoService;
import com.motorsport19.taller.orden.service.ResultadoConsumo;
import com.motorsport19.taller.orden.web.dto.AbrirOrdenRequest;
import com.motorsport19.taller.orden.web.dto.AprobacionRequest;
import com.motorsport19.taller.orden.web.dto.AsignarTecnicoRequest;
import com.motorsport19.taller.orden.web.dto.CantidadRequest;
import com.motorsport19.taller.orden.web.dto.DatosOrdenRequest;
import com.motorsport19.taller.orden.web.dto.DescuentoRequest;
import com.motorsport19.taller.orden.web.dto.DevolucionLineaRequest;
import com.motorsport19.taller.orden.web.dto.DiagnosticoRequest;
import com.motorsport19.taller.orden.web.dto.LineaOTResponse;
import com.motorsport19.taller.orden.web.dto.ManoDeObraRequest;
import com.motorsport19.taller.orden.web.dto.MotivoRequest;
import com.motorsport19.taller.orden.web.dto.OrdenTrabajoResponse;
import com.motorsport19.taller.orden.web.dto.OrdenTrabajoResumenResponse;
import com.motorsport19.taller.orden.web.dto.PiezaLineaRequest;
import com.motorsport19.taller.orden.web.dto.PrecioLineaRequest;
import com.motorsport19.taller.orden.web.dto.ResultadoConsumoResponse;
import com.motorsport19.taller.orden.web.dto.TarifaHoraRequest;
import com.motorsport19.taller.configuracion.service.ConfiguracionTallerService;
import com.motorsport19.taller.documento.ArmadorDocumento;
import com.motorsport19.taller.documento.GeneradorPdfDocumento;
import com.motorsport19.taller.seguridad.UsuarioActual;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final UsuarioActual usuarioActual;
    private final GeneradorPdfDocumento generadorDocumento;
    private final ArmadorDocumento armador;
    private final ConfiguracionTallerService configuracion;

    public OrdenTrabajoController(OrdenTrabajoService ordenService, UsuarioActual usuarioActual,
                                  GeneradorPdfDocumento generadorDocumento, ArmadorDocumento armador,
                                  ConfiguracionTallerService configuracion) {
        this.ordenService = ordenService;
        this.usuarioActual = usuarioActual;
        this.generadorDocumento = generadorDocumento;
        this.armador = armador;
        this.configuracion = configuracion;
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

    /**
     * El presupuesto en PDF, con el formato de siempre del taller.
     *
     * <p>Se regenera en cada peticion a partir de la orden. Mientras la orden
     * siga viva el presupuesto puede cambiar, y eso es correcto: lo que queda
     * congelado para siempre es la factura, no el presupuesto.
     */
    @GetMapping(value = "/{id}/presupuesto/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> presupuestoPdf(@PathVariable Long id) {
        OrdenTrabajo orden = ordenService.obtener(id);
        byte[] pdf = generadorDocumento.generar(
                armador.presupuesto(orden, ordenService.lineasDe(id), configuracion.obligatoria()));

        String nombre = "presupuesto-%s.pdf".formatted(orden.codigoVisible());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"%s\"".formatted(nombre))
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(pdf));
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
                                                      UriComponentsBuilder uriBuilder) {
        OrdenTrabajo orden = ordenService.abrir(
                peticion.motoId(), peticion.problemaReportado(), peticion.kmEntrada(),
                peticion.fechaEstimadaSalida(), peticion.tecnicoId(), peticion.observaciones(), usuarioActual.id());

        return ResponseEntity
                .created(uriBuilder.path("/ordenes/{id}").build(orden.getId()))
                .body(detalle(orden));
    }

    /** Pone o cambia el tecnico que lleva la orden, sin tocar su estado. */
    @PutMapping("/{id}/tecnico")
    public OrdenTrabajoResponse asignarTecnico(@PathVariable Long id,
                                               @RequestBody AsignarTecnicoRequest peticion) {
        return detalle(ordenService.asignarTecnico(id, peticion.tecnicoId()));
    }

    /**
     * Cambia el precio de la hora de esta orden, sin tocar el del taller.
     *
     * <p>Las horas ya apuntadas se revaloran a la tarifa nueva, asi que la
     * respuesta trae los totales ya recalculados.
     */
    @PutMapping("/{id}/tarifa-hora")
    public OrdenTrabajoResponse cambiarTarifaHora(@PathVariable Long id,
                                                  @Valid @RequestBody TarifaHoraRequest peticion) {
        return detalle(ordenService.cambiarTarifaHora(id, peticion.tarifaHora()));
    }

    /**
     * Mismo descuento en todas las lineas.
     *
     * <p>Es el «Dto. General» del pie del presupuesto. Se escribe en cada linea en
     * vez de guardarse aparte en la cabecera, de modo que la base imponible, el
     * PDF y la factura siguen saliendo de una sola fuente.
     */
    @PutMapping("/{id}/descuento-general")
    public OrdenTrabajoResponse aplicarDescuentoGeneral(@PathVariable Long id,
                                                        @Valid @RequestBody DescuentoRequest peticion) {
        return detalle(ordenService.aplicarDescuentoGeneral(id, peticion.descuentoPct()));
    }

    /** Fecha estimada de salida y notas internas. */
    @PutMapping("/{id}/datos")
    public OrdenTrabajoResponse actualizarDatos(@PathVariable Long id,
                                                @RequestBody DatosOrdenRequest peticion) {
        return detalle(ordenService.actualizarDatos(
                id, peticion.fechaEstimadaSalida(), peticion.observaciones()));
    }

    @PutMapping("/{id}/diagnostico")
    public OrdenTrabajoResponse registrarDiagnostico(@PathVariable Long id,
                                                     @Valid @RequestBody DiagnosticoRequest peticion) {
        return detalle(ordenService.registrarDiagnostico(id, peticion.diagnostico()));
    }

    @GetMapping("/{id}/lineas")
    public List<LineaOTResponse> lineas(@PathVariable Long id) {
        return ordenService.lineasDe(id).stream().map(this::linea).toList();
    }

    @PostMapping("/{id}/lineas/mano-de-obra")
    @ResponseStatus(HttpStatus.CREATED)
    public LineaOTResponse anadirManoDeObra(@PathVariable Long id,
                                            @Valid @RequestBody ManoDeObraRequest peticion) {
        return linea(ordenService.anadirManoDeObra(
                id, peticion.descripcion(), peticion.horas(), peticion.descuentoPct(), peticion.tipoIva()));
    }

    @PostMapping("/{id}/lineas/piezas")
    @ResponseStatus(HttpStatus.CREATED)
    public LineaOTResponse anadirPieza(@PathVariable Long id,
                                       @Valid @RequestBody PiezaLineaRequest peticion) {
        return linea(ordenService.anadirPieza(
                id, peticion.piezaId(), peticion.cantidad(), peticion.descuentoPct()));
    }

    /**
     * Vuelca un servicio tipo entero: la revision de 10.000 km con sus horas y
     * su kit de piezas, en una sola llamada en vez de siete.
     *
     * <p>Devuelve solo las lineas anadidas, no la orden entera: la pantalla las
     * anade a la tabla que ya tiene pintada y de paso puede senalar cuales
     * acaban de entrar.
     */
    @PostMapping("/{id}/servicios-tipo/{servicioTipoId}")
    @ResponseStatus(HttpStatus.CREATED)
    public List<LineaOTResponse> aplicarServicioTipo(@PathVariable Long id,
                                                     @PathVariable Long servicioTipoId) {
        return ordenService.aplicarServicioTipo(id, servicioTipoId).stream().map(this::linea).toList();
    }

    @PutMapping("/{id}/lineas/{lineaId}/cantidad")
    public LineaOTResponse cambiarCantidad(@PathVariable Long id, @PathVariable Long lineaId,
                                           @Valid @RequestBody CantidadRequest peticion) {
        return linea(ordenService.cambiarCantidadDeLinea(id, lineaId, peticion.cantidad()));
    }

    /** Precio cerrado para una linea de mano de obra, al margen de la tarifa/hora. */
    @PutMapping("/{id}/lineas/{lineaId}/precio")
    public LineaOTResponse cambiarPrecio(@PathVariable Long id, @PathVariable Long lineaId,
                                         @Valid @RequestBody PrecioLineaRequest peticion) {
        return linea(ordenService.cambiarPrecioDeManoDeObra(
                id, lineaId, peticion.precioUnitario()));
    }

    /** Descuento de una linea suelta, para el regateo concepto a concepto. */
    @PutMapping("/{id}/lineas/{lineaId}/descuento")
    public LineaOTResponse cambiarDescuento(@PathVariable Long id, @PathVariable Long lineaId,
                                            @Valid @RequestBody DescuentoRequest peticion) {
        return linea(ordenService.cambiarDescuentoDeLinea(id, lineaId, peticion.descuentoPct()));
    }

    @DeleteMapping("/{id}/lineas/{lineaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarLinea(@PathVariable Long id, @PathVariable Long lineaId) {
        ordenService.quitarLinea(id, lineaId);
    }

    /** Devuelve al almacen piezas que la linea ya habia consumido. */
    @PostMapping("/{id}/lineas/{lineaId}/devoluciones")
    public LineaOTResponse devolverPieza(@PathVariable Long id, @PathVariable Long lineaId,
                                         @Valid @RequestBody DevolucionLineaRequest peticion) {
        ordenService.devolverPiezaDeLinea(id, lineaId, peticion.cantidad(), peticion.motivo(), usuarioActual.id());
        return ordenService.lineasDe(id).stream()
                .filter(l -> l.getId().equals(lineaId))
                .findFirst()
                .map(this::linea)
                .orElseThrow();
    }

    // ------------------------------------------------------------------
    // Transiciones de estado
    // ------------------------------------------------------------------

    @PostMapping("/{id}/diagnostico")
    public OrdenTrabajoResponse iniciarDiagnostico(@PathVariable Long id,
                                                   @RequestParam(required = false) Long tecnicoId) {
        return detalle(ordenService.iniciarDiagnostico(id, tecnicoId, usuarioActual.id()));
    }

    /**
     * Deja la orden preparada para el taller y, si se indica, se la asigna a un
     * tecnico.
     *
     * <p>Es el atajo para el trabajo ya cerrado con el cliente: se salta el
     * diagnostico y la aprobacion del presupuesto porque el precio ya se pacto
     * fuera. A partir de aqui direccion compone las lineas y el tecnico solo
     * tiene que empezar.
     */
    @PostMapping("/{id}/preparacion")
    public OrdenTrabajoResponse preparar(@PathVariable Long id,
                                         @RequestBody(required = false) AsignarTecnicoRequest peticion) {
        Long tecnicoId = peticion == null ? null : peticion.tecnicoId();
        return detalle(ordenService.preparar(id, tecnicoId, usuarioActual.id()));
    }

    @PostMapping("/{id}/presupuesto")
    public OrdenTrabajoResponse presupuestar(@PathVariable Long id) {
        return detalle(ordenService.presupuestar(id, usuarioActual.id()));
    }

    @PostMapping("/{id}/aprobacion")
    public OrdenTrabajoResponse aprobar(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) AprobacionRequest peticion) {
        String aprobadoPor = peticion == null ? null : peticion.aprobadoPor();
        return detalle(ordenService.aprobar(id, aprobadoPor, usuarioActual.id()));
    }

    @PostMapping("/{id}/rechazo")
    public OrdenTrabajoResponse rechazar(@PathVariable Long id,
                                         @Valid @RequestBody MotivoRequest peticion) {
        return detalle(ordenService.rechazar(id, peticion.motivo(), usuarioActual.id()));
    }

    /**
     * Entra en reparacion consumiendo el material del almacen.
     *
     * <p>Si falta alguna pieza, la respuesta es 200 igualmente: la orden queda en
     * {@code ESPERANDO_PIEZAS} y el cuerpo detalla que hay que pedir. No es un
     * error, es el resultado normal de que el almacen este corto.
     */
    @PostMapping("/{id}/reparacion")
    public ResultadoConsumoResponse iniciarReparacion(@PathVariable Long id) {
        ResultadoConsumo resultado = ordenService.iniciarReparacion(id, usuarioActual.id());
        return ResultadoConsumoResponse.de(resultado);
    }

    /** Reintenta el material que faltaba cuando llega el pedido del proveedor. */
    @PostMapping("/{id}/reanudacion")
    public ResultadoConsumoResponse reanudarReparacion(@PathVariable Long id) {
        return ResultadoConsumoResponse.de(ordenService.reanudarReparacion(id, usuarioActual.id()));
    }

    @PostMapping("/{id}/espera-piezas")
    public OrdenTrabajoResponse bloquearPorFaltaDePiezas(@PathVariable Long id,
                                                         @Valid @RequestBody MotivoRequest peticion) {
        return detalle(ordenService.bloquearPorFaltaDePiezas(id, peticion.motivo(), usuarioActual.id()));
    }

    @PostMapping("/{id}/lista")
    public OrdenTrabajoResponse marcarLista(@PathVariable Long id) {
        return detalle(ordenService.marcarLista(id, usuarioActual.id()));
    }

    /** Entrega al cliente. A partir de aqui la orden queda congelada. */
    @PostMapping("/{id}/entrega")
    public OrdenTrabajoResponse entregar(@PathVariable Long id) {
        return detalle(ordenService.entregar(id, usuarioActual.id()));
    }

    // ------------------------------------------------------------------

    /**
     * Compone la ficha completa.
     *
     * <p>Lineas e historial se piden aparte en vez de traerse con la cabecera:
     * cargar dos colecciones en la misma consulta multiplicaria las filas.
     *
     * <p>A un tecnico se le sirve sin importes. Eso se decide aqui y no en la
     * pantalla: la pantalla puede no pintar una columna, pero el JSON sigue
     * llevando el dato y basta con abrir las herramientas del navegador para
     * verlo. Si el taller decide que el taller no ve precios, los precios no
     * salen del servidor.
     */
    private OrdenTrabajoResponse detalle(OrdenTrabajo orden) {
        OrdenTrabajoResponse ficha = OrdenTrabajoResponse.de(orden,
                ordenService.lineasDe(orden.getId()),
                ordenService.historialDe(orden.getId()));
        return usuarioActual.esTecnico() ? ficha.sinImportes() : ficha;
    }

    /** Una linea suelta, con el mismo criterio de importes que {@link #detalle}. */
    private LineaOTResponse linea(com.motorsport19.taller.orden.domain.LineaOT linea) {
        LineaOTResponse respuesta = LineaOTResponse.de(linea);
        return usuarioActual.esTecnico() ? respuesta.sinImportes() : respuesta;
    }
}
