package com.motorsport19.taller.cliente.service;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.cliente.domain.TipoDocumento;
import com.motorsport19.taller.cliente.repository.ClienteRepository;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.util.ValidadorDocumento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final com.motorsport19.taller.orden.repository.OrdenTrabajoRepository ordenRepository;

    public ClienteService(ClienteRepository clienteRepository,
                          com.motorsport19.taller.orden.repository.OrdenTrabajoRepository ordenRepository) {
        this.ordenRepository = ordenRepository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public Cliente obtener(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el cliente", id));
    }

    @Transactional(readOnly = true)
    public Page<Cliente> buscar(String texto, boolean soloActivos, Pageable pageable) {
        String filtro = (texto == null || texto.isBlank()) ? null : texto.trim();
        return clienteRepository.buscar(filtro, soloActivos, pageable);
    }

    /**
     * Alta de cliente. Los datos fiscales son opcionales: se puede abrir la ficha
     * de quien entra por la puerta con una averia y completarla despues, pero no
     * se le podra facturar hasta que esten.
     */
    @Transactional
    public Cliente crear(String nombre, String apellidos, String telefono, String email,
                         TipoDocumento tipoDocumento, String documento, String direccion,
                         String codigoPostal, String ciudad, String provincia, String pais) {
        Cliente cliente = Cliente.registrar(nombre, apellidos, telefono, email);

        String normalizado = ValidadorDocumento.normalizar(documento);
        if (normalizado != null) {
            comprobarDocumentoLibre(normalizado, null);
            cliente.asignarDatosFiscales(tipoDocumento, normalizado, direccion, codigoPostal, ciudad,
                    provincia, pais);
        }
        return clienteRepository.save(cliente);
    }

    @Transactional
    public Cliente actualizarContacto(Long id, String nombre, String apellidos, String telefono,
                                      String email, String observaciones) {
        Cliente cliente = obtener(id);
        cliente.actualizarContacto(nombre, apellidos, telefono, email, observaciones);
        return cliente;
    }

    @Transactional
    public Cliente actualizarDatosFiscales(Long id, TipoDocumento tipoDocumento, String documento,
                                           String direccion, String codigoPostal, String ciudad,
                                           String provincia, String pais) {
        Cliente cliente = obtener(id);
        String normalizado = ValidadorDocumento.normalizar(documento);
        if (normalizado != null) {
            comprobarDocumentoLibre(normalizado, id);
        }
        cliente.asignarDatosFiscales(tipoDocumento, documento, direccion, codigoPostal, ciudad, provincia, pais);
        return cliente;
    }

    @Transactional
    public Cliente darDeBaja(Long id) {
        Cliente cliente = obtener(id);

        // Mismo motivo que con las motos: con trabajo suyo en el taller, darlo
        // de baja lo hace desaparecer de las busquedas a mitad de faena.
        long abiertas = ordenRepository.contarAbiertasDeCliente(id);
        if (abiertas > 0) {
            throw new ConflictoException(
                    ("%s tiene %d orden(es) de trabajo sin cerrar. Cierrelas antes de darlo de baja.")
                            .formatted(cliente.nombreCompleto(), abiertas));
        }

        cliente.darDeBaja();
        return cliente;
    }

    @Transactional
    public Cliente reactivar(Long id) {
        Cliente cliente = obtener(id);
        cliente.reactivar();
        return cliente;
    }

    /**
     * Comprueba si el cliente reune los datos que exige una factura.
     *
     * <p>El modulo de facturacion la llamara antes de emitir; se expone ya para
     * que el mostrador pueda avisar antes de cerrar la orden de trabajo.
     */
    @Transactional(readOnly = true)
    public boolean puedeSerFacturado(Long id) {
        return obtener(id).tieneDatosFiscalesCompletos();
    }

    // ------------------------------------------------------------------

    /**
     * El indice unico de la base de datos ya impide dos clientes con el mismo
     * documento; se comprueba antes para poder dar un mensaje util en vez de un
     * error de clave duplicada.
     */
    private void comprobarDocumentoLibre(String documento, Long idExcluido) {
        boolean ocupado = idExcluido == null
                ? clienteRepository.existeConDocumento(documento)
                : clienteRepository.existeOtroConDocumento(documento, idExcluido);

        if (ocupado) {
            Cliente existente = clienteRepository.buscarPorDocumento(documento).orElse(null);
            String detalle = existente != null ? " (%s)".formatted(existente.nombreCompleto()) : "";
            throw new ConflictoException(
                    "Ya existe un cliente con el documento %s%s.".formatted(documento, detalle));
        }
    }
}
