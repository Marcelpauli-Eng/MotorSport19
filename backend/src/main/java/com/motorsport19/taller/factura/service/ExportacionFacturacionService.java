package com.motorsport19.taller.factura.service;

import com.motorsport19.taller.factura.domain.DesgloseIvaFactura;
import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.TipoEventoFactura;
import com.motorsport19.taller.factura.repository.FacturaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exportacion del libro registro de facturacion.
 *
 * <p>Dos formatos con dos usos distintos:
 *
 * <ul>
 *   <li><b>CSV</b> para la gestoria: una fila por factura, abrible en cualquier
 *       hoja de calculo.</li>
 *   <li><b>JSON</b> para archivo y auditoria: incluye lineas, desglose de IVA y
 *       la cadena de huellas completa, de modo que la integridad del libro se
 *       puede verificar <b>fuera</b> de este sistema. Un libro que solo se puede
 *       comprobar con el programa que lo escribio no demuestra gran cosa.</li>
 * </ul>
 *
 * <p>Toda exportacion queda anotada en el registro de eventos.
 */
@Service
public class ExportacionFacturacionService {

    private static final Logger log = LoggerFactory.getLogger(ExportacionFacturacionService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final char SEPARADOR = ';';

    private final FacturaRepository facturaRepository;
    private final RegistroEventosService registroEventos;
    private final ObjectMapper objectMapper;

    public ExportacionFacturacionService(FacturaRepository facturaRepository,
                                         RegistroEventosService registroEventos,
                                         ObjectMapper objectMapper) {
        this.facturaRepository = facturaRepository;
        this.registroEventos = registroEventos;
        this.objectMapper = objectMapper;
    }

    /**
     * Libro registro en CSV, con punto y coma como separador.
     *
     * <p>Lleva BOM UTF-8 al principio: sin el, Excel en Windows abre el fichero en
     * ANSI y destroza los acentos de los nombres de los clientes.
     */
    @Transactional
    public byte[] exportarCsv(LocalDate desde, LocalDate hasta, Long usuarioId) {
        List<Factura> facturas = facturaRepository.buscarParaExportar(desde, hasta);

        StringBuilder csv = new StringBuilder("﻿");
        csv.append(String.join(String.valueOf(SEPARADOR),
                "posicion_registro", "numero_factura", "tipo", "fecha_emision", "fecha_operacion",
                "receptor_nombre", "receptor_nif", "base_imponible", "total_iva", "total",
                "codigo_ot", "matricula", "rectifica_a", "huella_anterior", "huella")).append('\n');

        for (Factura f : facturas) {
            csv.append(f.getNumeroRegistro()).append(SEPARADOR)
                    .append(campo(f.getNumeroCompleto())).append(SEPARADOR)
                    .append(f.getTipo()).append(SEPARADOR)
                    .append(FECHA.format(f.getFechaEmision())).append(SEPARADOR)
                    .append(FECHA.format(f.getFechaOperacion())).append(SEPARADOR)
                    .append(campo(f.getDatosReceptor().getNombre())).append(SEPARADOR)
                    .append(campo(f.getDatosReceptor().getNif())).append(SEPARADOR)
                    .append(decimal(f.getBaseImponible())).append(SEPARADOR)
                    .append(decimal(f.getTotalIva())).append(SEPARADOR)
                    .append(decimal(f.getTotal())).append(SEPARADOR)
                    .append(campo(f.getCodigoOt())).append(SEPARADOR)
                    .append(campo(f.getMatricula())).append(SEPARADOR)
                    .append(campo(f.getFacturaRectificada() == null
                            ? null : f.getFacturaRectificada().getNumeroCompleto())).append(SEPARADOR)
                    .append(f.getHuellaAnterior()).append(SEPARADOR)
                    .append(f.getHuella()).append('\n');
        }

        anotarExportacion("CSV", desde, hasta, facturas, usuarioId);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Libro registro completo en JSON, con lineas, desglose y cadena de huellas.
     *
     * <p>Incluye a proposito la {@code cadena_huella} de cada factura: con ese
     * texto y un SHA-256 cualquiera puede recalcular las huellas y comprobar el
     * encadenamiento sin depender de este programa.
     */
    @Transactional
    public byte[] exportarJson(LocalDate desde, LocalDate hasta, Long usuarioId) {
        List<Factura> facturas = facturaRepository.buscarParaExportar(desde, hasta);

        ObjectNode raiz = objectMapper.createObjectNode();
        raiz.put("generado", java.time.Instant.now().toString());
        raiz.put("desde", desde == null ? null : FECHA.format(desde));
        raiz.put("hasta", hasta == null ? null : FECHA.format(hasta));
        raiz.put("facturas_exportadas", facturas.size());
        raiz.put("algoritmo_huella", com.motorsport19.taller.factura.domain.CalculadoraHuella.ALGORITMO);
        raiz.put("huella_genesis", Factura.HUELLA_GENESIS);

        ArrayNode array = raiz.putArray("facturas");
        for (Factura f : facturas) {
            ObjectNode nodo = array.addObject();
            nodo.put("posicion_registro", f.getNumeroRegistro());
            nodo.put("numero", f.getNumeroCompleto());
            nodo.put("tipo", f.getTipo().name());
            nodo.put("fecha_emision", FECHA.format(f.getFechaEmision()));
            nodo.put("fecha_operacion", FECHA.format(f.getFechaOperacion()));
            nodo.put("timestamp_emision", f.getTimestampEmision().toString());

            ObjectNode emisor = nodo.putObject("emisor");
            emisor.put("nombre", f.getEmisor().getNombre());
            emisor.put("nif", f.getEmisor().getNif());

            ObjectNode receptor = nodo.putObject("receptor");
            receptor.put("nombre", f.getDatosReceptor().getNombre());
            receptor.put("nif", f.getDatosReceptor().getNif());
            receptor.put("direccion", f.getDatosReceptor().getDireccion());
            receptor.put("codigo_postal", f.getDatosReceptor().getCodigoPostal());
            receptor.put("ciudad", f.getDatosReceptor().getCiudad());
            receptor.put("provincia", f.getDatosReceptor().getProvincia());

            nodo.put("codigo_ot", f.getCodigoOt());
            nodo.put("matricula", f.getMatricula());
            nodo.put("base_imponible", f.getBaseImponible());
            nodo.put("total_iva", f.getTotalIva());
            nodo.put("total", f.getTotal());

            if (f.getFacturaRectificada() != null) {
                ObjectNode rect = nodo.putObject("rectificacion");
                rect.put("rectifica_a", f.getFacturaRectificada().getNumeroCompleto());
                rect.put("tipo", String.valueOf(f.getTipoRectificativa()));
                rect.put("motivo", f.getMotivoRectificacion());
            }

            ArrayNode lineas = nodo.putArray("lineas");
            f.getLineas().forEach(l -> {
                ObjectNode nl = lineas.addObject();
                nl.put("numero", l.getNumeroLinea());
                nl.put("tipo", l.getTipo().name());
                nl.put("descripcion", l.getDescripcion());
                nl.put("sku", l.getPiezaSku());
                nl.put("cantidad", l.getCantidad());
                nl.put("precio_unitario", l.getPrecioUnitario());
                nl.put("descuento_pct", l.getDescuentoPct());
                nl.put("porcentaje_iva", l.getPorcentajeIva());
                nl.put("base_imponible", l.importes().baseImponible());
                nl.put("cuota_iva", l.importes().cuotaIva());
            });

            ArrayNode desglose = nodo.putArray("desglose_iva");
            for (DesgloseIvaFactura d : f.getDesgloseIva()) {
                ObjectNode nd = desglose.addObject();
                nd.put("tipo_iva", d.getTipoIva());
                nd.put("porcentaje", d.getPorcentajeIva());
                nd.put("base_imponible", d.getBaseImponible());
                nd.put("cuota_iva", d.getCuotaIva());
            }

            ObjectNode sello = nodo.putObject("sello");
            sello.put("huella_anterior", f.getHuellaAnterior());
            sello.put("huella", f.getHuella());
            sello.put("algoritmo", f.getAlgoritmoHuella());
            // Con esto y un SHA-256 se puede reverificar la cadena por fuera.
            sello.put("cadena_huella", f.getCadenaHuella());
        }

        anotarExportacion("JSON", desde, hasta, facturas, usuarioId);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(raiz);
        } catch (Exception e) {
            throw new IllegalStateException("No se ha podido serializar la exportacion JSON", e);
        }
    }

    // ------------------------------------------------------------------

    private void anotarExportacion(String formato, LocalDate desde, LocalDate hasta,
                                   List<Factura> facturas, Long usuarioId) {
        String rango = "%s a %s".formatted(
                desde == null ? "inicio" : FECHA.format(desde),
                hasta == null ? "hoy" : FECHA.format(hasta));

        registroEventos.anotar(null, TipoEventoFactura.EXPORTACION, usuarioId,
                "Exportacion %s del libro registro (%s): %d facturas"
                        .formatted(formato, rango, facturas.size()),
                """
                {"formato":"%s","desde":"%s","hasta":"%s","facturas":%d}"""
                        .formatted(formato, desde, hasta, facturas.size()),
                null);

        log.info("Exportacion {} del libro registro: {} facturas ({})", formato, facturas.size(), rango);
    }

    /** Escapa un campo CSV segun RFC 4180 y evita nulos. */
    private String campo(String valor) {
        if (valor == null) {
            return "";
        }
        if (valor.indexOf(SEPARADOR) >= 0 || valor.indexOf('"') >= 0 || valor.indexOf('\n') >= 0) {
            return '"' + valor.replace("\"", "\"\"") + '"';
        }
        return valor;
    }

    /** Decimal con coma, que es lo que espera una hoja de calculo en espanol. */
    private String decimal(java.math.BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
    }
}
