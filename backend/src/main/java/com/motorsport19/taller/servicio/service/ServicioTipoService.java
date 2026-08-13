package com.motorsport19.taller.servicio.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.inventario.service.PiezaService;
import com.motorsport19.taller.servicio.domain.ServicioTipo;
import com.motorsport19.taller.servicio.repository.ServicioTipoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mantenimiento de las plantillas de servicio.
 *
 * <p>Volcar una plantilla en una orden NO vive aqui: eso lo hace
 * {@code OrdenTrabajoService}, que es quien sabe de tarifas, de stock y de si
 * la OT admite todavia cambios. Esta clase solo custodia los moldes.
 */
@Service
public class ServicioTipoService {

    private final ServicioTipoRepository repositorio;
    private final PiezaService piezaService;

    public ServicioTipoService(ServicioTipoRepository repositorio, PiezaService piezaService) {
        this.repositorio = repositorio;
        this.piezaService = piezaService;
    }

    @Transactional(readOnly = true)
    public List<ServicioTipo> listar(boolean soloActivos) {
        return soloActivos ? repositorio.listarActivos() : repositorio.listarTodos();
    }

    @Transactional(readOnly = true)
    public ServicioTipo obtener(Long id) {
        return repositorio.buscarConLineas(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el servicio tipo", id));
    }

    @Transactional
    public ServicioTipo crear(String nombre, String descripcion, List<LineaPedida> lineas) {
        exigirNombreLibre(nombre, null);
        ServicioTipo servicio = ServicioTipo.crear(nombre, descripcion);
        servicio.reemplazarLineas(resolver(lineas));
        return repositorio.save(servicio);
    }

    @Transactional
    public ServicioTipo actualizar(Long id, String nombre, String descripcion, List<LineaPedida> lineas) {
        ServicioTipo servicio = obtener(id);
        exigirNombreLibre(nombre, id);
        servicio.renombrar(nombre, descripcion);
        servicio.reemplazarLineas(resolver(lineas));
        return servicio;
    }

    /**
     * Baja logica.
     *
     * <p>No hay borrado de verdad a proposito. Una plantilla retirada sigue
     * explicando por que una OT del ano pasado tiene esas seis lineas, y
     * borrarla no libera nada que importe.
     */
    @Transactional
    public ServicioTipo cambiarActivo(Long id, boolean activo) {
        ServicioTipo servicio = obtener(id);
        if (activo) {
            servicio.activar();
        } else {
            servicio.desactivar();
        }
        return servicio;
    }

    // ==================================================================

    /**
     * Convierte lo que llega del formulario en lo que entiende el dominio.
     *
     * <p>Aqui es donde se resuelven las piezas contra el catalogo, y donde se
     * rechaza una pieza dada de baja: meterla en una plantilla garantiza que el
     * volcado falle mas adelante, en mitad del mostrador y con el cliente
     * delante. Mejor decirlo al guardar la plantilla.
     */
    private List<ServicioTipo.NuevaLinea> resolver(List<LineaPedida> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            throw new ReglaNegocioException(
                    "Un servicio tipo sin lineas no ahorra nada: anade al menos una.");
        }
        return lineas.stream().map(this::resolverUna).toList();
    }

    private ServicioTipo.NuevaLinea resolverUna(LineaPedida linea) {
        if (linea.piezaId() == null) {
            return new ServicioTipo.NuevaLinea(linea.descripcion(), null, linea.cantidad());
        }
        Pieza pieza = piezaService.obtener(linea.piezaId());
        if (!pieza.isActivo()) {
            throw new ReglaNegocioException(
                    ("La pieza %s esta dada de baja: no se puede dejar dentro de un servicio tipo, "
                     + "porque al volcarlo en una OT fallaria.").formatted(pieza.getSku()));
        }
        return new ServicioTipo.NuevaLinea(null, pieza, linea.cantidad());
    }

    private void exigirNombreLibre(String nombre, Long idExcluido) {
        if (nombre != null && repositorio.existeNombre(nombre, idExcluido)) {
            throw new ConflictoException(
                    "Ya hay un servicio tipo que se llama «%s».".formatted(nombre.trim()));
        }
    }

    /** Linea tal como llega de la pantalla: o descripcion, o pieza. */
    public record LineaPedida(String descripcion, Long piezaId, BigDecimal cantidad) {}
}
