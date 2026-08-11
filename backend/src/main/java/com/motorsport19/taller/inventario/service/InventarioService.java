package com.motorsport19.taller.inventario.service;

import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.inventario.domain.MovimientoStock;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.inventario.domain.TipoMovimiento;
import com.motorsport19.taller.inventario.repository.FiltroMovimientos;
import com.motorsport19.taller.inventario.repository.MovimientoStockRepository;
import com.motorsport19.taller.inventario.repository.PiezaRepository;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Movimientos de stock: la unica puerta por la que cambian las existencias.
 *
 * <p>Aqui no se escribe {@code pieza.stock_actual} en ningun sitio, y no es un
 * olvido: esa columna la mantiene en exclusiva un trigger de PostgreSQL a partir
 * del libro de movimientos. Este servicio se limita a registrar asientos y a
 * releer la pieza despues para devolver el saldo actualizado.
 *
 * <p>Las comprobaciones de existencias se hacen <b>dos veces</b> a proposito:
 * aqui, tomando un bloqueo pesimista sobre la pieza para poder dar un mensaje de
 * negocio claro; y en la base de datos, que es la que garantiza de verdad que el
 * stock nunca queda negativo aunque alguien entre por otro camino.
 */
@Service
public class InventarioService {

    private static final Logger log = LoggerFactory.getLogger(InventarioService.class);

    private final PiezaRepository piezaRepository;
    private final MovimientoStockRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EntityManager entityManager;

    public InventarioService(PiezaRepository piezaRepository,
                             MovimientoStockRepository movimientoRepository,
                             UsuarioRepository usuarioRepository,
                             EntityManager entityManager) {
        this.piezaRepository = piezaRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------------
    // Registro de movimientos
    // ------------------------------------------------------------------

    /** Entrada por compra a proveedor. */
    @Transactional
    public MovimientoStock registrarEntrada(Long piezaId, BigDecimal cantidad, String documentoProveedor,
                                            BigDecimal precioCosteUnitario, String motivo, Long usuarioId) {
        Pieza pieza = cargarPiezaBloqueada(piezaId);
        Usuario usuario = cargarUsuario(usuarioId);

        MovimientoStock movimiento = MovimientoStock.entrada(
                pieza, cantidad, usuario, documentoProveedor, precioCosteUnitario, motivo);

        return registrar(movimiento, pieza);
    }

    /**
     * Salida de almacen no ligada a una orden de trabajo (uso interno, garantia,
     * merma). Exige motivo, igual que la base de datos.
     */
    @Transactional
    public MovimientoStock registrarSalida(Long piezaId, BigDecimal cantidad, String motivo, Long usuarioId) {
        Pieza pieza = cargarPiezaBloqueada(piezaId);
        comprobarExistencias(pieza, cantidad);

        MovimientoStock movimiento = MovimientoStock.salida(
                pieza, cantidad, cargarUsuario(usuarioId), null, null, motivo);

        return registrar(movimiento, pieza);
    }

    /**
     * Intenta servir material para una orden de trabajo <b>sin lanzar excepcion</b>
     * cuando no hay existencias.
     *
     * <p>Esta es la forma que usa el servicio de ordenes de trabajo, y la
     * distincion no es cosmetica: si este metodo dejara escapar una excepcion,
     * Spring marcaria la transaccion como rollback-only y el llamante no podria
     * seguir sirviendo el resto de lineas ni registrar el cambio de estado,
     * aunque capturase la excepcion. Al devolver la falta de stock como dato, la
     * OT puede quedarse con lo que si habia y pasar a ESPERANDO_PIEZAS.
     *
     * <p>El bloqueo pesimista sobre la pieza se mantiene desde la comprobacion
     * hasta el asiento, asi que no hay hueco entre comprobar y consumir.
     */
    @Transactional
    public IntentoConsumo intentarConsumoEnOrden(Long piezaId, BigDecimal cantidad, OrdenTrabajo orden,
                                                 LineaOT linea, Long usuarioId) {
        Pieza pieza = cargarPiezaBloqueada(piezaId);

        if (cantidad == null || cantidad.signum() <= 0) {
            throw new com.motorsport19.taller.common.error.ReglaNegocioException(
                    "La cantidad a consumir debe ser mayor que cero.");
        }
        if (!pieza.hayExistenciasPara(cantidad)) {
            return IntentoConsumo.sinExistencias(pieza.existencias(), cantidad);
        }

        MovimientoStock movimiento = MovimientoStock.salida(
                pieza, cantidad, cargarUsuario(usuarioId), orden, linea, null);

        return IntentoConsumo.servido(registrar(movimiento, pieza), pieza.existencias(), cantidad);
    }

    /**
     * Salida por consumo en una orden de trabajo, fallando si no hay existencias.
     *
     * <p>Variante estricta de {@link #intentarConsumoEnOrden} para cuando quien
     * llama quiere que la operacion entera se deshaga si falta material.
     */
    @Transactional
    public MovimientoStock registrarConsumoEnOrden(Long piezaId, BigDecimal cantidad, OrdenTrabajo orden,
                                                   LineaOT linea, Long usuarioId) {
        IntentoConsumo intento = intentarConsumoEnOrden(piezaId, cantidad, orden, linea, usuarioId);
        if (!intento.servido()) {
            Pieza pieza = piezaRepository.findById(piezaId).orElseThrow();
            throw new StockInsuficienteException(pieza.getSku(), intento.disponible(), cantidad);
        }
        return intento.movimiento();
    }

    /** Devolucion al almacen de una pieza que no llego a usarse. */
    @Transactional
    public MovimientoStock registrarDevolucion(Long piezaId, BigDecimal cantidad, OrdenTrabajo orden,
                                               LineaOT linea, String motivo, Long usuarioId) {
        Pieza pieza = cargarPiezaBloqueada(piezaId);
        Usuario usuario = cargarUsuario(usuarioId);

        MovimientoStock movimiento = MovimientoStock.devolucion(pieza, cantidad, usuario, orden, linea, motivo);

        return registrar(movimiento, pieza);
    }

    /**
     * Ajuste tras inventario fisico. Acepta cantidad con signo y exige motivo.
     *
     * <p>Es tambien la unica forma de corregir un movimiento equivocado: el libro
     * es inmutable, asi que se compensa con otro asiento en vez de reescribir el
     * anterior.
     */
    @Transactional
    public MovimientoStock registrarAjuste(Long piezaId, BigDecimal cantidadConSigno, String motivo,
                                           Long usuarioId) {
        Pieza pieza = cargarPiezaBloqueada(piezaId);

        if (cantidadConSigno != null && cantidadConSigno.signum() < 0) {
            comprobarExistencias(pieza, cantidadConSigno.abs());
        }

        MovimientoStock movimiento = MovimientoStock.ajuste(
                pieza, cantidadConSigno, cargarUsuario(usuarioId), motivo);

        return registrar(movimiento, pieza);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public boolean hayExistenciasSuficientes(Long piezaId, BigDecimal cantidad) {
        Pieza pieza = piezaRepository.findById(piezaId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la pieza", piezaId));
        return pieza.hayExistenciasPara(cantidad);
    }

    /** Piezas activas que han caido al minimo o por debajo. */
    @Transactional(readOnly = true)
    public List<Pieza> alertasDeStock() {
        return piezaRepository.buscarBajoMinimo();
    }

    /**
     * Libro de movimientos, con todos los filtros opcionales.
     *
     * <p>El orden por defecto es el ultimo movimiento primero, que es como se
     * mira un libro: lo que acaba de pasar arriba. El {@code id} desempata los
     * que comparten instante, para que dos consultas seguidas no devuelvan las
     * mismas filas en distinto orden.
     */
    @Transactional(readOnly = true)
    public Page<MovimientoStock> consultarMovimientos(Long piezaId, TipoMovimiento tipo, Instant desde,
                                                      Instant hasta, Pageable pageable) {
        Pageable orden = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "fecha", "id"));

        return movimientoRepository.findAll(FiltroMovimientos.de(piezaId, tipo, desde, hasta), orden);
    }

    @Transactional(readOnly = true)
    public Page<MovimientoStock> consultarMovimientosDePieza(Long piezaId, Pageable pageable) {
        if (!piezaRepository.existsById(piezaId)) {
            throw RecursoNoEncontradoException.de("la pieza", piezaId);
        }
        return movimientoRepository.buscarPorPieza(piezaId, pageable);
    }

    // ------------------------------------------------------------------

    /**
     * Persiste el asiento y relee la pieza.
     *
     * <p>El {@code flush} fuerza el INSERT para que se dispare el trigger, y el
     * {@code refresh} trae el {@code stock_actual} que ha dejado. Sin ese refresco
     * devolveriamos el saldo anterior al movimiento.
     */
    private MovimientoStock registrar(MovimientoStock movimiento, Pieza pieza) {
        MovimientoStock guardado = movimientoRepository.saveAndFlush(movimiento);
        entityManager.refresh(pieza);

        log.debug("Movimiento {} de {} unidades sobre {}: stock {} -> {}",
                guardado.getTipo(), guardado.getCantidad(), pieza.getSku(),
                guardado.getStockAnterior(), guardado.getStockResultante());

        if (pieza.estaBajoMinimo()) {
            log.info("ALERTA DE STOCK: la pieza {} ({}) esta en {} unidades, por debajo del minimo de {}",
                    pieza.getSku(), pieza.getDescripcion(), pieza.existencias(), pieza.getStockMinimo());
        }
        return guardado;
    }

    /**
     * Carga la pieza bloqueando su fila hasta el final de la transaccion.
     *
     * <p>Sin el bloqueo, dos mecanicos consumiendo a la vez la ultima unidad
     * podrian pasar ambos la comprobacion de existencias.
     */
    private Pieza cargarPiezaBloqueada(Long piezaId) {
        return piezaRepository.buscarParaActualizarStock(piezaId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la pieza", piezaId));
    }

    private void comprobarExistencias(Pieza pieza, BigDecimal cantidad) {
        if (cantidad != null && cantidad.signum() > 0 && !pieza.hayExistenciasPara(cantidad)) {
            throw new StockInsuficienteException(pieza.getSku(), pieza.existencias(), cantidad);
        }
    }

    private Usuario cargarUsuario(Long usuarioId) {
        if (usuarioId == null) {
            // Hasta la fase 5 no hay usuario autenticado; el movimiento queda sin firmar.
            return null;
        }
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el usuario", usuarioId));
    }
}
