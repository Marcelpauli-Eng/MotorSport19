package com.motorsport19.taller.solicitud.service;

import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.configuracion.domain.ReglaCobro;
import com.motorsport19.taller.configuracion.domain.TipoIva;
import com.motorsport19.taller.configuracion.repository.ConfiguracionTallerRepository;
import com.motorsport19.taller.configuracion.repository.ReglaCobroRepository;
import com.motorsport19.taller.configuracion.repository.TipoIvaRepository;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.inventario.service.PiezaService;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.domain.TipoLinea;
import com.motorsport19.taller.orden.service.CobroDePiezas;
import com.motorsport19.taller.orden.service.OrdenTrabajoService;
import com.motorsport19.taller.servicio.domain.LineaServicioTipo;
import com.motorsport19.taller.servicio.domain.ServicioTipo;
import com.motorsport19.taller.servicio.service.ServicioTipoService;
import com.motorsport19.taller.solicitud.domain.CanalPresupuesto;
import com.motorsport19.taller.solicitud.domain.EstadoSolicitud;
import com.motorsport19.taller.solicitud.domain.LineaPresupuestoWeb;
import com.motorsport19.taller.solicitud.domain.SolicitudWeb;
import com.motorsport19.taller.solicitud.repository.SolicitudWebRepository;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Presupuesto de una solicitud web: se monta como el de una orden (mano de
 * obra, material del almacen, pluses y tasas), se manda, y si el cliente lo
 * acepta se abre la orden con esas mismas lineas.
 *
 * <p>No es una orden porque la moto aun no ha entrado: abrir una orden exige
 * darla de alta con su bastidor, y eso se hace cuando el cliente dice que si.
 */
@Service
public class PresupuestoWebService {

    private static final Logger log = LoggerFactory.getLogger(PresupuestoWebService.class);

    private final SolicitudWebRepository solicitudes;
    private final PiezaService piezas;
    private final ServicioTipoService serviciosTipo;
    private final ReglaCobroRepository reglas;
    private final TipoIvaRepository tiposIva;
    private final ConfiguracionTallerRepository configuracion;
    private final OrdenTrabajoService ordenes;
    private final UsuarioRepository usuarios;

    public PresupuestoWebService(SolicitudWebRepository solicitudes, PiezaService piezas,
                                 ServicioTipoService serviciosTipo, ReglaCobroRepository reglas,
                                 TipoIvaRepository tiposIva, ConfiguracionTallerRepository configuracion,
                                 OrdenTrabajoService ordenes, UsuarioRepository usuarios) {
        this.solicitudes = solicitudes;
        this.piezas = piezas;
        this.serviciosTipo = serviciosTipo;
        this.reglas = reglas;
        this.tiposIva = tiposIva;
        this.configuracion = configuracion;
        this.ordenes = ordenes;
        this.usuarios = usuarios;
    }

    // ------------------------------------------------------------------
    // Montarlo
    // ------------------------------------------------------------------

    /**
     * La solicitud con su presupuesto. Si aun no se habia empezado, se empieza:
     * se le congela el precio de la hora del taller.
     */
    @Transactional
    public SolicitudWeb abrir(Long id) {
        SolicitudWeb s = cargar(id);
        if (s.getTarifaHora() == null && s.getEstado().abierta()) {
            s.empezarPresupuesto(configuracion.findById(ConfiguracionTaller.ID_UNICO)
                    .map(ConfiguracionTaller::getTarifaHoraDefecto)
                    .orElseThrow(() -> new ConflictoException(
                            "Falta el precio de la hora del taller en Ajustes > Empresa y facturacion.")));
        }
        return s;
    }

    @Transactional
    public LineaPresupuestoWeb anadirManoDeObra(Long id, String descripcion, BigDecimal horas,
                                                BigDecimal descuentoPct, String codigoTipoIva) {
        SolicitudWeb s = cargar(id);
        TipoIva tipoIva = tipoIva(s, codigoTipoIva);
        return s.anadirManoDeObra(descripcion, horas, descuentoPct, tipoIva.getCodigo(), tipoIva.getPorcentaje());
    }

    @Transactional
    public LineaPresupuestoWeb anadirPieza(Long id, Long piezaId, BigDecimal cantidad, BigDecimal descuentoPct) {
        SolicitudWeb s = cargar(id);
        Pieza pieza = piezas.obtener(piezaId);
        return anadirPieza(s, reglas.findAll(), pieza, cantidad, descuentoPct);
    }

    /** Vuelca una plantilla entera, como en una orden. */
    @Transactional
    public List<LineaPresupuestoWeb> aplicarServicioTipo(Long id, Long servicioTipoId) {
        SolicitudWeb s = cargar(id);
        ServicioTipo servicio = serviciosTipo.obtener(servicioTipoId);
        if (!servicio.isActivo()) {
            throw new ReglaNegocioException(
                    "El servicio «%s» esta retirado. Vuelve a activarlo en Servicios si todavia se ofrece."
                            .formatted(servicio.getNombre()));
        }
        List<ReglaCobro> todas = reglas.findAll();
        List<LineaPresupuestoWeb> anadidas = new ArrayList<>();
        for (LineaServicioTipo plantilla : servicio.getLineas()) {
            if (plantilla.esManoDeObra()) {
                TipoIva tipoIva = tipoIva(s, null);
                anadidas.add(s.anadirManoDeObra(plantilla.getDescripcion(), plantilla.getCantidad(), null,
                        tipoIva.getCodigo(), tipoIva.getPorcentaje()));
            } else {
                anadidas.add(anadirPieza(s, todas, plantilla.getPieza(), plantilla.getCantidad(), null));
            }
        }
        return anadidas;
    }

    @Transactional
    public LineaPresupuestoWeb cambiarCantidad(Long id, Long lineaId, BigDecimal cantidad) {
        SolicitudWeb s = cargar(id);
        LineaPresupuestoWeb linea = linea(s, lineaId);
        BigDecimal anterior = linea.getCantidad();
        s.cambiarCantidadDeLinea(linea, cantidad);
        CobroDePiezas.moverTasa(s, reglas.findAll(), linea, cantidad.subtract(anterior));
        return linea;
    }

    @Transactional
    public LineaPresupuestoWeb cambiarPrecio(Long id, Long lineaId, BigDecimal precioUnitario) {
        SolicitudWeb s = cargar(id);
        LineaPresupuestoWeb linea = linea(s, lineaId);
        s.cambiarPrecioDeManoDeObra(linea, precioUnitario);
        return linea;
    }

    @Transactional
    public LineaPresupuestoWeb cambiarDescuento(Long id, Long lineaId, BigDecimal descuentoPct) {
        SolicitudWeb s = cargar(id);
        LineaPresupuestoWeb linea = linea(s, lineaId);
        s.cambiarDescuentoDeLinea(linea, descuentoPct);
        return linea;
    }

    @Transactional
    public void quitarLinea(Long id, Long lineaId) {
        SolicitudWeb s = cargar(id);
        LineaPresupuestoWeb linea = linea(s, lineaId);
        s.quitarLinea(linea);
        CobroDePiezas.moverTasa(s, reglas.findAll(), linea, linea.getCantidad().negate());
    }

    @Transactional
    public SolicitudWeb cambiarTarifaHora(Long id, BigDecimal tarifa) {
        SolicitudWeb s = cargar(id);
        s.cambiarTarifaHora(tarifa);
        return s;
    }

    @Transactional
    public SolicitudWeb aplicarDescuentoGeneral(Long id, BigDecimal descuentoPct) {
        SolicitudWeb s = cargar(id);
        s.aplicarDescuentoGeneral(descuentoPct);
        return s;
    }

    @Transactional
    public SolicitudWeb aplicarTipoIva(Long id, String codigo) {
        SolicitudWeb s = cargar(id);
        TipoIva tipoIva = cargarTipoIva(codigo);
        s.aplicarTipoIvaGeneral(tipoIva.getCodigo(), tipoIva.getPorcentaje());
        return s;
    }

    // ------------------------------------------------------------------
    // Mandarlo y la respuesta del cliente
    // ------------------------------------------------------------------

    @Transactional
    public SolicitudWeb enviar(Long id, CanalPresupuesto canal, Long usuarioId) {
        SolicitudWeb s = cargar(id);
        s.enviarPresupuesto(canal, usuario(usuarioId));
        return s;
    }

    @Transactional
    public SolicitudWeb reescribir(Long id) {
        SolicitudWeb s = cargar(id);
        s.reescribirPresupuesto();
        return s;
    }

    @Transactional
    public SolicitudWeb rechazar(Long id, String motivo, Long usuarioId) {
        SolicitudWeb s = cargar(id);
        s.rechazarPresupuesto(motivo, usuario(usuarioId));
        return s;
    }

    /**
     * El cliente acepta: se abre la orden de la moto con las lineas del
     * presupuesto, ya presupuestada y aprobada por el cliente, todo o nada.
     *
     * <p>Las lineas pasan por el servicio de ordenes como si se tecleasen alli
     * (las tasas las pone el solo y luego se igualan). Lo que se respeta es el
     * precio que vio el cliente: si una pieza ha subido desde entonces, se le
     * deja al del presupuesto.
     */
    @Transactional
    public OrdenTrabajo aceptar(Long id, Long motoId, int kmEntrada, LocalDate fechaEstimadaSalida,
                                Long tecnicoId, String observaciones, Long usuarioId) {
        SolicitudWeb s = cargar(id);
        if (s.getEstado() != EstadoSolicitud.PRESUPUESTADA) {
            throw new ConflictoException("Solo se acepta un presupuesto que ya se ha mandado al cliente.");
        }

        OrdenTrabajo orden = ordenes.abrir(motoId, s.getNecesita(), kmEntrada, fechaEstimadaSalida, tecnicoId,
                observaciones, usuarioId);
        Long ordenId = orden.getId();
        ordenes.iniciarDiagnostico(ordenId, tecnicoId, usuarioId);
        ordenes.registrarDiagnostico(ordenId, s.getNecesita());
        ordenes.cambiarTarifaHora(ordenId, s.getTarifaHora());
        if (s.getTipoIva() != null) {
            ordenes.aplicarTipoIvaGeneral(ordenId, s.getTipoIva());
        }

        for (LineaPresupuestoWeb l : s.getLineas()) {
            BigDecimal descuento = l.tieneDescuento() ? l.getDescuentoPct() : null;
            if (l.getTipo() == TipoLinea.MANO_DE_OBRA) {
                LineaOT copia = ordenes.anadirManoDeObra(ordenId, l.getDescripcion(), l.getCantidad(), descuento,
                        l.getTipoIva());
                copia.repreciarManoDeObra(l.getPrecioUnitario());
            } else if (l.getTipo() == TipoLinea.PIEZA) {
                LineaOT copia = ordenes.anadirPieza(ordenId, l.getPieza().getId(), l.getCantidad(), descuento,
                        usuarioId);
                BigDecimal subida = copia.getPrecioUnitario().subtract(l.getPrecioUnitario());
                if (subida.signum() > 0) {
                    copia.rebajarPrecio(subida);
                }
            }
        }
        igualarTasas(ordenes.obtener(ordenId), s);

        ordenes.presupuestar(ordenId, usuarioId);
        ordenes.aprobar(ordenId, s.getNombre(), usuarioId);
        s.aceptarPresupuesto(orden, usuario(usuarioId));
        log.info("Presupuesto web {} aceptado: abierta la orden {}", id, orden.codigoVisible());
        return orden;
    }

    /**
     * Las tasas las pone la orden sola al meter cada pieza, sin descuento. Aqui
     * se dejan como estaban en el presupuesto: con su descuento, con su
     * cantidad, o quitadas si alli se quitaron a mano.
     */
    private static void igualarTasas(OrdenTrabajo orden, SolicitudWeb s) {
        for (LineaOT tasa : List.copyOf(orden.getLineas())) {
            if (tasa.getTipo() != TipoLinea.TASA) {
                continue;
            }
            s.getLineas().stream()
                    .filter(l -> l.getTipo() == TipoLinea.TASA
                            && l.getDescripcion().equalsIgnoreCase(tasa.getDescripcion())
                            && l.getPrecioUnitario().compareTo(tasa.getPrecioUnitario()) == 0)
                    .findFirst()
                    .ifPresentOrElse(l -> {
                        tasa.cambiarCantidad(l.getCantidad(), null);
                        tasa.cambiarDescuento(l.getDescuentoPct());
                    }, () -> orden.quitarLinea(tasa));
        }
    }

    // ------------------------------------------------------------------

    /** La solicitud con sus lineas cargadas. */
    @Transactional(readOnly = true)
    public SolicitudWeb cargar(Long id) {
        SolicitudWeb s = solicitudes.findConRelacionesById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud " + id));
        s.getLineas().forEach(l -> {
            if (l.getPieza() != null) {
                l.getPieza().getSku();
            }
        });
        return s;
    }

    private LineaPresupuestoWeb anadirPieza(SolicitudWeb s, List<ReglaCobro> todas, Pieza pieza,
                                            BigDecimal cantidad, BigDecimal descuentoPct) {
        TipoIva tipoIva = tipoIva(s, pieza.getTipoIva());
        LineaPresupuestoWeb linea = CobroDePiezas.anadirConPlus(s, todas, pieza, cantidad, descuentoPct,
                tipoIva.getPorcentaje());
        CobroDePiezas.anadirTasa(s, todas, linea, cantidad, () -> tipoIva(s, null));
        return linea;
    }

    private static LineaPresupuestoWeb linea(SolicitudWeb s, Long lineaId) {
        return s.buscarLinea(lineaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Esa linea no es de este presupuesto."));
    }

    /** El IVA general del presupuesto manda sobre el de la pieza. */
    private TipoIva tipoIva(SolicitudWeb s, String propuesto) {
        return cargarTipoIva(s.getTipoIva() != null ? s.getTipoIva() : propuesto);
    }

    private TipoIva cargarTipoIva(String codigo) {
        String codigoFinal = codigo == null || codigo.isBlank() ? "GENERAL" : codigo;
        return tiposIva.findById(codigoFinal)
                .orElseThrow(() -> new ConflictoException(
                        "El tipo de IVA '%s' no existe en el catalogo.".formatted(codigoFinal)));
    }

    private Usuario usuario(Long id) {
        return id == null ? null : usuarios.findById(id).orElse(null);
    }
}
