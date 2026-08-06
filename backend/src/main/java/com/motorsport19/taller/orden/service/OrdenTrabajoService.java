package com.motorsport19.taller.orden.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.configuracion.domain.TipoIva;
import com.motorsport19.taller.configuracion.repository.ConfiguracionTallerRepository;
import com.motorsport19.taller.configuracion.repository.TipoIvaRepository;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.inventario.repository.MovimientoStockRepository;
import com.motorsport19.taller.inventario.service.IntentoConsumo;
import com.motorsport19.taller.inventario.service.InventarioService;
import com.motorsport19.taller.inventario.service.PiezaService;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.moto.service.MotoService;
import com.motorsport19.taller.orden.domain.CambioEstadoOT;
import com.motorsport19.taller.orden.domain.ContadorOt;
import com.motorsport19.taller.orden.domain.EstadoOT;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.repository.CambioEstadoOTRepository;
import com.motorsport19.taller.orden.repository.ContadorOtRepository;
import com.motorsport19.taller.orden.repository.LineaOTRepository;
import com.motorsport19.taller.orden.repository.OrdenTrabajoRepository;
import com.motorsport19.taller.seguridad.UsuarioActual;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Ciclo de vida de las ordenes de trabajo.
 *
 * <p>El servicio no decide transiciones: se las pide a {@link OrdenTrabajo}, que
 * las valida contra la maquina de estados. Lo que si hace es coordinar lo que
 * rodea a cada transicion — numeracion, kilometraje, y sobre todo el consumo de
 * almacen al entrar en reparacion.
 */
@Service
public class OrdenTrabajoService {

    private static final Logger log = LoggerFactory.getLogger(OrdenTrabajoService.class);

    private final OrdenTrabajoRepository ordenRepository;
    private final LineaOTRepository lineaRepository;
    private final ContadorOtRepository contadorRepository;
    private final CambioEstadoOTRepository cambioEstadoRepository;
    private final MovimientoStockRepository movimientoRepository;
    private final MotoService motoService;
    private final PiezaService piezaService;
    private final InventarioService inventarioService;
    private final UsuarioRepository usuarioRepository;
    private final TipoIvaRepository tipoIvaRepository;
    private final ConfiguracionTallerRepository configuracionRepository;
    private final UsuarioActual usuarioActual;

    public OrdenTrabajoService(OrdenTrabajoRepository ordenRepository,
                               LineaOTRepository lineaRepository,
                               ContadorOtRepository contadorRepository,
                               CambioEstadoOTRepository cambioEstadoRepository,
                               MovimientoStockRepository movimientoRepository,
                               MotoService motoService,
                               PiezaService piezaService,
                               InventarioService inventarioService,
                               UsuarioRepository usuarioRepository,
                               TipoIvaRepository tipoIvaRepository,
                               ConfiguracionTallerRepository configuracionRepository,
                               UsuarioActual usuarioActual) {
        this.ordenRepository = ordenRepository;
        this.lineaRepository = lineaRepository;
        this.contadorRepository = contadorRepository;
        this.cambioEstadoRepository = cambioEstadoRepository;
        this.movimientoRepository = movimientoRepository;
        this.motoService = motoService;
        this.piezaService = piezaService;
        this.inventarioService = inventarioService;
        this.usuarioRepository = usuarioRepository;
        this.tipoIvaRepository = tipoIvaRepository;
        this.configuracionRepository = configuracionRepository;
        this.usuarioActual = usuarioActual;
    }

    // ==================================================================
    // Consultas
    // ==================================================================

    @Transactional(readOnly = true)
    public OrdenTrabajo obtener(Long id) {
        return ordenRepository.buscarConDetalle(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la orden de trabajo", id));
    }

    @Transactional(readOnly = true)
    public OrdenTrabajo obtenerPorCodigo(String codigo) {
        return ordenRepository.buscarPorCodigo(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la orden de trabajo %s.".formatted(codigo)));
    }

    /**
     * Busca ordenes de trabajo.
     *
     * <p>A un TECNICO se le fuerza el filtro a sus propias ordenes. Es una
     * restriccion de datos, no de rutas: la ruta es la misma para todos, pero un
     * tecnico no ve el tablero completo del taller.
     *
     * <p>La ficha individual ({@link #obtener}) si es accesible para cualquier
     * tecnico: en un taller pequeno se cubren entre ellos y necesitan poder
     * consultar la orden de un companero. Lo que no pueden es trabajarla.
     */
    @Transactional(readOnly = true)
    public Page<OrdenTrabajo> buscar(EstadoOT estado, Long tecnicoId, Long clienteId, Long motoId,
                                     boolean soloAbiertas, Pageable pageable) {
        Long filtroTecnico = usuarioActual.esTecnico() ? usuarioActual.id() : tecnicoId;
        return ordenRepository.buscar(estado, filtroTecnico, clienteId, motoId, soloAbiertas, pageable);
    }

    /**
     * Comprueba que el usuario puede modificar esta orden.
     *
     * <p>Un tecnico solo trabaja sus ordenes. Mostrador y administracion pueden
     * con todas, porque son quienes reasignan y cierran.
     */
    private void exigirPermisoDeTrabajo(OrdenTrabajo orden) {
        if (!usuarioActual.esTecnico()) {
            return;
        }
        Long tecnicoAsignado = idDelTecnico(orden);
        if (tecnicoAsignado == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "La orden %s no tiene tecnico asignado. Pasala a diagnostico para hacerte cargo."
                            .formatted(orden.codigoVisible()));
        }
        if (!tecnicoAsignado.equals(usuarioActual.id())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "La orden %s esta asignada a otro tecnico.".formatted(orden.codigoVisible()));
        }
    }

    /**
     * Variante mas laxa: deja pasar la orden sin asignar, rechaza la de otro.
     *
     * <p>Se usa solo al entrar en diagnostico, que es el momento en que un tecnico
     * se hace cargo de una orden que aun no era de nadie.
     */
    private void exigirQueNoSeaDeOtroTecnico(OrdenTrabajo orden) {
        if (!usuarioActual.esTecnico()) {
            return;
        }
        Long tecnicoAsignado = idDelTecnico(orden);
        if (tecnicoAsignado != null && !tecnicoAsignado.equals(usuarioActual.id())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "La orden %s esta asignada a otro tecnico.".formatted(orden.codigoVisible()));
        }
    }

    private Long idDelTecnico(OrdenTrabajo orden) {
        return orden.getTecnico() == null ? null : orden.getTecnico().getId();
    }

    @Transactional(readOnly = true)
    public List<OrdenTrabajo> historialDeMoto(Long motoId) {
        motoService.obtener(motoId);
        return ordenRepository.historialDeMoto(motoId);
    }

    @Transactional(readOnly = true)
    public List<LineaOT> lineasDe(Long ordenId) {
        return lineaRepository.buscarPorOrden(ordenId);
    }

    @Transactional(readOnly = true)
    public List<CambioEstadoOT> historialDe(Long ordenId) {
        return cambioEstadoRepository.buscarPorOrden(ordenId);
    }

    // ==================================================================
    // Apertura
    // ==================================================================

    /**
     * Abre una orden de trabajo para una moto.
     *
     * <p>Consume un numero del contador del ejercicio con la fila bloqueada, y
     * aprovecha el kilometraje de entrada para actualizar el de la moto.
     */
    @Transactional
    public OrdenTrabajo abrir(Long motoId, String problemaReportado, int kmEntrada,
                              LocalDate fechaEstimadaSalida, Long tecnicoId, String observaciones,
                              Long usuarioId) {
        Moto moto = motoService.obtener(motoId);

        // Dos OT abiertas para la misma moto casi siempre es un despiste del
        // mostrador, no una situacion real.
        long abiertas = ordenRepository.contarAbiertasDeMoto(motoId);
        if (abiertas > 0) {
            throw new ConflictoException(
                    ("La moto %s ya tiene %d orden(es) de trabajo sin cerrar. Cierre la anterior antes de "
                     + "abrir otra.").formatted(moto.getMatricula(), abiertas));
        }

        int ejercicio = Year.now().getValue();
        int numero = siguienteNumero(ejercicio);

        OrdenTrabajo orden = OrdenTrabajo.abrir(
                ejercicio, numero, moto, tarifaHoraVigente(), kmEntrada, problemaReportado,
                fechaEstimadaSalida, cargarUsuario(tecnicoId), observaciones, cargarUsuario(usuarioId));

        OrdenTrabajo guardada = ordenRepository.save(orden);

        // El kilometraje de entrada es la lectura mas reciente que tenemos de la
        // moto. Si es menor que el registrado, el dominio de Moto lo rechazara.
        if (kmEntrada > moto.getKmActual()) {
            moto.registrarKilometraje(kmEntrada);
        }

        log.info("Abierta la orden {} para la moto {}", guardada.codigoVisible(), moto.getMatricula());
        return guardada;
    }

    // ==================================================================
    // Transiciones
    // ==================================================================

    /**
     * Pasa la orden a diagnostico, asignando tecnico si se indica.
     *
     * <p>Aqui no vale {@link #exigirPermisoDeTrabajo}: coger una orden todavia sin
     * asignar es justo lo que hace un tecnico al empezar el dia. Lo que se impide
     * es quitarsela a un companero.
     */
    @Transactional
    public OrdenTrabajo iniciarDiagnostico(Long id, Long tecnicoId, Long usuarioId) {
        OrdenTrabajo orden = obtener(id);
        exigirQueNoSeaDeOtroTecnico(orden);
        orden.iniciarDiagnostico(cargarUsuario(tecnicoId), cargarUsuario(usuarioId));
        return orden;
    }

    @Transactional
    public OrdenTrabajo registrarDiagnostico(Long id, String diagnostico) {
        OrdenTrabajo orden = obtener(id);
        exigirPermisoDeTrabajo(orden);
        orden.registrarDiagnostico(diagnostico);
        return orden;
    }

    @Transactional
    public OrdenTrabajo presupuestar(Long id, Long usuarioId) {
        OrdenTrabajo orden = cargarConLineas(id);
        exigirPermisoDeTrabajo(orden);
        orden.presupuestar(cargarUsuario(usuarioId));
        return orden;
    }

    @Transactional
    public OrdenTrabajo aprobar(Long id, String aprobadoPor, Long usuarioId) {
        OrdenTrabajo orden = obtener(id);
        orden.aprobar(aprobadoPor, cargarUsuario(usuarioId));
        return orden;
    }

    @Transactional
    public OrdenTrabajo rechazar(Long id, String motivo, Long usuarioId) {
        OrdenTrabajo orden = obtener(id);
        orden.rechazar(motivo, cargarUsuario(usuarioId));
        log.info("Presupuesto rechazado en la orden {}", orden.codigoVisible());
        return orden;
    }

    /**
     * Entra en reparacion consumiendo el material necesario.
     *
     * <p>Aqui esta el cruce entre la maquina de estados y el inventario. Se
     * intenta servir cada linea de pieza; si alguna no se puede servir entera, la
     * OT NO entra en reparacion sino que queda en {@code ESPERANDO_PIEZAS}, con el
     * detalle de lo que falta anotado en el historial. En ningun caso se permite
     * que el stock quede negativo.
     *
     * <p>El material que si estaba disponible SE CONSUME igualmente: en un taller
     * se empieza por lo que se puede montar mientras llega el resto, y esas piezas
     * ya estan apartadas para esta moto.
     */
    @Transactional
    public ResultadoConsumo iniciarReparacion(Long id, Long usuarioId) {
        OrdenTrabajo orden = cargarConLineas(id);
        exigirPermisoDeTrabajo(orden);
        exigirEstado(orden, EstadoOT.APROBADA, EstadoOT.ESPERANDO_PIEZAS);
        return consumirMaterialYResolverEstado(orden, usuarioId);
    }

    /**
     * Reanuda una reparacion bloqueada, reintentando el material que faltaba.
     *
     * <p>Si sigue sin haber existencias, la OT se queda donde estaba y se explica
     * que falta todavia.
     */
    @Transactional
    public ResultadoConsumo reanudarReparacion(Long id, Long usuarioId) {
        OrdenTrabajo orden = cargarConLineas(id);
        exigirPermisoDeTrabajo(orden);
        exigirEstado(orden, EstadoOT.ESPERANDO_PIEZAS);
        return consumirMaterialYResolverEstado(orden, usuarioId);
    }

    /** Bloqueo manual por falta de material, sin pasar por el consumo automatico. */
    @Transactional
    public OrdenTrabajo bloquearPorFaltaDePiezas(Long id, String motivo, Long usuarioId) {
        OrdenTrabajo orden = obtener(id);
        exigirPermisoDeTrabajo(orden);
        orden.bloquearPorFaltaDePiezas(motivo, cargarUsuario(usuarioId));
        return orden;
    }

    @Transactional
    public OrdenTrabajo marcarLista(Long id, Long usuarioId) {
        OrdenTrabajo orden = obtener(id);
        exigirPermisoDeTrabajo(orden);
        orden.marcarLista(cargarUsuario(usuarioId));
        log.info("Orden {} lista para entregar", orden.codigoVisible());
        return orden;
    }

    /**
     * Entrega al cliente.
     *
     * <p>A partir de este momento la OT queda congelada: ni la maquina de estados
     * ni los triggers de la base de datos admiten mas cambios sobre ella ni sobre
     * sus lineas.
     */
    @Transactional
    public OrdenTrabajo entregar(Long id, Long usuarioId) {
        OrdenTrabajo orden = obtener(id);
        orden.entregar(cargarUsuario(usuarioId));
        log.info("Orden {} entregada", orden.codigoVisible());
        return orden;
    }

    // ==================================================================
    // Lineas
    // ==================================================================

    @Transactional
    public LineaOT anadirManoDeObra(Long ordenId, String descripcion, BigDecimal horas,
                                    BigDecimal descuentoPct, String codigoTipoIva) {
        OrdenTrabajo orden = cargarConLineas(ordenId);
        exigirPermisoDeTrabajo(orden);
        TipoIva tipoIva = cargarTipoIva(codigoTipoIva);
        return orden.anadirManoDeObra(descripcion, horas, descuentoPct, tipoIva.getCodigo(),
                tipoIva.getPorcentaje());
    }

    /**
     * Anade una pieza al presupuesto.
     *
     * <p>Anadirla NO saca material del almacen: eso ocurre al entrar en
     * reparacion. Lo que si hace es congelar el precio de catalogo en la linea.
     */
    @Transactional
    public LineaOT anadirPieza(Long ordenId, Long piezaId, BigDecimal cantidad, BigDecimal descuentoPct) {
        OrdenTrabajo orden = cargarConLineas(ordenId);
        exigirPermisoDeTrabajo(orden);
        Pieza pieza = piezaService.obtener(piezaId);
        TipoIva tipoIva = cargarTipoIva(pieza.getTipoIva());
        return orden.anadirPieza(pieza, cantidad, descuentoPct, tipoIva.getPorcentaje());
    }

    @Transactional
    public LineaOT cambiarCantidadDeLinea(Long ordenId, Long lineaId, BigDecimal cantidad) {
        OrdenTrabajo orden = cargarConLineas(ordenId);
        exigirPermisoDeTrabajo(orden);
        LineaOT linea = buscarLinea(orden, lineaId);
        linea.cambiarCantidad(cantidad, consumoDe(linea));
        return linea;
    }

    /**
     * Quita una linea del presupuesto.
     *
     * <p>Solo si no ha sacado material del almacen. Si ya lo ha hecho, hay que
     * devolverlo primero: el libro de movimientos es inmutable y no se puede
     * borrar el rastro de unas piezas que salieron fisicamente.
     */
    @Transactional
    public void quitarLinea(Long ordenId, Long lineaId) {
        OrdenTrabajo orden = cargarConLineas(ordenId);
        exigirPermisoDeTrabajo(orden);
        LineaOT linea = buscarLinea(orden, lineaId);

        BigDecimal consumido = consumoDe(linea);
        if (consumido.signum() > 0) {
            throw new ConflictoException(
                    ("La linea %d de la orden %s ya ha consumido %s unidades de %s. Devuelvalas al almacen "
                     + "antes de quitar la linea.").formatted(linea.getNumeroLinea(), orden.codigoVisible(),
                            consumido.toPlainString(), linea.skuPieza()));
        }
        orden.quitarLinea(linea);
    }

    /** Devuelve al almacen piezas ya consumidas por una linea. */
    @Transactional
    public void devolverPiezaDeLinea(Long ordenId, Long lineaId, BigDecimal cantidad, String motivo,
                                     Long usuarioId) {
        OrdenTrabajo orden = cargarConLineas(ordenId);
        exigirPermisoDeTrabajo(orden);
        LineaOT linea = buscarLinea(orden, lineaId);

        if (!linea.esDePieza()) {
            throw new ReglaNegocioException(
                    "La linea %d no es de tipo PIEZA: no hay nada que devolver.".formatted(linea.getNumeroLinea()));
        }
        BigDecimal consumido = consumoDe(linea);
        if (cantidad == null || cantidad.signum() <= 0 || cantidad.compareTo(consumido) > 0) {
            throw new ReglaNegocioException(
                    "Solo se pueden devolver entre 0 y %s unidades de la linea %d."
                            .formatted(consumido.toPlainString(), linea.getNumeroLinea()));
        }

        inventarioService.registrarDevolucion(linea.getPieza().getId(), cantidad, orden, linea,
                motivo != null ? motivo : "Devolucion desde la orden " + orden.codigoVisible(), usuarioId);
    }

    // ==================================================================

    /**
     * Intenta servir todas las lineas de pieza y decide el estado resultante.
     */
    private ResultadoConsumo consumirMaterialYResolverEstado(OrdenTrabajo orden, Long usuarioId) {
        Usuario usuario = cargarUsuario(usuarioId);

        // La orden entra en reparacion ANTES de tocar el almacen. No es un detalle:
        // la maquina de estados no permite saltar de APROBADA a ESPERANDO_PIEZAS, y
        // con razon. Lo que ocurre de verdad es que el trabajo empieza y, si falta
        // material, se bloquea; el historial refleja las dos cosas por separado en
        // vez de fingir que nunca se intento.
        if (orden.getEstado() == EstadoOT.APROBADA) {
            orden.entrarEnReparacion(usuario, null);
        }

        List<PiezaFaltante> faltantes = new ArrayList<>();
        int servidas = 0;

        for (LineaOT linea : orden.lineasDePiezas()) {
            BigDecimal pendiente = linea.getCantidad().subtract(consumoDe(linea));
            if (pendiente.signum() <= 0) {
                continue;   // ya se sirvio en un intento anterior
            }

            Pieza pieza = linea.getPieza();

            // Se usa la variante que NO lanza excepcion: una excepcion escapando
            // de un metodo transaccional marcaria toda la transaccion como
            // rollback-only y no podriamos ni servir el resto de lineas ni
            // registrar el cambio de estado, aunque la capturasemos aqui.
            IntentoConsumo intento = inventarioService.intentarConsumoEnOrden(
                    pieza.getId(), pendiente, orden, linea, usuarioId);

            if (intento.servido()) {
                servidas++;
            } else {
                faltantes.add(new PiezaFaltante(pieza.getId(), pieza.getSku(), pieza.getDescripcion(),
                        linea.getCantidad(), intento.disponible(), intento.faltan()));
            }
        }

        if (faltantes.isEmpty()) {
            // Al reanudar, la orden venia bloqueada y ahora si puede continuar.
            if (orden.getEstado() == EstadoOT.ESPERANDO_PIEZAS) {
                orden.entrarEnReparacion(usuario, "Recibido el material pendiente");
            }
            log.info("Orden {} en reparacion: servidas {} lineas de material",
                    orden.codigoVisible(), servidas);
            return new ResultadoConsumo(EstadoOT.EN_REPARACION, servidas, List.of());
        }

        ResultadoConsumo resultado = new ResultadoConsumo(EstadoOT.ESPERANDO_PIEZAS, servidas, faltantes);

        // Si ya estaba bloqueada, sigue bloqueada: no hay transicion que registrar,
        // pero tampoco se puede avanzar.
        if (orden.getEstado() != EstadoOT.ESPERANDO_PIEZAS) {
            orden.bloquearPorFaltaDePiezas(resultado.descripcionDeFaltantes(), usuario);
        }

        log.info("Orden {} en espera de piezas: {}", orden.codigoVisible(),
                resultado.descripcionDeFaltantes());
        return resultado;
    }

    /**
     * Consume el siguiente numero del ejercicio con la fila del contador
     * bloqueada, creando el contador la primera vez que se abre una OT del ano.
     */
    private int siguienteNumero(int ejercicio) {
        ContadorOt contador = contadorRepository.bloquearEjercicio(ejercicio)
                .orElseGet(() -> contadorRepository.saveAndFlush(ContadorOt.para(ejercicio)));
        return contador.consumirSiguiente();
    }

    private BigDecimal tarifaHoraVigente() {
        return configuracionRepository.findById(ConfiguracionTaller.ID_UNICO)
                .map(ConfiguracionTaller::getTarifaHoraDefecto)
                .orElseThrow(() -> new ConflictoException(
                        "No hay configuracion del taller: defina la tarifa por hora antes de abrir ordenes."));
    }

    /** Unidades que la linea tiene ahora mismo sacadas del almacen. */
    private BigDecimal consumoDe(LineaOT linea) {
        if (linea.getId() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal consumo = movimientoRepository.consumoNetoDeLinea(linea.getId());
        return consumo == null ? BigDecimal.ZERO : consumo;
    }

    private OrdenTrabajo cargarConLineas(Long id) {
        OrdenTrabajo orden = obtener(id);
        // Fuerza la inicializacion de la coleccion dentro de la transaccion.
        orden.getLineas().size();
        return orden;
    }

    private LineaOT buscarLinea(OrdenTrabajo orden, Long lineaId) {
        return orden.buscarLinea(lineaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La linea %d no pertenece a la orden %s.".formatted(lineaId, orden.codigoVisible())));
    }

    private void exigirEstado(OrdenTrabajo orden, EstadoOT... admitidos) {
        for (EstadoOT admitido : admitidos) {
            if (orden.getEstado() == admitido) {
                return;
            }
        }
        throw new ConflictoException(
                "La orden %s esta en estado %s y esta operacion requiere que este en %s."
                        .formatted(orden.codigoVisible(), orden.getEstado().name(),
                                String.join(" o ", java.util.Arrays.stream(admitidos).map(Enum::name).toList())));
    }

    private TipoIva cargarTipoIva(String codigo) {
        String codigoFinal = codigo == null || codigo.isBlank() ? "GENERAL" : codigo;
        return tipoIvaRepository.findById(codigoFinal)
                .orElseThrow(() -> new ConflictoException(
                        "El tipo de IVA '%s' no existe en el catalogo.".formatted(codigoFinal)));
    }

    private Usuario cargarUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return null;   // hasta la fase 5 no hay usuario autenticado
        }
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el usuario", usuarioId));
    }
}
