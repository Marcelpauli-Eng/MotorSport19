package com.motorsport19.taller.cliente.web;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.cliente.service.ClienteService;
import com.motorsport19.taller.cliente.web.dto.ActualizarContactoRequest;
import com.motorsport19.taller.cliente.web.dto.ClienteResponse;
import com.motorsport19.taller.cliente.web.dto.ClienteResumenResponse;
import com.motorsport19.taller.cliente.web.dto.CrearClienteRequest;
import com.motorsport19.taller.cliente.web.dto.DatosFiscalesRequest;
import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.moto.service.MotoService;
import com.motorsport19.taller.moto.web.dto.MotoResumenResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final MotoService motoService;

    public ClienteController(ClienteService clienteService, MotoService motoService) {
        this.clienteService = clienteService;
        this.motoService = motoService;
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

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody CrearClienteRequest peticion,
                                                 UriComponentsBuilder uriBuilder) {
        Cliente cliente = clienteService.crear(
                peticion.nombre(), peticion.apellidos(), peticion.telefono(), peticion.email(),
                peticion.tipoDocumento(), peticion.documento(), peticion.direccion(),
                peticion.codigoPostal(), peticion.ciudad(), peticion.provincia(), peticion.pais());

        return ResponseEntity
                .created(uriBuilder.path("/clientes/{id}").build(cliente.getId()))
                .body(ClienteResponse.de(cliente));
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
