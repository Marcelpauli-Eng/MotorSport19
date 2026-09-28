package com.motorsport19.taller.cliente.service;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.cliente.domain.TipoDocumento;
import com.motorsport19.taller.cliente.repository.ClienteRepository;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.common.util.ValidadorDocumento;
import com.motorsport19.taller.fichaje.service.RegistroActividad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final com.motorsport19.taller.orden.repository.OrdenTrabajoRepository ordenRepository;
    private final RegistroActividad registroActividad;

    public ClienteService(ClienteRepository clienteRepository,
                          com.motorsport19.taller.orden.repository.OrdenTrabajoRepository ordenRepository,
                          RegistroActividad registroActividad) {
        this.ordenRepository = ordenRepository;
        this.registroActividad = registroActividad;
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
                         String codigoPostal, String ciudad, String provincia, String pais,
                         String observaciones) {
        Cliente cliente = Cliente.registrar(nombre, apellidos, telefono, email, observaciones);
        String normalizado = ValidadorDocumento.normalizar(documento);
        if (normalizado != null) {
            comprobarDocumentoLibre(normalizado, null);
        }
        return guardar(cliente, tipoDocumento, normalizado, direccion, codigoPostal, ciudad, provincia, pais);
    }

    /**
     * Alta desde un fichero importado: la de {@link #crear}, con dos diferencias.
     *
     * <p><b>No repite a quien ya esta.</b> Con documento ya lo impide {@link #crear}.
     * Sin el, volver a importar el mismo fichero —lo normal despues de corregir
     * las filas que fallaron— duplicaria a todos los que no lo tienen. Se da por
     * el mismo cliente al que coincide en nombre, apellidos, telefono y email.
     *
     * <p><b>Un documento que no vale no deja fuera al cliente.</b> No se guarda
     * como documento, porque con el saldrian facturas que Hacienda rechazaria,
     * pero queda en observaciones para corregirlo en su ficha. Lo que traen los
     * ficheros de otros programas suele ser un NIF mal copiado o un documento
     * extranjero con forma de NIF, y por eso no se puede tirar la fila entera.
     */
    @Transactional
    public Cliente importar(String nombre, String apellidos, String telefono, String email,
                            TipoDocumento tipoDocumento, String documento, String direccion,
                            String codigoPostal, String ciudad, String provincia, String pais,
                            String observaciones) {
        String normalizado = ValidadorDocumento.normalizar(documento);
        if (normalizado != null) {
            // Antes de apartarlo: si ya hay alguien con ese documento, aunque no
            // valga, es este mismo cliente importado otra vez.
            comprobarDocumentoLibre(normalizado, null);
            if (!ValidadorDocumento.admite(tipoDocumento, normalizado)) {
                String nota = "Documento del fichero importado, que no es valido: " + normalizado;
                observaciones = observaciones == null || observaciones.isBlank() ? nota : observaciones + "\n" + nota;
                normalizado = null;
            }
        }

        Cliente cliente = Cliente.registrar(nombre, apellidos, telefono, email, observaciones);
        if (normalizado == null && clienteRepository.existeIgual(
                cliente.getNombre(), cliente.getApellidos(), cliente.getTelefono(), cliente.getEmail())) {
            throw new ConflictoException(
                    "Ya existe un cliente con el mismo nombre y contacto: %s.".formatted(cliente.nombreCompleto()));
        }
        return guardar(cliente, tipoDocumento, normalizado, direccion, codigoPostal, ciudad, provincia, pais);
    }

    /**
     * El cliente al que se refiere una fila importada: por su documento o, si no
     * lo trae, por su nombre completo tal y como sale en su ficha.
     */
    @Transactional(readOnly = true)
    public Long identificar(String documentoONombre) {
        String texto = documentoONombre == null ? "" : documentoONombre.trim().replaceAll("\\s+", " ");
        if (texto.isEmpty()) {
            throw new ReglaNegocioException("Falta el cliente: ponga su NIF o su nombre completo.");
        }
        Optional<Cliente> porDocumento = clienteRepository.buscarPorDocumento(ValidadorDocumento.normalizar(texto));
        if (porDocumento.isPresent()) {
            return porDocumento.get().getId();
        }

        List<Long> porNombre = clienteRepository.idsConNombreCompleto(texto);
        if (porNombre.isEmpty()) {
            throw new RecursoNoEncontradoException(
                    "No hay ningun cliente con el NIF o el nombre '%s'.".formatted(texto));
        }
        if (porNombre.size() > 1) {
            throw new ConflictoException(
                    "Hay %d clientes que se llaman '%s': ponga su NIF para saber cual es."
                            .formatted(porNombre.size(), texto));
        }
        return porNombre.get(0);
    }

    @Transactional
    public Cliente actualizarContacto(Long id, String nombre, String apellidos, String telefono,
                                      String email, String observaciones) {
        Cliente cliente = obtener(id);
        cliente.actualizarContacto(nombre, apellidos, telefono, email, observaciones);
        registroActividad.anotar("EDICION", "cliente", id, "Editó el contacto de " + cliente.nombreCompleto());
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
        registroActividad.anotar("EDICION", "cliente", id, "Editó los datos fiscales de " + cliente.nombreCompleto());
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

    /** @param documento ya normalizado y comprobado que esta libre, o nulo si no hay */
    private Cliente guardar(Cliente cliente, TipoDocumento tipoDocumento, String documento, String direccion,
                            String codigoPostal, String ciudad, String provincia, String pais) {
        if (documento != null) {
            cliente.asignarDatosFiscales(tipoDocumento, documento, direccion, codigoPostal, ciudad,
                    provincia, pais);
        }
        return clienteRepository.save(cliente);
    }

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
