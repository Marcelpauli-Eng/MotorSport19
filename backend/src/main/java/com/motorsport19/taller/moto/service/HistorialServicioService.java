package com.motorsport19.taller.moto.service;

import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.configuracion.service.ConfiguracionTallerService;
import com.motorsport19.taller.documento.HistorialImprimible;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.orden.domain.EstadoOT;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.repository.OrdenTrabajoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * La hoja de vida de una moto: todo lo que se le ha hecho en el taller.
 *
 * <p>Se arma en el momento a partir de las ordenes de trabajo. No hay ninguna
 * tabla de historial que mantener al dia, con lo que el papel no puede
 * contradecir a las ordenes de las que sale.
 *
 * <p><b>Que entra y que no.</b> Solo las intervenciones terminadas: una orden en
 * curso todavia no es historial, y un presupuesto rechazado no es trabajo
 * hecho. Se incluye tambien la que esta lista y sin recoger, porque el trabajo
 * ya se hizo.
 */
@Service
public class HistorialServicioService {

    /** Estados en los que el trabajo ya esta hecho. */
    private static final Set<EstadoOT> TERMINADAS = EnumSet.of(EstadoOT.ENTREGADA, EstadoOT.LISTA);

    private static final ZoneId ZONA_TALLER = ZoneId.systemDefault();

    private final MotoService motoService;
    private final OrdenTrabajoRepository ordenRepository;
    private final ConfiguracionTallerService configuracion;

    public HistorialServicioService(MotoService motoService,
                                    OrdenTrabajoRepository ordenRepository,
                                    ConfiguracionTallerService configuracion) {
        this.motoService = motoService;
        this.ordenRepository = ordenRepository;
        this.configuracion = configuracion;
    }

    /**
     * Prepara el historial de una moto.
     *
     * @param conImportes si se imprime lo que costo cada intervencion. Con ellos
     *                    el papel demuestra el dinero invertido, que es lo que le
     *                    da valor al vender; sin ellos sirve para enseñarselo a
     *                    un tercero sin airear lo que cobra el taller
     */
    @Transactional(readOnly = true)
    public HistorialImprimible preparar(Long motoId, boolean conImportes) {
        Moto moto = motoService.obtener(motoId);
        ConfiguracionTaller taller = configuracion.obligatoria();

        // De la mas antigua a la mas reciente: un historial se lee hacia delante,
        // aunque el listado de la ficha lo enseñe al reves.
        List<OrdenTrabajo> ordenes = ordenRepository.historialDeMoto(motoId).stream()
                .filter(o -> TERMINADAS.contains(o.getEstado()))
                .sorted(Comparator.comparing(OrdenTrabajo::getFechaEntrada))
                .toList();

        List<HistorialImprimible.Intervencion> intervenciones = ordenes.stream()
                .map(this::aIntervencion)
                .toList();

        return new HistorialImprimible(
                new HistorialImprimible.Emisor(
                        taller.getRazonSocial(), taller.getDireccion(), poblacion(taller),
                        taller.getNif(), taller.getTelefono(), taller.getEmail()),
                new HistorialImprimible.Vehiculo(
                        moto.getMatricula(), moto.getMarca(), moto.getModelo(), moto.getAnio(),
                        moto.getNumeroBastidor(), moto.getKmActual()),
                moto.getCliente() == null ? null : moto.getCliente().nombreCompleto(),
                LocalDate.now(),
                conImportes,
                resumir(intervenciones),
                intervenciones);
    }

    // ------------------------------------------------------------------

    private HistorialImprimible.Intervencion aIntervencion(OrdenTrabajo orden) {
        List<LineaOT> lineas = orden.getLineas();

        List<String> trabajos = lineas.stream()
                .filter(l -> !l.esDePieza())
                .map(LineaOT::getDescripcion)
                .toList();

        // La cantidad solo se dice cuando es mas de una: «Pastillas de freno» se
        // lee mejor que «1 x Pastillas de freno».
        List<String> piezas = lineas.stream()
                .filter(LineaOT::esDePieza)
                .map(l -> l.getCantidad() != null && l.getCantidad().compareTo(BigDecimal.ONE) > 0
                        ? "%s x %s".formatted(cantidad(l.getCantidad()), l.getDescripcion())
                        : l.getDescripcion())
                .toList();

        BigDecimal importe = lineas.stream()
                .map(LineaOT::getTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new HistorialImprimible.Intervencion(
                orden.codigoVisible(),
                enDia(orden.getFechaEntrada()),
                orden.getFechaRealSalida() == null ? null : enDia(orden.getFechaRealSalida()),
                orden.getKmEntrada(),
                orden.getProblemaReportado(),
                orden.getDiagnostico(),
                orden.getTecnico() == null ? null : orden.getTecnico().getNombreCompleto(),
                trabajos,
                piezas,
                importe);
    }

    /**
     * Las cifras de cabecera.
     *
     * <p>Los kilometros recorridos salen de la diferencia entre la primera y la
     * ultima lectura, no del cuentakilometros de hoy: es lo que se puede
     * demostrar con este papel en la mano.
     */
    private HistorialImprimible.Resumen resumir(List<HistorialImprimible.Intervencion> intervenciones) {
        if (intervenciones.isEmpty()) {
            return new HistorialImprimible.Resumen(0, null, null, null, BigDecimal.ZERO);
        }

        HistorialImprimible.Intervencion primera = intervenciones.get(0);
        HistorialImprimible.Intervencion ultima = intervenciones.get(intervenciones.size() - 1);

        Integer recorridos = null;
        if (primera.km() != null && ultima.km() != null && ultima.km() > primera.km()) {
            recorridos = ultima.km() - primera.km();
        }

        BigDecimal total = intervenciones.stream()
                .map(HistorialImprimible.Intervencion::importe)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new HistorialImprimible.Resumen(
                intervenciones.size(), primera.fecha(), ultima.fecha(), recorridos, total);
    }

    private static LocalDate enDia(java.time.Instant instante) {
        return instante.atZone(ZONA_TALLER).toLocalDate();
    }

    private static String cantidad(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }

    private static String poblacion(ConfiguracionTaller c) {
        return "%s %s %s".formatted(
                        valorOVacio(c.getCodigoPostal()), valorOVacio(c.getCiudad()),
                        valorOVacio(c.getProvincia()))
                .trim().replaceAll("\\s+", " ");
    }

    private static String valorOVacio(String valor) {
        return valor == null ? "" : valor;
    }
}
