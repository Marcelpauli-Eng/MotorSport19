package com.motorsport19.taller.factura.service;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.configuracion.repository.ConfiguracionTallerRepository;
import com.motorsport19.taller.factura.domain.ContadorRegistroFacturacion;
import com.motorsport19.taller.factura.domain.DatosEmision;
import com.motorsport19.taller.factura.domain.DatosFiscales;
import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.LineaAFacturar;
import com.motorsport19.taller.factura.domain.SerieFactura;
import com.motorsport19.taller.factura.domain.TipoEventoFactura;
import com.motorsport19.taller.factura.domain.TipoFactura;
import com.motorsport19.taller.factura.domain.TipoRectificativa;
import com.motorsport19.taller.factura.repository.ContadorRegistroFacturacionRepository;
import com.motorsport19.taller.factura.repository.FacturaRepository;
import com.motorsport19.taller.factura.repository.SerieFacturaRepository;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.service.OrdenTrabajoService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Emision de facturas.
 *
 * <p>La emision es la operacion mas delicada del sistema porque produce un
 * documento que ya no se podra tocar. Todo ocurre en una unica transaccion y en
 * este orden:
 *
 * <ol>
 *   <li>Se bloquea el contador global del registro. Eso serializa TODAS las
 *       emisiones: la cadena de huellas es una sola y dos facturas no pueden
 *       encadenar a la vez con la misma huella anterior.</li>
 *   <li>Se bloquea la serie y se toma su siguiente numero.</li>
 *   <li>Se copian los datos fiscales, la moto y las lineas: instantaneas, no
 *       referencias.</li>
 *   <li>Se calculan totales y desglose sumando lineas.</li>
 *   <li>Se sella la factura encadenandola con la huella de la anterior.</li>
 * </ol>
 *
 * <p>Los triggers de la base de datos vuelven a comprobar todo esto en el INSERT
 * y al hacer commit. Si algo no cuadra, la transaccion falla en vez de guardar
 * una factura invalida.
 */
@Service
public class FacturacionService {

    private static final Logger log = LoggerFactory.getLogger(FacturacionService.class);

    private final FacturaRepository facturaRepository;
    private final SerieFacturaRepository serieRepository;
    private final ContadorRegistroFacturacionRepository contadorRepository;
    private final ConfiguracionTallerRepository configuracionRepository;
    private final OrdenTrabajoService ordenService;
    private final RegistroEventosService registroEventos;
    private final EntityManager entityManager;

    public FacturacionService(FacturaRepository facturaRepository,
                              SerieFacturaRepository serieRepository,
                              ContadorRegistroFacturacionRepository contadorRepository,
                              ConfiguracionTallerRepository configuracionRepository,
                              OrdenTrabajoService ordenService,
                              RegistroEventosService registroEventos,
                              EntityManager entityManager) {
        this.facturaRepository = facturaRepository;
        this.serieRepository = serieRepository;
        this.contadorRepository = contadorRepository;
        this.configuracionRepository = configuracionRepository;
        this.ordenService = ordenService;
        this.registroEventos = registroEventos;
        this.entityManager = entityManager;
    }

    // ==================================================================
    // Consultas
    // ==================================================================

    @Transactional(readOnly = true)
    public Factura obtener(Long id) {
        return conDetalleCargado(facturaRepository.buscarConDetalle(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la factura", id)));
    }

    @Transactional(readOnly = true)
    public Factura obtenerPorNumero(String numeroCompleto) {
        return conDetalleCargado(facturaRepository.buscarPorNumeroCompleto(numeroCompleto)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la factura %s.".formatted(numeroCompleto))));
    }

    /**
     * Fuerza la carga de lineas y desglose antes de salir de la transaccion.
     *
     * <p>Con {@code open-in-view} desactivado, la sesion se cierra al volver del
     * servicio; si las colecciones siguieran perezosas, el mapeo a DTO fallaria.
     * No se traen con JOIN FETCH en la consulta porque cargar dos colecciones a
     * la vez multiplicaria las filas del resultado.
     */
    private Factura conDetalleCargado(Factura factura) {
        factura.getLineas().size();
        factura.getDesgloseIva().size();
        return factura;
    }

    @Transactional(readOnly = true)
    public Page<Factura> buscar(TipoFactura tipo, LocalDate desde, LocalDate hasta, Long receptorId,
                                Boolean conIva, Pageable pageable) {
        return facturaRepository.buscar(tipo, desde, hasta, receptorId, conIva, pageable);
    }

    @Transactional(readOnly = true)
    public List<Factura> rectificativasDe(Long facturaId) {
        return facturaRepository.buscarRectificativasDe(facturaId);
    }

    @Transactional(readOnly = true)
    public List<SerieFactura> seriesActivas() {
        return serieRepository.findByActivaTrueOrderByEjercicioDescCodigoAsc();
    }

    /** Todas, incluidas las cerradas: es lo que se mantiene desde Ajustes. */
    @Transactional(readOnly = true)
    public List<SerieFactura> todasLasSeries() {
        return serieRepository.findAllByOrderByEjercicioDescCodigoAsc();
    }

    /**
     * Abre una serie de facturacion.
     *
     * <p>Sin ninguna serie no se puede emitir una sola factura, y hasta ahora la
     * unica forma de tenerlas era el juego de datos de demostracion: una
     * instalacion de verdad se quedaba sin poder facturar. Se crean desde
     * Ajustes.
     */
    @Transactional
    public SerieFactura crearSerie(String codigo, Integer ejercicio, String descripcion,
                                   TipoFactura tipo, boolean simplificada) {
        SerieFactura serie = SerieFactura.crear(codigo, ejercicio, descripcion, tipo, simplificada);

        // La base de datos tiene su UNIQUE, pero el mensaje que suelta no dice
        // nada a quien esta rellenando el formulario.
        serieRepository.buscarPorCodigoYEjercicio(serie.getCodigo(), serie.getEjercicio())
                .ifPresent(existente -> {
                    throw new ConflictoException(
                            "Ya existe la serie %s del ejercicio %d."
                                    .formatted(serie.getCodigo(), serie.getEjercicio()));
                });

        SerieFactura guardada = serieRepository.save(serie);
        log.info("Abierta la serie de facturacion {}/{} ({})",
                guardada.getCodigo(), guardada.getEjercicio(), guardada.getTipo());
        return guardada;
    }

    /**
     * Cambia la descripcion de una serie y la abre o la cierra.
     *
     * <p>El codigo, el ejercicio y el tipo no se pueden tocar: van impresos en el
     * numero de cada factura ya emitida.
     */
    @Transactional
    public SerieFactura actualizarSerie(Long id, String descripcion, boolean activa) {
        SerieFactura serie = serieRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la serie de facturacion", id));

        serie.renombrar(descripcion);
        if (activa) {
            serie.activar();
        } else {
            serie.desactivar();
        }
        return serie;
    }

    // ==================================================================
    // Emision
    // ==================================================================

    /**
     * Emite la factura de una orden de trabajo.
     *
     * @param ordenId  orden a facturar; debe estar LISTA o ENTREGADA
     * @param serieId  serie de facturacion, que debe ser de tipo ORDINARIA
     */
    @Transactional
    public Factura emitirDesdeOrden(Long ordenId, Long serieId, LocalDate fechaEmision, Long usuarioId) {
        OrdenTrabajo orden = ordenService.obtener(ordenId);

        if (!orden.puedeFacturarse()) {
            throw new ConflictoException(
                    ("La orden %s esta en estado %s. Solo se factura una orden LISTA o ENTREGADA.")
                            .formatted(orden.codigoVisible(), orden.getEstado().name()));
        }
        if (!facturaRepository.buscarOrdinariasDeOrden(ordenId).isEmpty()) {
            Factura existente = facturaRepository.buscarOrdinariasDeOrden(ordenId).get(0);
            throw new ConflictoException(
                    ("La orden %s ya tiene la factura %s. Para corregirla emita una rectificativa; "
                     + "una factura no se reemite.").formatted(orden.codigoVisible(),
                            existente.numeroVisible()));
        }

        Cliente cliente = orden.getCliente();

        List<LineaOT> lineasOrden = ordenService.lineasDe(ordenId);
        if (lineasOrden.isEmpty()) {
            throw new ReglaNegocioException(
                    "La orden %s no tiene lineas: no hay nada que facturar.".formatted(orden.codigoVisible()));
        }
        List<LineaAFacturar> lineas = lineasOrden.stream().map(LineaAFacturar::copiaDe).toList();

        boolean simplificada = decidirSimplificada(cliente, lineas);

        Factura factura = emitir(serieId, TipoFactura.ORDINARIA, orden, null, null, null,
                cliente, fechaEmision, lineas, simplificada, usuarioId);

        registroEventos.anotar(factura, TipoEventoFactura.EMISION, usuarioId,
                "Emision de la factura %s desde la orden %s"
                        .formatted(factura.numeroVisible(), orden.codigoVisible()),
                """
                {"origen":"%s","importe":%s,"lineas":%d}"""
                        .formatted(orden.codigoVisible(), factura.getTotal().toPlainString(), lineas.size()),
                null);

        log.info("Emitida la factura {} por {} EUR desde la orden {}",
                factura.numeroVisible(), factura.getTotal(), orden.codigoVisible());
        return factura;
    }

    /**
     * Emite una factura rectificativa sobre otra ya emitida.
     *
     * <p>Es el UNICO mecanismo para corregir una factura: la original no se toca
     * ni se anula, se queda donde esta y la rectificativa la referencia.
     *
     * @param tipoRectificativa POR_SUSTITUCION si las lineas sustituyen a las
     *                          originales; POR_DIFERENCIAS si recogen solo el ajuste
     * @param lineas            lineas corregidas. Si van vacias en una rectificativa
     *                          por diferencias, se genera el negativo exacto de la
     *                          original (anulacion completa)
     */
    @Transactional
    public Factura emitirRectificativa(Long facturaOriginalId, Long serieId,
                                       TipoRectificativa tipoRectificativa, String motivo,
                                       List<LineaAFacturar> lineas, LocalDate fechaEmision,
                                       Long usuarioId) {
        Factura original = obtener(facturaOriginalId);

        if (original.esRectificativa()) {
            throw new ConflictoException(
                    ("La factura %s ya es una rectificativa. Rectifique la factura ordinaria original.")
                            .formatted(original.numeroVisible()));
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("Una rectificativa debe explicar el motivo de la correccion.");
        }

        List<LineaAFacturar> lineasFinales = lineas;
        if (lineasFinales == null || lineasFinales.isEmpty()) {
            if (tipoRectificativa != TipoRectificativa.POR_DIFERENCIAS) {
                throw new ReglaNegocioException(
                        "Una rectificativa por sustitucion debe indicar las lineas corregidas.");
            }
            // Anulacion completa: el negativo exacto de la original.
            lineasFinales = original.getLineas().stream()
                    .map(l -> new LineaAFacturar(l.getTipo(), l.getDescripcion(), l.getPiezaSku(),
                            l.getCantidad().negate(), l.getPrecioUnitario(), l.getDescuentoPct(),
                            l.getTipoIva(), l.getPorcentajeIva()))
                    .toList();
        }

        // Una rectificativa corrige a su original: si aquella iba sin datos del
        // cliente, esta tampoco los lleva.
        Factura rectificativa = emitir(serieId, TipoFactura.RECTIFICATIVA, original.getOrdenTrabajo(),
                original, tipoRectificativa, motivo, original.getReceptor(), fechaEmision,
                lineasFinales, original.isSimplificada(), usuarioId);

        registroEventos.anotar(rectificativa, TipoEventoFactura.RECTIFICACION, usuarioId,
                "Emision de la rectificativa %s que corrige a %s"
                        .formatted(rectificativa.numeroVisible(), original.numeroVisible()),
                """
                {"rectifica":"%s","tipo":"%s","motivo":"%s"}"""
                        .formatted(original.numeroVisible(), tipoRectificativa,
                                motivo.replace("\"", "'")),
                null);

        log.info("Emitida la rectificativa {} sobre {}",
                rectificativa.numeroVisible(), original.numeroVisible());
        return rectificativa;
    }

    // ==================================================================
    // Verificacion de la cadena
    // ==================================================================

    /**
     * Recorre el registro completo comprobando que la cadena de huellas es
     * coherente.
     *
     * <p>Comprueba cuatro cosas en cada factura: que su huella corresponde a su
     * contenido, que enlaza con la huella de la anterior, que no falta ninguna
     * posicion del registro, y que los totales cuadran con las lineas.
     *
     * <p>Un informe sin anomalias significa que ninguna factura se ha alterado ni
     * eliminado desde que se emitio.
     */
    @Transactional
    public InformeVerificacion verificarCadena(Long usuarioId) {
        List<AnomaliaCadena> anomalias = new java.util.ArrayList<>();
        long verificadas = 0;
        long posicionEsperada = 1;
        String huellaEsperada = Factura.HUELLA_GENESIS;
        String primeraHuella = null;
        String ultimaHuella = null;

        // El stream se cierra siempre: en libros grandes esto no cabe en memoria.
        try (var cadena = facturaRepository.recorrerCadena()) {
            for (Factura factura : (Iterable<Factura>) cadena::iterator) {
                verificadas++;
                if (primeraHuella == null) {
                    primeraHuella = factura.getHuella();
                }
                ultimaHuella = factura.getHuella();

                if (factura.getNumeroRegistro() != posicionEsperada) {
                    anomalias.add(new AnomaliaCadena(factura.getNumeroRegistro(),
                            factura.getNumeroCompleto(), AnomaliaCadena.Tipo.HUECO_EN_EL_REGISTRO,
                            "Se esperaba la posicion %d y se ha encontrado la %d: falta alguna factura."
                                    .formatted(posicionEsperada, factura.getNumeroRegistro())));
                }

                if (!factura.huellaEsCoherente()) {
                    anomalias.add(new AnomaliaCadena(factura.getNumeroRegistro(),
                            factura.getNumeroCompleto(), AnomaliaCadena.Tipo.HUELLA_ALTERADA,
                            "La huella guardada no corresponde a la cadena canonica sellada."));
                }

                // Comprobacion clave: los valores de la fila deben seguir siendo
                // los que se pasaron por SHA-256. Sin esto, alguien podria cambiar
                // el importe dejando intacta la cadena canonica y la huella
                // seguiria cuadrando consigo misma.
                if (!factura.contenidoCoincideConElSello()) {
                    anomalias.add(new AnomaliaCadena(factura.getNumeroRegistro(),
                            factura.getNumeroCompleto(), AnomaliaCadena.Tipo.CONTENIDO_ALTERADO,
                            "Los datos actuales no coinciden con los sellados. Sellado: [%s]. Ahora: [%s]."
                                    .formatted(factura.getCadenaHuella(),
                                            com.motorsport19.taller.factura.domain.CalculadoraHuella
                                                    .cadenaCanonica(factura.getEmisor().getNif(),
                                                            factura.getNumeroCompleto(),
                                                            factura.getFechaEmision(), factura.getTipo(),
                                                            factura.getTotalIva(), factura.getTotal(),
                                                            factura.getHuellaAnterior(),
                                                            factura.getTimestampEmision()))));
                }

                if (!factura.enlazaCon(huellaEsperada)) {
                    anomalias.add(new AnomaliaCadena(factura.getNumeroRegistro(),
                            factura.getNumeroCompleto(), AnomaliaCadena.Tipo.CADENA_ROTA,
                            "Declara la huella anterior %s cuando la de la factura precedente es %s."
                                    .formatted(recorta(factura.getHuellaAnterior()),
                                            recorta(huellaEsperada))));
                }

                comprobarTotales(factura).ifPresent(anomalias::add);

                huellaEsperada = factura.getHuella();
                posicionEsperada = factura.getNumeroRegistro() + 1;
                entityManager.detach(factura);
            }
        }

        InformeVerificacion informe = new InformeVerificacion(
                Instant.now(), verificadas, List.copyOf(anomalias), primeraHuella, ultimaHuella);

        registroEventos.anotar(null, TipoEventoFactura.VERIFICACION_CADENA, usuarioId,
                informe.resumen(),
                """
                {"facturas_verificadas":%d,"anomalias":%d,"ultima_huella":"%s"}"""
                        .formatted(verificadas, anomalias.size(),
                                ultimaHuella == null ? "" : ultimaHuella),
                null);

        if (!informe.integra()) {
            log.error("VERIFICACION DE CADENA CON ANOMALIAS: {}", informe.resumen());
        }
        return informe;
    }

    // ==================================================================

    /**
     * Decide si la factura puede ir sin los datos del cliente.
     *
     * <p>Con la ficha fiscal completa se emite factura completa y no hay mas que
     * hablar: es lo que sirve para todo.
     *
     * <p>Sin ella se puede emitir una simplificada, pero solo por debajo del
     * limite configurado. Por encima <b>hay que</b> emitir factura completa
     * aunque el cliente diga que no la quiere: la obligacion nace del importe de
     * la operacion, no de que la pida, y el cliente esta obligado a dar sus
     * datos. Por eso ahi se corta y se explica, en vez de partir el trabajo en
     * dos facturas pequeñas, que es justo lo que no se puede hacer.
     */
    private boolean decidirSimplificada(Cliente cliente, List<LineaAFacturar> lineas) {
        if (cliente.tieneDatosFiscalesCompletos()) {
            return false;
        }

        // Las facturas sin IVA se emiten sin tope y sin datos del cliente, por
        // decision expresa del taller.
        //
        // Queda escrito aqui porque va en contra de lo que suele exigirse: en una
        // operacion exenta hay que poder justificar POR QUE no lleva IVA, y eso
        // normalmente pasa por identificar al destinatario —el NIF-IVA europeo en
        // una entrega intracomunitaria, por ejemplo—. Si algun dia estas facturas
        // dejan de ser pruebas, esto es lo primero que hay que repasar con la
        // gestoria.
        if (sinIva(lineas)) {
            return true;
        }

        // Se suman los importes ya calculados de cada linea: la misma cuenta que
        // hara la factura, para que el limite se compare contra lo que de verdad
        // se va a cobrar.
        BigDecimal total = lineas.stream()
                .map(l -> l.importes().total())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal limite = configuracionRepository.findById(ConfiguracionTaller.ID_UNICO)
                .map(ConfiguracionTaller::getLimiteFacturaSimplificada)
                .orElse(BigDecimal.ZERO);

        if (total.compareTo(limite) > 0) {
            throw new ReglaNegocioException(
                    ("No se puede facturar a '%s': son %s € y el maximo de una factura simplificada "
                     + "son %s €. Por encima de ese importe hay que emitir factura completa, aunque el "
                     + "cliente no la quiera, asi que necesita su documento y su domicilio en la ficha.")
                            .formatted(cliente.nombreCompleto(), total.toPlainString(),
                                    limite.toPlainString()));
        }
        return true;
    }

    /** Ninguna linea repercute IVA: la factura entera va al 0 %. */
    private boolean sinIva(List<LineaAFacturar> lineas) {
        return lineas.stream().allMatch(l -> l.importes().cuotaIva().signum() == 0);
    }

    /**
     * Nucleo de la emision, comun a ordinarias y rectificativas.
     */
    private Factura emitir(Long serieId, TipoFactura tipo, OrdenTrabajo orden, Factura rectificada,
                           TipoRectificativa tipoRectificativa, String motivo, Cliente receptor,
                           LocalDate fechaEmision, List<LineaAFacturar> lineas, boolean simplificada,
                           Long usuarioId) {

        // 1. Bloqueo global: serializa TODA la facturacion. La cadena es unica.
        ContadorRegistroFacturacion contador = contadorRepository.bloquear()
                .orElseThrow(() -> new IllegalStateException(
                        "Falta la fila del contador del registro de facturacion."));

        // 2. Bloqueo de la serie y siguiente numero.
        SerieFactura serie = serieRepository.bloquear(serieId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la serie de facturacion", serieId));
        if (!serie.isActiva()) {
            throw new ConflictoException(
                    "La serie %s/%d esta inactiva.".formatted(serie.getCodigo(), serie.getEjercicio()));
        }
        if (serie.getTipo() != tipo) {
            throw new ConflictoException(
                    "La serie %s es de tipo %s: no admite facturas %s."
                            .formatted(serie.getCodigo(), serie.getTipo(), tipo));
        }
        // Las simplificadas van en su propia serie y las completas en la suya. Es
        // lo que deja el libro ordenado y lo que espera la gestoria.
        if (serie.isSimplificada() != simplificada) {
            throw new ConflictoException(simplificada
                    ? ("Esta factura va sin los datos del cliente y la serie %s no es de facturas "
                       + "simplificadas. Abra una serie para simplificadas en Ajustes y emitala ahi.")
                            .formatted(serie.getCodigo())
                    : ("La serie %s es de facturas simplificadas: no admite una factura completa."
                            .formatted(serie.getCodigo())));
        }

        exigirContadoresCuadrados(serie, contador);

        int numero = serie.getUltimoNumero() + 1;
        long numeroRegistro = contador.siguientePosicion();

        // 3. Huella con la que hay que encadenar.
        String huellaAnterior = facturaRepository.buscarUltimaDeLaCadena()
                .map(Factura::getHuella)
                .orElse(Factura.HUELLA_GENESIS);

        ConfiguracionTaller config = configuracionRepository.findById(ConfiguracionTaller.ID_UNICO)
                .orElseThrow(() -> new ConflictoException(
                        "Faltan los datos fiscales del taller: rellenelos en Ajustes > Empresa y "
                        + "facturacion antes de emitir ninguna factura."));

        DatosEmision datos = new DatosEmision(
                serie, numero, numeroRegistro, tipo,
                orden, rectificada, tipoRectificativa, motivo,
                fechaEmision != null ? fechaEmision : LocalDate.now(),
                fechaOperacion(orden, fechaEmision),
                Instant.now(),
                datosFiscalesDe(config),
                receptor,
                simplificada ? soloNombreDe(receptor) : datosFiscalesDe(receptor),
                orden == null ? null : orden.getMoto().getMatricula(),
                orden == null ? null : orden.getMoto().descripcion(),
                orden == null ? null : orden.codigoVisible(),
                config.getSoftwareNombre(), config.getSoftwareVersion(), config.getSoftwareNif(),
                config.getUrlVerificacionQr(),
                simplificada,
                usuarioId);

        Factura factura = Factura.emitir(datos, lineas, huellaAnterior);

        // El flush dispara los triggers: numeracion correlativa, posicion del
        // registro y enlace de la huella se validan AQUI, no al final.
        Factura guardada = facturaRepository.saveAndFlush(factura);

        // Los contadores los actualiza el trigger; se releen para que las
        // entidades en memoria no queden con el valor viejo.
        entityManager.refresh(serie);
        entityManager.refresh(contador);
        // El refresh trae de la base de datos lo que ella genero (numero completo,
        // importes de linea), pero a cambio deja las colecciones como proxies
        // perezosos: hay que volver a cargarlas antes de cerrar la sesion.
        entityManager.refresh(guardada);

        return conDetalleCargado(guardada);
    }

    /**
     * Comprueba que los contadores coinciden con las facturas que hay.
     *
     * <p>Los contadores solo avanzan dentro de la transaccion que emite, asi
     * que en condiciones normales van siempre a la par del registro. Si se
     * separan es porque alguien ha borrado facturas por debajo de la
     * aplicacion, saltandose los triggers de inmutabilidad.
     *
     * <p>Sin esta comprobacion el intento sigue adelante y lo corta el trigger
     * de la base con un «Numeracion no correlativa en la serie A: se esperaba el
     * numero 5, llego 6», que es exacto pero no le dice a nadie que ha pasado ni
     * como salir. Se para antes y se explica.
     *
     * <p>Lo que NO se hace es recolocar el contador solo y seguir emitiendo: en
     * un libro de facturas, que falte una factura tiene que enterarse alguien.
     */
    private void exigirContadoresCuadrados(SerieFactura serie, ContadorRegistroFacturacion contador) {
        int emitidoEnSerie = facturaRepository.ultimoNumeroEmitidoEn(serie.getId());
        if (emitidoEnSerie != serie.getUltimoNumero()) {
            throw new ConflictoException(
                    ("El registro de la serie %s no cuadra: el contador dice que la ultima factura fue "
                     + "la %d, pero la ultima que hay es la %d. Falta alguna factura del registro, asi "
                     + "que no se puede emitir: emitir ahora dejaria un hueco en la numeracion. "
                     + "Compruebe si esa factura llego a entregarse a un cliente; si nunca existio, "
                     + "hay que devolver el contador de la serie a %d.")
                            .formatted(serie.getCodigo(), serie.getUltimoNumero(), emitidoEnSerie,
                                    emitidoEnSerie));
        }

        long ocupada = facturaRepository.ultimaPosicionOcupada();
        if (ocupada != contador.getUltimoNumero()) {
            throw new ConflictoException(
                    ("El registro de facturacion no cuadra: el contador global va por la posicion %d y "
                     + "la ultima ocupada es la %d. Falta alguna factura del registro; no se puede "
                     + "emitir hasta que cuadre.")
                            .formatted(contador.getUltimoNumero(), ocupada));
        }
    }

    private java.util.Optional<AnomaliaCadena> comprobarTotales(Factura factura) {
        var suma = factura.getLineas().stream()
                .map(l -> l.importes())
                .reduce(com.motorsport19.taller.factura.domain.ImporteLinea.cero(),
                        com.motorsport19.taller.factura.domain.ImporteLinea::mas);

        boolean cuadra = suma.baseImponible().compareTo(factura.getBaseImponible()) == 0
                && suma.cuotaIva().compareTo(factura.getTotalIva()) == 0
                && suma.total().compareTo(factura.getTotal()) == 0;

        if (cuadra) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new AnomaliaCadena(factura.getNumeroRegistro(),
                factura.getNumeroCompleto(), AnomaliaCadena.Tipo.TOTALES_DESCUADRADOS,
                "Cabecera: base %s, IVA %s, total %s. Suma de lineas: base %s, IVA %s, total %s."
                        .formatted(factura.getBaseImponible(), factura.getTotalIva(), factura.getTotal(),
                                suma.baseImponible(), suma.cuotaIva(), suma.total())));
    }

    /** Fecha en que se presto el servicio: la salida de la moto, si la hubo. */
    private LocalDate fechaOperacion(OrdenTrabajo orden, LocalDate fechaEmision) {
        LocalDate emision = fechaEmision != null ? fechaEmision : LocalDate.now();
        if (orden == null || orden.getFechaRealSalida() == null) {
            return emision;
        }
        return orden.getFechaRealSalida()
                .atZone(com.motorsport19.taller.factura.domain.CalculadoraHuella.ZONA)
                .toLocalDate();
    }

    private DatosFiscales datosFiscalesDe(ConfiguracionTaller config) {
        return new DatosFiscales(config.getRazonSocial(), config.getNif(), config.getDireccion(),
                config.getCodigoPostal(), config.getCiudad(), config.getProvincia(), config.getPais());
    }

    /**
     * Datos del receptor de una simplificada: solo el nombre.
     *
     * <p>La norma no obliga ni a eso, pero el taller siempre sabe de quien era
     * la moto, y una factura sin ningun nombre no hay forma de casarla despues
     * con su orden ni de contestar a un cliente que pregunta por ella.
     */
    private DatosFiscales soloNombreDe(Cliente cliente) {
        return new DatosFiscales(cliente.nombreCompleto(), null, null, null, null, null,
                cliente.getPais());
    }

    private DatosFiscales datosFiscalesDe(Cliente cliente) {
        if (!cliente.tieneDatosFiscalesCompletos()) {
            throw new ReglaNegocioException(
                    "No se puede facturar a '%s': faltan datos fiscales.".formatted(cliente.nombreCompleto()));
        }
        return new DatosFiscales(cliente.nombreCompleto(), cliente.getDocumento(), cliente.getDireccion(),
                cliente.getCodigoPostal(), cliente.getCiudad(), cliente.getProvincia(), cliente.getPais());
    }

    private static String recorta(String huella) {
        return huella == null ? "(vacia)" : huella.substring(0, Math.min(12, huella.length())) + "...";
    }
}
