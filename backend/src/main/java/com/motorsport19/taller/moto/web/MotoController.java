package com.motorsport19.taller.moto.web;

import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.documento.GeneradorPdfHistorial;
import com.motorsport19.taller.documento.HistorialImprimible;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.moto.service.HistorialServicioService;
import com.motorsport19.taller.moto.service.MotoService;
import com.motorsport19.taller.moto.web.dto.ActualizarMotoRequest;
import com.motorsport19.taller.moto.web.dto.CambioPropietarioRequest;
import com.motorsport19.taller.moto.web.dto.CrearMotoRequest;
import com.motorsport19.taller.moto.web.dto.KilometrajeRequest;
import com.motorsport19.taller.moto.web.dto.MotoResponse;
import com.motorsport19.taller.moto.web.dto.MotoResumenResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

@RestController
@RequestMapping("/motos")
public class MotoController {

    private final MotoService motoService;
    private final HistorialServicioService historialServicio;
    private final GeneradorPdfHistorial generadorHistorial;

    public MotoController(MotoService motoService,
                          HistorialServicioService historialServicio,
                          GeneradorPdfHistorial generadorHistorial) {
        this.motoService = motoService;
        this.historialServicio = historialServicio;
        this.generadorHistorial = generadorHistorial;
    }

    @GetMapping
    public PaginaResponse<MotoResumenResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "true") boolean soloActivas,
            @PageableDefault(size = 20, sort = "matricula", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<Moto> pagina = motoService.buscar(texto, soloActivas, pageable);
        return PaginaResponse.de(pagina, MotoResumenResponse::de);
    }

    @GetMapping("/{id}")
    public MotoResponse obtener(@PathVariable Long id) {
        return MotoResponse.de(motoService.obtener(id));
    }

    /** Busqueda directa por matricula: es como llega la moto al mostrador. */
    @GetMapping("/matricula/{matricula}")
    public MotoResponse obtenerPorMatricula(@PathVariable String matricula) {
        return MotoResponse.de(motoService.obtenerPorMatricula(matricula));
    }

    /**
     * Hoja de vida de la moto en PDF: todo lo que se le ha hecho en el taller.
     *
     * <p>Es el papel que se lleva el cliente cuando vende la moto. Se genera al
     * vuelo desde las ordenes, asi que siempre esta al dia.
     *
     * @param importes a {@code false} sale sin lo que costo cada intervencion,
     *                 para enseñarselo a un tercero sin airear los precios
     */
    @GetMapping(value = "/{id}/historial/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> historialPdf(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "true") boolean importes) {
        HistorialImprimible historial = historialServicio.preparar(id, importes);
        byte[] pdf = generadorHistorial.generar(historial);

        // El historial de una moto trae un solo bloque: el suyo.
        String nombre = "historial-%s.pdf".formatted(
                historial.motos().get(0).vehiculo().matricula().replaceAll("[^A-Za-z0-9-]", ""));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"%s\"".formatted(nombre))
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(pdf));
    }

    @PostMapping
    public ResponseEntity<MotoResponse> crear(@Valid @RequestBody CrearMotoRequest peticion,
                                              UriComponentsBuilder uriBuilder) {
        Moto moto = motoService.crear(
                peticion.clienteId(), peticion.matricula(), peticion.marca(), peticion.modelo(),
                peticion.anio(), peticion.cilindrada(), peticion.color(), peticion.numeroBastidor(),
                peticion.kmActual(), peticion.observaciones());

        return ResponseEntity
                .created(uriBuilder.path("/motos/{id}").build(moto.getId()))
                .body(MotoResponse.de(moto));
    }

    @PutMapping("/{id}")
    public MotoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarMotoRequest peticion) {
        return MotoResponse.de(motoService.actualizar(
                id, peticion.matricula(), peticion.marca(), peticion.modelo(), peticion.anio(),
                peticion.cilindrada(), peticion.color(), peticion.numeroBastidor(), peticion.observaciones()));
    }

    /** El cuentakilometros no retrocede: una lectura menor se rechaza. */
    @PutMapping("/{id}/kilometraje")
    public MotoResponse registrarKilometraje(@PathVariable Long id,
                                             @Valid @RequestBody KilometrajeRequest peticion) {
        return MotoResponse.de(motoService.registrarKilometraje(id, peticion.km()));
    }

    /** Cambio de propietario. El historial de la moto se queda con la moto. */
    @PutMapping("/{id}/propietario")
    public MotoResponse cambiarPropietario(@PathVariable Long id,
                                           @Valid @RequestBody CambioPropietarioRequest peticion) {
        return MotoResponse.de(motoService.cambiarPropietario(id, peticion.nuevoClienteId()));
    }

    @PostMapping("/{id}/baja")
    public MotoResponse darDeBaja(@PathVariable Long id) {
        return MotoResponse.de(motoService.darDeBaja(id));
    }

    @PostMapping("/{id}/reactivacion")
    public MotoResponse reactivar(@PathVariable Long id) {
        return MotoResponse.de(motoService.reactivar(id));
    }
}
