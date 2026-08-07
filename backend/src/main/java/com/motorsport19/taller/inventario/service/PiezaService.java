package com.motorsport19.taller.inventario.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.configuracion.repository.TipoIvaRepository;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.inventario.domain.Proveedor;
import com.motorsport19.taller.inventario.repository.PiezaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Alta y mantenimiento del catalogo de recambios.
 *
 * <p>Fijate en que aqui NO hay ningun metodo para cambiar existencias: eso vive
 * en {@link InventarioService} y pasa siempre por un movimiento de stock.
 */
@Service
public class PiezaService {

    private final PiezaRepository piezaRepository;
    private final ProveedorService proveedorService;
    private final TipoIvaRepository tipoIvaRepository;
    private final InventarioService inventarioService;

    public PiezaService(PiezaRepository piezaRepository,
                        ProveedorService proveedorService,
                        TipoIvaRepository tipoIvaRepository,
                        InventarioService inventarioService) {
        this.piezaRepository = piezaRepository;
        this.proveedorService = proveedorService;
        this.tipoIvaRepository = tipoIvaRepository;
        this.inventarioService = inventarioService;
    }

    @Transactional(readOnly = true)
    public Pieza obtener(Long id) {
        // Con el proveedor ya resuelto: la respuesta lo muestra y para entonces
        // la sesion de persistencia esta cerrada.
        return piezaRepository.buscarConProveedor(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la pieza", id));
    }

    @Transactional(readOnly = true)
    public Pieza obtenerPorSku(String sku) {
        return piezaRepository.buscarPorSkuConProveedor(sku)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay ninguna pieza con SKU %s.".formatted(sku)));
    }

    /** Familias en uso, para los desplegables. */
    @Transactional(readOnly = true)
    public java.util.List<String> familias() {
        return piezaRepository.familias();
    }

    @Transactional(readOnly = true)
    public Page<Pieza> buscar(String texto, String familia, Long proveedorId, boolean soloActivas, boolean soloBajoMinimo,
                              Pageable pageable) {
        String filtro = (texto == null || texto.isBlank()) ? null : texto.trim();
        String grupo = (familia == null || familia.isBlank()) ? null : familia.trim();
        return piezaRepository.buscar(filtro, grupo, proveedorId, soloActivas, soloBajoMinimo, pageable);
    }

    /**
     * Da de alta una pieza.
     *
     * <p>Si se indica un stock inicial, no se escribe en la pieza: se registra un
     * movimiento de ENTRADA. Asi hasta la primera unidad del almacen tiene su
     * asiento en el libro.
     */
    @Transactional
    public Pieza crear(String sku, String descripcion, String marca, String ubicacion, String familia, BigDecimal stockMinimo,
                       BigDecimal precioCoste, BigDecimal precioVenta, String tipoIva, Long proveedorId,
                       String unidadMedida, String observaciones, BigDecimal stockInicial, Long usuarioId) {
        if (piezaRepository.existeConSku(sku)) {
            throw new ConflictoException("Ya existe una pieza con el SKU %s.".formatted(sku.toUpperCase()));
        }
        comprobarTipoIva(tipoIva);
        Proveedor proveedor = proveedorId == null ? null : proveedorService.obtener(proveedorId);

        Pieza pieza = Pieza.registrar(sku, descripcion, marca, ubicacion, familia, stockMinimo, precioCoste,
                precioVenta, tipoIva, proveedor, unidadMedida, observaciones);
        Pieza guardada = piezaRepository.saveAndFlush(pieza);

        if (stockInicial != null && stockInicial.signum() > 0) {
            inventarioService.registrarEntrada(guardada.getId(), stockInicial, null, precioCoste,
                    "Carga inicial de inventario", usuarioId);
        }
        return guardada;
    }

    @Transactional
    public Pieza actualizar(Long id, String sku, String descripcion, String marca, String ubicacion, String familia,
                            BigDecimal stockMinimo, String tipoIva, Long proveedorId, String unidadMedida,
                            String observaciones) {
        Pieza pieza = obtener(id);
        if (piezaRepository.existeOtraConSku(sku, id)) {
            throw new ConflictoException("Ya existe otra pieza con el SKU %s.".formatted(sku.toUpperCase()));
        }
        comprobarTipoIva(tipoIva);
        Proveedor proveedor = proveedorId == null ? null : proveedorService.obtener(proveedorId);

        pieza.actualizarDatos(sku, descripcion, marca, ubicacion, familia, stockMinimo, tipoIva, proveedor,
                unidadMedida, observaciones);
        return pieza;
    }

    /**
     * Cambia los precios de catalogo. No afecta a ninguna OT abierta: las lineas
     * ya creadas conservan el precio con el que se anadieron.
     */
    @Transactional
    public Pieza actualizarPrecios(Long id, BigDecimal precioCoste, BigDecimal precioVenta) {
        Pieza pieza = obtener(id);
        pieza.actualizarPrecios(precioCoste, precioVenta);
        return pieza;
    }

    /**
     * Baja logica.
     *
     * <p>Se avisa si aun quedan existencias: dar de baja una pieza con stock deja
     * unidades en el almacen que ya no se pueden usar, asi que conviene sacarlas
     * con un ajuste antes.
     */
    @Transactional
    public Pieza darDeBaja(Long id) {
        Pieza pieza = obtener(id);
        if (pieza.existencias().signum() > 0) {
            throw new ConflictoException(
                    ("La pieza %s todavia tiene %s unidades en almacen. Regularice el stock con un ajuste "
                     + "antes de darla de baja.").formatted(pieza.getSku(), pieza.existencias().toPlainString()));
        }
        pieza.darDeBaja();
        return pieza;
    }

    @Transactional
    public Pieza reactivar(Long id) {
        Pieza pieza = obtener(id);
        pieza.reactivar();
        return pieza;
    }

    // ------------------------------------------------------------------

    private void comprobarTipoIva(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        if (!tipoIvaRepository.existsById(codigo)) {
            throw new ConflictoException("El tipo de IVA '%s' no existe en el catalogo.".formatted(codigo));
        }
    }
}
