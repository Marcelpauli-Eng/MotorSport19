package com.motorsport19.taller.factura.web;

import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.LineaAFacturar;
import com.motorsport19.taller.factura.domain.TipoEventoFactura;
import com.motorsport19.taller.factura.domain.TipoFactura;
import com.motorsport19.taller.factura.service.ExportacionFacturacionService;
import com.motorsport19.taller.factura.service.FacturacionService;
import com.motorsport19.taller.documento.ArmadorDocumento;
import com.motorsport19.taller.documento.GeneradorPdfDocumento;
import com.motorsport19.taller.configuracion.service.ConfiguracionTallerService;
import com.motorsport19.taller.factura.service.InformeVerificacion;
import com.motorsport19.taller.factura.service.RegistroEventosService;
import com.motorsport19.taller.factura.web.dto.EmitirFacturaRequest;
import com.motorsport19.taller.factura.web.dto.FacturaResponse;
import com.motorsport19.taller.factura.web.dto.FacturaResumenResponse;
import com.motorsport19.taller.factura.web.dto.LineaRectificativaRequest;
import com.motorsport19.taller.factura.web.dto.RectificarFacturaRequest;
import com.motorsport19.taller.factura.web.dto.SerieFacturaResponse;
import com.motorsport19.taller.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

/**
 * Facturacion.
 *
 * <p>No hay PUT ni DELETE en toda esta API, y no es un olvido: una factura
 * emitida no se modifica ni se borra. Lo unico que se puede hacer con una
 * factura equivocada es emitir una rectificativa que la corrija.
 */
@RestController
@RequestMapping("/facturas")
public class FacturaController {

    private final FacturacionService facturacionService;
    private final GeneradorPdfDocumento generadorDocumento;
    private final ArmadorDocumento armador;
    private final ConfiguracionTallerService configuracion;
    private final ExportacionFacturacionService exportacionService;
    private final RegistroEventosService registroEventos;
    private final UsuarioActual usuarioActual;

    public FacturaController(FacturacionService facturacionService,
                             GeneradorPdfDocumento generadorDocumento,
                             ArmadorDocumento armador,
                             ConfiguracionTallerService configuracion,
                             ExportacionFacturacionService exportacionService,
                             RegistroEventosService registroEventos,
                             UsuarioActual usuarioActual) {
        this.facturacionService = facturacionService;
        this.generadorDocumento = generadorDocumento;
        this.armador = armador;
        this.configuracion = configuracion;
        this.exportacionService = exportacionService;
        this.registroEventos = registroEventos;
        this.usuarioActual = usuarioActual;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @GetMapping
    public PaginaResponse<FacturaResumenResponse> buscar(
            @RequestParam(required = false) TipoFactura tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long receptorId,
            @RequestParam(required = false) Boolean conIva,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Factura> pagina = facturacionService.buscar(tipo, desde, hasta, receptorId, conIva, pageable);
        return PaginaResponse.de(pagina, FacturaResumenResponse::de);
    }

    @GetMapping("/{id}")
    public FacturaResponse obtener(@PathVariable Long id) {
        return FacturaResponse.de(facturacionService.obtener(id));
    }

    @GetMapping("/numero/{serie}/{ejercicio}/{numero}")
    public FacturaResponse obtenerPorNumero(@PathVariable String serie, @PathVariable int ejercicio,
                                            @PathVariable int numero) {
        return FacturaResponse.de(facturacionService.obtenerPorNumero(
                "%s/%d/%06d".formatted(serie, ejercicio, numero)));
    }

    /** Rectificativas emitidas sobre una factura. */
    @GetMapping("/{id}/rectificativas")
    public List<FacturaResumenResponse> rectificativas(@PathVariable Long id) {
        return facturacionService.rectificativasDe(id).stream().map(FacturaResumenResponse::de).toList();
    }

    @GetMapping("/series")
    public List<SerieFacturaResponse> series() {
        return facturacionService.seriesActivas().stream().map(SerieFacturaResponse::de).toList();
    }

    // ------------------------------------------------------------------
    // Emision
    // ------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<FacturaResponse> emitir(@Valid @RequestBody EmitirFacturaRequest peticion,
                                                  UriComponentsBuilder uriBuilder) {
        Factura factura = facturacionService.emitirDesdeOrden(
                peticion.ordenTrabajoId(), peticion.serieId(), peticion.fechaEmision(), usuarioActual.id());

        return ResponseEntity
                .created(uriBuilder.path("/facturas/{id}").build(factura.getId()))
                .body(FacturaResponse.de(factura));
    }

    /** Emite una rectificativa. Es la unica forma de corregir una factura. */
    @PostMapping("/{id}/rectificativas")
    public ResponseEntity<FacturaResponse> rectificar(@PathVariable Long id,
                                                      @Valid @RequestBody RectificarFacturaRequest peticion,
                                                      UriComponentsBuilder uriBuilder) {
        List<LineaAFacturar> lineas = peticion.lineas() == null
                ? List.of()
                : peticion.lineas().stream().map(LineaRectificativaRequest::aDominio).toList();

        Factura rectificativa = facturacionService.emitirRectificativa(
                id, peticion.serieId(), peticion.tipoRectificativa(), peticion.motivo(), lineas,
                peticion.fechaEmision(), usuarioActual.id());

        return ResponseEntity
                .created(uriBuilder.path("/facturas/{id}").build(rectificativa.getId()))
                .body(FacturaResponse.de(rectificativa));
    }

    // ------------------------------------------------------------------
    // PDF
    // ------------------------------------------------------------------

    /**
     * PDF de la factura, con el QR de verificacion y la huella impresa.
     *
     * <p>Se regenera en cada peticion en vez de guardarse: el documento que vale
     * es la fila de la base de datos, y regenerarlo siempre da el mismo
     * resultado porque todos sus datos estan congelados.
     */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> pdf(@PathVariable Long id) {
        Factura factura = facturacionService.obtener(id);
        // Con el formato del taller: mismas bandas y mismos rotulos que el
        // documento que sus clientes llevan años recibiendo.
        byte[] pdf = generadorDocumento.generar(
                armador.factura(factura, factura.getLineas(), configuracion.obligatoria()));

        registroEventos.anotar(factura, TipoEventoFactura.GENERACION_PDF, usuarioActual.id(),
                "Generacion del PDF de la factura " + factura.getNumeroCompleto(), null, null);

        String nombre = "factura-%s.pdf".formatted(factura.getNumeroCompleto().replace('/', '-'));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"%s\"".formatted(nombre))
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(pdf));
    }

    // ------------------------------------------------------------------
    // Verificacion y exportacion
    // ------------------------------------------------------------------

    /**
     * Recorre el registro completo comprobando la cadena de huellas.
     *
     * <p>Un informe sin anomalias significa que ninguna factura se ha alterado ni
     * eliminado desde que se emitio.
     */
    @PostMapping("/verificacion")
    public InformeVerificacion verificarCadena() {
        return facturacionService.verificarCadena(usuarioActual.id());
    }

    @GetMapping(value = "/exportacion/csv", produces = "text/csv")
    public ResponseEntity<Resource> exportarCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        byte[] csv = exportacionService.exportarCsv(desde, hasta, usuarioActual.id());
        return descarga(csv, "libro-facturas.csv", "text/csv; charset=UTF-8");
    }

    /**
     * Los PDF de las facturas seleccionadas, en un ZIP.
     *
     * <p>Los identificadores van en la URL y no en el cuerpo porque esto es una
     * descarga: asi el navegador puede pedirla como cualquier otra y la peticion
     * sigue siendo una lectura. Con el tope de facturas por descarga, la URL no
     * se acerca a ningun limite.
     */
    @GetMapping(value = "/exportacion/pdf", produces = "application/zip")
    public ResponseEntity<Resource> exportarPdfs(@RequestParam(required = false) List<Long> ids) {
        // El parametro se declara opcional para que una peticion sin facturas la
        // conteste el servicio con su motivo, y no Spring con un error generico.
        byte[] zip = exportacionService.exportarPdfsEnZip(ids, usuarioActual.id());

        String nombre = "facturas-%d.zip".formatted(ids.stream().distinct().count());
        return descarga(zip, nombre, "application/zip");
    }

    @GetMapping(value = "/exportacion/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> exportarJson(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        byte[] json = exportacionService.exportarJson(desde, hasta, usuarioActual.id());
        return descarga(json, "libro-facturas.json", MediaType.APPLICATION_JSON_VALUE);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Resource> descarga(byte[] contenido, String nombre, String tipoMime) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(nombre))
                .header(HttpHeaders.CONTENT_TYPE, tipoMime)
                .contentLength(contenido.length)
                .body(new ByteArrayResource(contenido));
    }
}
