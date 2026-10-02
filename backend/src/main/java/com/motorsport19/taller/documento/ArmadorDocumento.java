package com.motorsport19.taller.documento;

import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.LineaFactura;
import com.motorsport19.taller.orden.domain.LineaImporte;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.domain.TipoLinea;
import com.motorsport19.taller.solicitud.domain.SolicitudWeb;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Traduce una orden o una factura a lo que necesita el papel.
 *
 * <p>Aqui viven las decisiones de presentacion que comparten los dos
 * documentos: como se agrupan las lineas, que codigo lleva cada una y que va en
 * las casillas del formato que el taller usa pero que el programa todavia no
 * gestiona.
 */
@Component
public class ArmadorDocumento {

    /** Codigo generico de las lineas de mano de obra, como en el documento de siempre. */
    private static final String CODIGO_MANO_OBRA = "GEN";

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Dias que se mantiene el precio de un presupuesto. */
    private static final int DIAS_VALIDEZ = 30;

    public DocumentoImprimible presupuesto(OrdenTrabajo orden, List<LineaOT> lineas,
                                           ConfiguracionTaller cfg) {
        var cliente = orden.getCliente();
        var moto = orden.getMoto();
        return presupuesto(
                referenciaPresupuesto(orden),
                orden.getFechaEntrada().atZone(MADRID).toLocalDate(),
                new DocumentoImprimible.Cliente(
                        cliente.nombreCompleto(), cliente.getCiudad(),
                        cliente.getDocumento(), numeroCliente(cliente.getId()), cliente.getTelefono()),
                new DocumentoImprimible.Vehiculo(
                        moto.getMatricula(), moto.getNumeroBastidor(), moto.descripcion(),
                        orden.getKmEntrada()),
                lineas, cfg, orden.getObservaciones());
    }

    /**
     * El presupuesto de una solicitud de la web, en el mismo papel que el de una
     * orden. Quien lo pide todavia no es cliente del taller: sin numero de
     * cliente, sin NIF y sin bastidor.
     */
    public DocumentoImprimible presupuestoWeb(SolicitudWeb solicitud, List<? extends LineaImporte> lineas,
                                              ConfiguracionTaller cfg) {
        Instant fecha = solicitud.getPresupuestadaEn() != null ? solicitud.getPresupuestadaEn() : Instant.now();
        return presupuesto(
                "WEB|PRE|%011d".formatted(solicitud.getId()),
                fecha.atZone(MADRID).toLocalDate(),
                new DocumentoImprimible.Cliente(solicitud.getNombre(), null, null, "", solicitud.getTelefono()),
                new DocumentoImprimible.Vehiculo(
                        solicitud.getMatricula(), null, solicitud.getMarca() + " " + solicitud.getModelo(), null),
                lineas, cfg, null);
    }

    /**
     * El presupuesto sin rellenar, para hacerlo a boli en el mismo papel. Solo
     * lleva impresos los datos del taller.
     */
    public DocumentoImprimible plantillaPresupuesto(ConfiguracionTaller cfg) {
        return new DocumentoImprimible(
                "PRESUPUESTO", "TOTAL PRESUPUESTO", null, null, null, null,
                emisor(cfg),
                new DocumentoImprimible.Cliente(null, null, null, null, null),
                new DocumentoImprimible.Vehiculo(null, null, null, null),
                "", null, List.of(), null, null);
    }

    private DocumentoImprimible presupuesto(String referencia, LocalDate fecha,
                                            DocumentoImprimible.Cliente cliente,
                                            DocumentoImprimible.Vehiculo vehiculo,
                                            List<? extends LineaImporte> lineas,
                                            ConfiguracionTaller cfg, String observaciones) {
        List<DocumentoImprimible.Linea> filas = new ArrayList<>();
        agrupar(filas, lineas);

        // Las tasas van en su casilla, no en el importe: importe - dto. + tasas = base.
        List<? extends LineaImporte> tasas = lineas.stream().filter(l -> l.getTipo() == TipoLinea.TASA).toList();
        List<? extends LineaImporte> resto = lineas.stream().filter(l -> l.getTipo() != TipoLinea.TASA).toList();
        BigDecimal bruto = suma(resto, LineaImporte::importeBruto);
        BigDecimal descuento = suma(resto, LineaImporte::importeDescuento);
        BigDecimal base = suma(lineas, LineaImporte::getBaseImponible);
        BigDecimal iva = suma(lineas, LineaImporte::getCuotaIva);

        return new DocumentoImprimible(
                "PRESUPUESTO",
                "TOTAL PRESUPUESTO",
                referencia,
                "ORDINARIA",
                fecha,
                "S/N",
                emisor(cfg),
                cliente,
                vehiculo,
                "CONTADO",
                fecha.plusDays(DIAS_VALIDEZ),
                filas,
                totales(bruto, descuento, suma(tasas, LineaImporte::getBaseImponible), base, iva,
                        porcentajeDominante(lineas)),
                observaciones);
    }

    public DocumentoImprimible factura(Factura factura, List<LineaFactura> lineas,
                                       ConfiguracionTaller cfg) {
        List<DocumentoImprimible.Linea> filas = new ArrayList<>();
        agruparFactura(filas, lineas);

        BigDecimal bruto = lineas.stream().filter(l -> l.getTipo() != TipoLinea.TASA)
                .map(LineaFactura::importeBruto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuento = lineas.stream().filter(l -> l.getTipo() != TipoLinea.TASA)
                .map(LineaFactura::importeDescuento).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tasas = lineas.stream().filter(l -> l.getTipo() == TipoLinea.TASA)
                .map(l -> l.importes().baseImponible()).reduce(BigDecimal.ZERO, BigDecimal::add);

        var receptor = factura.getDatosReceptor();

        // El rotulo dice lo que es el papel. Una simplificada no identifica al
        // destinatario, asi que quien la recibe tiene que ver de un vistazo que
        // no le sirve para deducirse el IVA.
        String titulo = factura.getTipo().name().equals("RECTIFICATIVA")
                ? "FACTURA RECTIFICATIVA"
                : factura.isSimplificada() ? "FACTURA SIMPLIFICADA" : "FACTURA";

        return new DocumentoImprimible(
                titulo,
                "TOTAL FACTURA",
                factura.getNumeroCompleto(),
                factura.getTipo().name(),
                factura.getFechaEmision(),
                "S/N",
                emisor(cfg),
                new DocumentoImprimible.Cliente(
                        receptor.getNombre(),
                        // En una simplificada no hay domicilio ni NIF que imprimir:
                        // las casillas salen vacias en vez de con un hueco raro.
                        factura.isSimplificada() ? null : receptor.getCiudad(),
                        factura.isSimplificada() ? null : receptor.getNif(),
                        factura.getReceptor() == null ? "" : numeroCliente(factura.getReceptor().getId()),
                        null),
                new DocumentoImprimible.Vehiculo(
                        factura.getMatricula(), null, factura.getDescripcionVehiculo(), null),
                cfg.getNumeroCuenta() == null ? "CONTADO" : cfg.getNumeroCuenta(),
                null,
                filas,
                totales(bruto, descuento, tasas, factura.getBaseImponible(), factura.getTotalIva(),
                        porcentajeDominanteFactura(lineas)),
                null);
    }

    // ==================================================================

    /**
     * Agrupa por bloques, con su rotulo y su subtotal.
     *
     * <p>Primero la mano de obra y luego el material, que es el orden en que lo
     * lee un cliente: primero lo que se ha hecho, despues lo que se ha puesto.
     */
    private void agrupar(List<DocumentoImprimible.Linea> destino, List<? extends LineaImporte> lineas) {
        anadirBloque(destino, "MANO DE OBRA",
                lineas.stream().filter(LineaImporte::esManoDeObra).toList(),
                l -> CODIGO_MANO_OBRA, LineaImporte::getDescripcion, LineaImporte::getCantidad,
                LineaImporte::getPrecioUnitario, LineaImporte::getDescuentoPct, LineaImporte::getBaseImponible);

        anadirBloque(destino, "MATERIAL",
                lineas.stream().filter(l -> !l.esManoDeObra()).toList(),
                l -> l.skuPieza() == null ? "" : l.skuPieza(), LineaImporte::getDescripcion,
                LineaImporte::getCantidad, LineaImporte::getPrecioUnitario, LineaImporte::getDescuentoPct,
                LineaImporte::getBaseImponible);
    }

    private void agruparFactura(List<DocumentoImprimible.Linea> destino, List<LineaFactura> lineas) {
        anadirBloque(destino, "MANO DE OBRA",
                lineas.stream().filter(l -> l.getTipo() == TipoLinea.MANO_DE_OBRA).toList(),
                l -> CODIGO_MANO_OBRA, LineaFactura::getDescripcion, LineaFactura::getCantidad,
                LineaFactura::getPrecioUnitario, LineaFactura::getDescuentoPct,
                l -> l.importes().baseImponible());

        anadirBloque(destino, "MATERIAL",
                lineas.stream().filter(l -> l.getTipo() != TipoLinea.MANO_DE_OBRA).toList(),
                l -> l.getPiezaSku() == null ? "" : l.getPiezaSku(), LineaFactura::getDescripcion,
                LineaFactura::getCantidad, LineaFactura::getPrecioUnitario,
                LineaFactura::getDescuentoPct, l -> l.importes().baseImponible());
    }

    private <T> void anadirBloque(List<DocumentoImprimible.Linea> destino, String rotulo, List<T> lineas,
                                  java.util.function.Function<T, String> codigo,
                                  java.util.function.Function<T, String> descripcion,
                                  java.util.function.Function<T, BigDecimal> cantidad,
                                  java.util.function.Function<T, BigDecimal> precio,
                                  java.util.function.Function<T, BigDecimal> descuento,
                                  java.util.function.Function<T, BigDecimal> base) {
        if (lineas.isEmpty()) {
            return;
        }
        BigDecimal subtotal = lineas.stream().map(base)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        destino.add(DocumentoImprimible.Linea.cabecera(rotulo, subtotal));
        for (T l : lineas) {
            destino.add(DocumentoImprimible.Linea.de(
                    codigo.apply(l), descripcion.apply(l), cantidad.apply(l),
                    precio.apply(l), descuento.apply(l), base.apply(l)));
        }
    }

    private DocumentoImprimible.Emisor emisor(ConfiguracionTaller c) {
        String poblacion = "%s %s %s".formatted(
                valorOVacio(c.getCodigoPostal()), valorOVacio(c.getCiudad()),
                valorOVacio(c.getProvincia())).trim().replaceAll("\\s+", " ");

        return new DocumentoImprimible.Emisor(
                c.getRazonSocial(), c.getDireccion(), poblacion,
                c.getNif(), c.getTelefono(), c.getEmail());
    }

    /**
     * Las casillas del formato que el programa aun no gestiona van a cero.
     *
     * <p>Portes, descuentos globales e IRPF forman parte del documento que usa
     * el taller. Se imprimen a cero en vez de quitarlas: el papel sigue siendo
     * el mismo, y el dia que hagan falta ya tienen su sitio, como lo tenian las
     * tasas.
     */
    private DocumentoImprimible.Totales totales(BigDecimal bruto, BigDecimal descuento,
                                                BigDecimal tasas, BigDecimal base, BigDecimal iva,
                                                BigDecimal porcentajeIva) {
        return new DocumentoImprimible.Totales(
                bruto, descuento, tasas, CERO, CERO,
                base, porcentajeIva, iva,
                CERO, CERO,
                base.add(iva));
    }

    /**
     * El tipo de IVA que se enseña en la banda de totales.
     *
     * <p>Con varios tipos en el mismo documento se enseña el de mayor base, que
     * es el que manda. El desglose completo sigue estando en la factura.
     */
    private BigDecimal porcentajeDominante(List<? extends LineaImporte> lineas) {
        return lineas.stream()
                .max(java.util.Comparator.comparing(l ->
                        l.getBaseImponible() == null ? BigDecimal.ZERO : l.getBaseImponible()))
                .map(LineaImporte::getPorcentajeIva)
                .orElse(CERO);
    }

    private BigDecimal porcentajeDominanteFactura(List<LineaFactura> lineas) {
        return lineas.stream()
                .max(java.util.Comparator.comparing(l -> l.importes().baseImponible()))
                .map(LineaFactura::getPorcentajeIva)
                .orElse(CERO);
    }

    /** «ORD|PRE|202600000000041», como lo numera el taller. */
    private String referenciaPresupuesto(OrdenTrabajo orden) {
        return "ORD|PRE|%d%011d".formatted(orden.getEjercicio(), orden.getNumero());
    }

    private String numeroCliente(Long id) {
        return "%05d".formatted(id);
    }

    private BigDecimal suma(List<? extends LineaImporte> lineas,
                            java.util.function.Function<LineaImporte, BigDecimal> campo) {
        return lineas.stream().map(campo)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String valorOVacio(String valor) {
        return valor == null ? "" : valor;
    }
}
