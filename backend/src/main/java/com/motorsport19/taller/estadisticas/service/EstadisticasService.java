package com.motorsport19.taller.estadisticas.service;

import com.motorsport19.taller.estadisticas.repository.EstadisticasRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Informes agregados del taller.
 *
 * <p>Todo lo que sale de aqui se calcula en el momento a partir de las tablas
 * reales; no hay tablas de resumen que puedan quedarse desfasadas. Con el
 * volumen de un taller —unos pocos miles de facturas al año— la base de datos
 * responde de sobra, y a cambio los numeros nunca discrepan de la facturacion.
 */
@Service
public class EstadisticasService {

    private final EstadisticasRepository repositorio;

    public EstadisticasService(EstadisticasRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Los doce meses de un ejercicio, con sus derivados ya calculados. */
    @Transactional(readOnly = true)
    public List<ResumenMes> facturacionMensual(int ejercicio) {
        Map<Integer, BigDecimal> dias = new HashMap<>();
        for (Object[] fila : repositorio.diasEnTallerPorMes(ejercicio)) {
            dias.put(((Number) fila[0]).intValue(),
                    BigDecimal.valueOf(((Number) fila[1]).doubleValue()));
        }

        return repositorio.facturacionMensual(ejercicio).stream()
                .map(f -> ResumenMes.de(f, dias.getOrDefault(f.mes(), BigDecimal.ZERO)))
                .toList();
    }

    /**
     * Las facturas de un periodo partidas en dos: las que llevan IVA y las
     * emitidas al 0 %.
     *
     * <p>El periodo es libre y no el ejercicio: es el mismo rango de fechas con
     * el que se esta mirando el libro de facturas, de modo que los numeros de
     * arriba y la lista de abajo hablan siempre de lo mismo.
     */
    @Transactional(readOnly = true)
    public InformeIva facturacionPorIva(LocalDate desde, LocalDate hasta) {
        LocalDate[] rango = rangoEfectivo(desde, hasta);
        desde = rango[0];
        hasta = rango[1];

        List<FilaMesIva> filas = repositorio.facturacionPorIva(desde, hasta);

        List<ResumenMesIva> conIva = mesesDe(filas, true);
        List<ResumenMesIva> sinIva = mesesDe(filas, false);

        BigDecimal totalGeneral = Stream.concat(conIva.stream(), sinIva.stream())
                .map(ResumenMesIva::totalFacturado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new InformeIva(
                desde, hasta,
                ColumnaIva.de(true, conIva, totalGeneral),
                ColumnaIva.de(false, sinIva, totalGeneral));
    }

    /**
     * Completa las fechas que falten.
     *
     * <p>Un extremo abierto se rellena con el del libro, no con el año en curso:
     * al mirar «todo», el informe tiene que hablar de todo lo facturado. Si no
     * hay ni una factura, se devuelve el año actual, que da doce meses a cero en
     * lugar de un rango invertido.
     */
    private LocalDate[] rangoEfectivo(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null) {
            return new LocalDate[]{desde, hasta};
        }
        LocalDate[] libro = repositorio.rangoDelLibro();
        int anio = LocalDate.now().getYear();
        LocalDate primera = libro != null ? libro[0] : LocalDate.of(anio, Month.JANUARY, 1);
        LocalDate ultima = libro != null ? libro[1] : LocalDate.of(anio, Month.DECEMBER, 31);

        return new LocalDate[]{
                desde != null ? desde : primera,
                hasta != null ? hasta : ultima};
    }

    private static List<ResumenMesIva> mesesDe(List<FilaMesIva> filas, boolean conIva) {
        return filas.stream()
                .filter(f -> f.conIva() == conIva)
                .map(ResumenMesIva::de)
                .toList();
    }

    /** Ejercicios con facturas. Si no hay ninguno todavia, el año en curso. */
    @Transactional(readOnly = true)
    public List<Integer> ejerciciosDisponibles() {
        List<Integer> ejercicios = repositorio.ejerciciosConFacturas();
        return ejercicios.isEmpty() ? List.of(LocalDate.now().getYear()) : ejercicios;
    }

    /**
     * Totales de un ejercicio y comparacion con el anterior.
     *
     * <p>El acumulado del año corriente se compara con el mismo tramo del año
     * pasado, no con su total: en agosto, comparar ocho meses contra doce diria
     * que el taller va peor cuando no es cierto.
     */
    @Transactional(readOnly = true)
    public TotalesEjercicio totales(int ejercicio) {
        List<ResumenMes> meses = facturacionMensual(ejercicio);
        int hoyAnio = LocalDate.now().getYear();
        int mesTope = ejercicio == hoyAnio ? LocalDate.now().getMonthValue() : 12;

        BigDecimal base = suma(meses, mesTope, ResumenMes::baseFacturada);
        BigDecimal iva = suma(meses, mesTope, ResumenMes::ivaRepercutido);
        BigDecimal total = suma(meses, mesTope, ResumenMes::totalFacturado);
        BigDecimal compras = suma(meses, mesTope, ResumenMes::comprasMaterial);
        BigDecimal ivaSoportado = suma(meses, mesTope, ResumenMes::ivaSoportado);
        BigDecimal coste = suma(meses, mesTope, ResumenMes::costeMaterialVendido);
        BigDecimal manoDeObra = suma(meses, mesTope, ResumenMes::ingresoManoDeObra);
        BigDecimal piezas = suma(meses, mesTope, ResumenMes::ingresoPiezas);
        int facturas = meses.stream().limit(mesTope).mapToInt(ResumenMes::numeroFacturas).sum();

        List<ResumenMes> anterior = facturacionMensual(ejercicio - 1);
        BigDecimal baseAnterior = suma(anterior, mesTope, ResumenMes::baseFacturada);

        return new TotalesEjercicio(
                ejercicio,
                base, iva, total, facturas,
                manoDeObra, piezas,
                compras, ivaSoportado,
                iva.subtract(ivaSoportado),
                coste,
                base.subtract(coste),
                porcentaje(base.subtract(coste), base),
                facturas == 0 ? BigDecimal.ZERO
                        : total.divide(BigDecimal.valueOf(facturas), 2, RoundingMode.HALF_UP),
                variacion(base, baseAnterior),
                mesTope);
    }

    /**
     * Lo que esta hecho y sin cobrar, ahora mismo.
     *
     * <p>Va con el informe pero no depende de su ejercicio: son ordenes
     * terminadas sin factura, sean de cuando sean.
     */
    @Transactional(readOnly = true)
    public TrabajoSinFacturar trabajoSinFacturar() {
        List<TrabajoSinFacturar.Fila> filas = repositorio.trabajoSinFacturar();

        BigDecimal importe = filas.stream()
                .map(TrabajoSinFacturar.Fila::importe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new TrabajoSinFacturar(filas.size(), importe, filas);
    }

    /** Los clientes que mas han facturado en el ejercicio. */
    @Transactional(readOnly = true)
    public List<FilaReparto> mejoresClientes(int ejercicio, int limite) {
        return repositorio.mejoresClientes(
                LocalDate.of(ejercicio, Month.JANUARY, 1),
                LocalDate.of(ejercicio, Month.DECEMBER, 31),
                limite);
    }

    /** Las piezas que mas salen del almacen en el ejercicio. */
    @Transactional(readOnly = true)
    public List<FilaReparto> piezasMasUsadas(int ejercicio, int limite) {
        return repositorio.piezasMasUsadas(
                LocalDate.of(ejercicio, Month.JANUARY, 1),
                LocalDate.of(ejercicio, Month.DECEMBER, 31),
                limite);
    }

    private static BigDecimal suma(List<ResumenMes> meses, int hastaMes,
                                   java.util.function.Function<ResumenMes, BigDecimal> campo) {
        return meses.stream()
                .limit(hastaMes)
                .map(campo)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0
                ? BigDecimal.ZERO
                : parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    /**
     * Variacion porcentual frente al año anterior.
     *
     * <p>Sin base con la que comparar no se devuelve un 100 % inventado: se
     * devuelve nulo y la interfaz no enseña la comparacion. Un taller que abrio
     * en marzo no ha crecido un 100 %, es que antes no existia.
     */
    private static BigDecimal variacion(BigDecimal actual, BigDecimal anterior) {
        if (anterior.signum() == 0) {
            return null;
        }
        return actual.subtract(anterior)
                .multiply(BigDecimal.valueOf(100))
                .divide(anterior.abs(), 1, RoundingMode.HALF_UP);
    }
}
