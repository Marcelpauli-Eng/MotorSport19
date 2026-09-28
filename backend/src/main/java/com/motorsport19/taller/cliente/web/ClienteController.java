package com.motorsport19.taller.cliente.web;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.cliente.service.ClienteService;
import com.motorsport19.taller.cliente.web.dto.ActualizarContactoRequest;
import com.motorsport19.taller.cliente.web.dto.ClienteResponse;
import com.motorsport19.taller.cliente.web.dto.ClienteResumenResponse;
import com.motorsport19.taller.cliente.web.dto.CrearClienteRequest;
import com.motorsport19.taller.cliente.web.dto.DatosFiscalesRequest;
import com.motorsport19.taller.common.util.ValidadorDocumento;
import com.motorsport19.taller.common.web.Importador;
import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.documento.GeneradorPdfHistorial;
import com.motorsport19.taller.documento.HistorialImprimible;
import com.motorsport19.taller.moto.service.HistorialServicioService;
import com.motorsport19.taller.moto.service.MotoService;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final MotoService motoService;
    private final HistorialServicioService historialServicio;
    private final GeneradorPdfHistorial generadorHistorial;
    private final Importador importador;

    public ClienteController(ClienteService clienteService, MotoService motoService,
                             HistorialServicioService historialServicio,
                             GeneradorPdfHistorial generadorHistorial, Importador importador) {
        this.clienteService = clienteService;
        this.motoService = motoService;
        this.historialServicio = historialServicio;
        this.generadorHistorial = generadorHistorial;
        this.importador = importador;
    }

    /**
     * Busqueda de mostrador: el mismo parametro {@code texto} filtra por nombre,
     * apellidos, documento, telefono o email.
     */
    @GetMapping
    public PaginaResponse<ClienteResumenResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "true") boolean soloActivos,
            @PageableDefault(size = 20, sort = {"apellidos", "nombre"}, direction = Sort.Direction.ASC)
            Pageable pageable) {

        Page<Cliente> pagina = clienteService.buscar(texto, soloActivos, pageable);
        return PaginaResponse.de(pagina, ClienteResumenResponse::de);
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return ClienteResponse.de(clienteService.obtener(id));
    }

    /** Motos del cliente. Es la consulta previa a abrir una orden de trabajo. */
    @GetMapping("/{id}/motos")
    public List<MotoResumenResponse> motosDelCliente(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean soloActivas) {

        return motoService.buscarPorCliente(id, soloActivas).stream()
                .map(MotoResumenResponse::de)
                .toList();
    }

    /**
     * Hoja de vida del cliente: todas sus motos, cada una con su historial.
     *
     * <p>Es el mismo papel que el de una moto suelta pero del cliente entero.
     * Se genera al vuelo desde las ordenes, asi que siempre esta al dia.
     *
     * @param importes a {@code false} sale sin lo que costo cada intervencion
     */
    @GetMapping(value = "/{id}/historial/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> historialPdf(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "true") boolean importes) {
        HistorialImprimible historial = historialServicio.prepararDeCliente(id, importes);
        byte[] pdf = generadorHistorial.generar(historial);

        String nombre = "historial-cliente-%d.pdf".formatted(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"%s\"".formatted(nombre))
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(pdf));
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody CrearClienteRequest peticion,
                                                 UriComponentsBuilder uriBuilder) {
        Cliente cliente = clienteService.crear(
                peticion.nombre(), peticion.apellidos(), peticion.telefono(), peticion.email(),
                peticion.tipoDocumento(), peticion.documento(), peticion.direccion(),
                peticion.codigoPostal(), peticion.ciudad(), peticion.provincia(), peticion.pais(),
                peticion.observaciones());

        return ResponseEntity
                .created(uriBuilder.path("/clientes/{id}").build(cliente.getId()))
                .body(ClienteResponse.de(cliente));
    }

    /**
     * Alta en bloque desde un fichero. Cada fila es un alta normal, con sus
     * mismas reglas: las que no entran vuelven con el motivo, y las que entran
     * dejando aparte un NIF o un email que no valen, con un aviso.
     */
    @PostMapping("/importacion")
    public Importador.Resultado importar(@RequestBody List<Map<String, Object>> filas) {
        return importador.importar(filas, (fila, avisos) -> {
            usarRazonSocial(fila);
            importador.apartarSiNoVale(fila, CrearClienteRequest.class, "email", "El email", avisos);
            CrearClienteRequest p = importador.leer(fila, CrearClienteRequest.class);
            Cliente cliente = clienteService.importar(p.nombre(), p.apellidos(), p.telefono(), p.email(),
                    p.tipoDocumento(), p.documento(), p.direccion(), p.codigoPostal(), p.ciudad(), p.provincia(),
                    p.pais(), p.observaciones());

            String documento = ValidadorDocumento.normalizar(p.documento());
            if (documento != null && cliente.getDocumento() == null) {
                avisos.add(("El documento '%s' no es un %s valido: ha entrado sin el y queda en observaciones. "
                        + "Si es extranjero, pongalo en su ficha como pasaporte u otro.").formatted(documento,
                        Objects.requireNonNullElse(p.tipoDocumento(), ValidadorDocumento.deducirTipo(documento))));
            }
        });
    }

    /**
     * La razon social manda: es el nombre que va en las facturas y con el que
     * los demas ficheros del programa anterior se refieren al cliente (el de
     * motos pone el nombre de la empresa, no el de su persona de contacto).
     *
     * <p>Si coincide con nombre y apellidos es una persona, y se respeta como
     * viene partida. Si no, es una empresa con su persona de contacto: el
     * cliente es la empresa y la persona queda en observaciones.
     */
    private static void usarRazonSocial(Map<String, Object> fila) {
        String razon = sinEspaciosDeMas(fila.get("razonSocial"));
        String persona = sinEspaciosDeMas(
                Objects.toString(fila.get("nombre"), "") + " " + Objects.toString(fila.get("apellidos"), ""));
        if (razon == null || razon.equalsIgnoreCase(persona)) {
            return;
        }
        fila.put("nombre", razon);
        fila.remove("apellidos");
        if (persona != null) {
            Importador.anotar(fila, "Contacto: " + persona);
        }
    }

    private static String sinEspaciosDeMas(Object texto) {
        String limpio = texto == null ? "" : texto.toString().trim().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    @PutMapping("/{id}/contacto")
    public ClienteResponse actualizarContacto(@PathVariable Long id,
                                              @Valid @RequestBody ActualizarContactoRequest peticion) {
        return ClienteResponse.de(clienteService.actualizarContacto(
                id, peticion.nombre(), peticion.apellidos(), peticion.telefono(), peticion.email(),
                peticion.observaciones()));
    }

    /** Completa o corrige los datos fiscales. Valida el digito de control del documento. */
    @PutMapping("/{id}/datos-fiscales")
    public ClienteResponse actualizarDatosFiscales(@PathVariable Long id,
                                                   @Valid @RequestBody DatosFiscalesRequest peticion) {
        return ClienteResponse.de(clienteService.actualizarDatosFiscales(
                id, peticion.tipoDocumento(), peticion.documento(), peticion.direccion(),
                peticion.codigoPostal(), peticion.ciudad(), peticion.provincia(), peticion.pais()));
    }

    /**
     * Baja logica. No existe un DELETE en esta API a proposito: los clientes
     * nunca se borran, y la base de datos rechazaria el intento igualmente.
     */
    @PostMapping("/{id}/baja")
    public ClienteResponse darDeBaja(@PathVariable Long id) {
        return ClienteResponse.de(clienteService.darDeBaja(id));
    }

    @PostMapping("/{id}/reactivacion")
    public ClienteResponse reactivar(@PathVariable Long id) {
        return ClienteResponse.de(clienteService.reactivar(id));
    }
}
