package com.motorsport19.taller.documento;

import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.LineaFactura;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.domain.TipoLinea;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    /** Dias que se mantiene el precio de un presupuesto. */
    private static final int DIAS_VALIDEZ = 30;

    public DocumentoImprimible presupuesto(OrdenTrabajo orden, List<LineaOT> lineas,
                                           ConfiguracionTaller cfg) {
        var cliente = orden.getCliente();
        var moto = orden.getMoto();

        List<DocumentoImprimible.Linea> filas = new ArrayList<>();
        agrupar(filas, lineas);

        BigDecimal bruto = suma(lineas, LineaOT::importeBruto);
        BigDecimal descuento = suma(lineas, LineaOT::importeDescuento);
        BigDecimal base = suma(lineas, LineaOT::getBaseImponible);
        BigDecimal iva = suma(lineas, LineaOT::getCuotaIva);

        return new DocumentoImprimible(
                "PRESUPUESTO",
                "TOTAL PRESUPUESTO",
                referenciaPresupuesto(orden),
                "ORDINARIA",
                orden.getFechaEntrada().atZone(java.time.ZoneId.of("Europe/Madrid")).toLocalDate(),
                "S/N",
                emisor(cfg),
                new DocumentoImprimible.Cliente(
                        cliente.nombreCompleto(), cliente.getCiudad(),
                        cliente.getDocumento(), numeroCliente(cliente.getId()), cliente.getTelefono()),
                new DocumentoImprimible.Vehiculo(
                        moto.getMatricula(), moto.getNumeroBastidor(), moto.descripcion(),
                        orden.getKmEntrada()),
                "CONTADO",
                orden.getFechaEntrada().atZone(java.time.ZoneId.of("Europe/Madrid"))
                        .toLocalDate().plusDays(DIAS_VALIDEZ),
                filas,
                totales(bruto, descuento, base, iva, porcentajeDominante(lineas)),
                orden.getObservaciones());
    }

    public DocumentoImprimible factura(Factura factura, List<LineaFactura> lineas,
                                       ConfiguracionTaller cfg) {
        List<DocumentoImprimible.Linea> filas = new ArrayList<>();
        agruparFactura(filas, lineas);

        BigDecimal bruto = lineas.stream().map(LineaFactura::importeBruto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuento = lineas.stream().map(LineaFactura::importeDescuento)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var receptor = factura.getDatosReceptor();

        return new DocumentoImprimible(
                factura.getTipo().name().equals("RECTIFICATIVA") ? "FACTURA RECTIFICATIVA" : "FACTURA",
                "TOTAL FACTURA",
                factura.getNumeroCompleto(),
                factura.getTipo().name(),
                factura.getFechaEmision(),
                "S/N",
                emisor(cfg),
                new DocumentoImprimible.Cliente(
                        receptor.getNombre(), receptor.getCiudad(), receptor.getNif(),
                        factura.getReceptor() == null ? "" : numeroCliente(factura.getReceptor().getId()),
                        null),
                new DocumentoImprimible.Vehiculo(
                        factura.getMatricula(), null, factura.getDescripcionVehiculo(), null),
                "CONTADO",
                null,
                filas,
                totales(bruto, descuento, factura.getBaseImponible(), factura.getTotalIva(),
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
    private void agrupar(List<DocumentoImprimible.Linea> destino, List<LineaOT> lineas) {
        anadirBloque(destino, "MANO DE OBRA",
                lineas.stream().filter(l -> !l.esDePieza()).toList(),
                l -> CODIGO_MANO_OBRA, LineaOT::getDescripcion, LineaOT::getCantidad,
                LineaOT::getPrecioUnitario, LineaOT::getDescuentoPct, LineaOT::getBaseImponible);

        anadirBloque(destino, "MATERIAL",
                lineas.stream().filter(LineaOT::esDePieza).toList(),
                l -> l.skuPieza() == null ? "" : l.skuPieza(), LineaOT::getDescripcion,
                LineaOT::getCantidad, LineaOT::getPrecioUnitario, LineaOT::getDescuentoPct,
                LineaOT::getBaseImponible);
    }

    private void agruparFactura(List<DocumentoImprimible.Linea> destino, List<LineaFactura> lineas) {
        anadirBloque(destino, "MANO DE OBRA",
                lineas.stream().filter(l -> l.getTipo() != TipoLinea.PIEZA).toList(),
                l -> CODIGO_MANO_OBRA, LineaFactura::getDescripcion, LineaFactura::getCantidad,
                LineaFactura::getPrecioUnitario, LineaFactura::getDescuentoPct,
                l -> l.importes().baseImponible());

        anadirBloque(destino, "MATERIAL",
                lineas.stream().filter(l -> l.getTipo() == TipoLinea.PIEZA).toList(),
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
     * <p>Tasas, portes, descuentos globales e IRPF forman parte del documento
     * que usa el taller. Se imprimen a cero en vez de quitarlas: el papel sigue
     * siendo el mismo, y el dia que hagan falta ya tienen su sitio.
     */
    private DocumentoImprimible.Totales totales(BigDecimal bruto, BigDecimal descuento,
                                                BigDecimal base, BigDecimal iva,
                                                BigDecimal porcentajeIva) {
        return new DocumentoImprimible.Totales(
                bruto, descuento, CERO, CERO, CERO,
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
    private BigDecimal porcentajeDominante(List<LineaOT> lineas) {
        return lineas.stream()
                .max(java.util.Comparator.comparing(l ->
                        l.getBaseImponible() == null ? BigDecimal.ZERO : l.getBaseImponible()))
                .map(LineaOT::getPorcentajeIva)
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

    private BigDecimal suma(List<LineaOT> lineas,
                            java.util.function.Function<LineaOT, BigDecimal> campo) {
        return lineas.stream().map(campo)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String valorOVacio(String valor) {
        return valor == null ? "" : valor;
    }
}
