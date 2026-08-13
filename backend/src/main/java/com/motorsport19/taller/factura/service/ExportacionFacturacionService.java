package com.motorsport19.taller.factura.service;

import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.configuracion.service.ConfiguracionTallerService;
import com.motorsport19.taller.documento.ArmadorDocumento;
import com.motorsport19.taller.documento.GeneradorPdfDocumento;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    /**
     * Tope de facturas por ZIP.
     *
     * <p>Cada PDF se dibuja entero en memoria, asi que una peticion con el libro
     * completo dejaria la API sin aire mientras dura. Para eso estan el CSV y el
     * JSON, que no generan documentos. Cincuenta cubre de sobra el caso real:
     * mandar un grupo concreto de facturas a alguien.
     */
    private static final int MAXIMO_FACTURAS_ZIP = 50;

    private final FacturaRepository facturaRepository;
    private final RegistroEventosService registroEventos;
    private final ObjectMapper objectMapper;
    private final GeneradorPdfDocumento generadorPdf;
    private final ArmadorDocumento armador;
    private final ConfiguracionTallerService configuracion;

    public ExportacionFacturacionService(FacturaRepository facturaRepository,
                                         RegistroEventosService registroEventos,
                                         ObjectMapper objectMapper,
                                         GeneradorPdfDocumento generadorPdf,
                                         ArmadorDocumento armador,
                                         ConfiguracionTallerService configuracion) {
        this.facturaRepository = facturaRepository;
        this.registroEventos = registroEventos;
        this.objectMapper = objectMapper;
        this.generadorPdf = generadorPdf;
        this.armador = armador;
        this.configuracion = configuracion;
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

    /**
     * Los PDF de varias facturas dentro de un ZIP.
     *
     * <p>Sirve para lo de todos los meses: mandarle a un cliente, a un seguro o a
     * la gestoria un puñado de facturas concretas sin abrirlas y guardarlas de
     * una en una.
     *
     * <p>Cada PDF se regenera aqui y ahora, igual que al abrir una suelta. No hay
     * PDF guardados en ninguna parte: el documento que vale es la fila de la base
     * de datos, y como todos sus datos estan congelados, regenerarlo da siempre
     * exactamente el mismo papel.
     *
     * <p>Las facturas van en el orden del registro, no en el que llegaron los
     * identificadores: dentro del ZIP salen ordenadas como en el libro.
     *
     * @param ids identificadores de las facturas pedidas; se ignoran repetidos
     * @return el ZIP y los nombres que lleva dentro
     */
    @Transactional
    public byte[] exportarPdfsEnZip(List<Long> ids, Long usuarioId) {
        if (ids == null || ids.isEmpty()) {
            throw new ReglaNegocioException("No se ha seleccionado ninguna factura.");
        }
        List<Long> unicos = ids.stream().distinct().toList();
        if (unicos.size() > MAXIMO_FACTURAS_ZIP) {
            throw new ReglaNegocioException(
                    ("Se han pedido %d facturas y el maximo por descarga son %d. "
                     + "Para el libro entero use la exportacion CSV o JSON.")
                            .formatted(unicos.size(), MAXIMO_FACTURAS_ZIP));
        }

        List<Factura> facturas = facturaRepository.findAllById(unicos).stream()
                .sorted(java.util.Comparator.comparing(Factura::getNumeroRegistro))
                .toList();

        if (facturas.size() < unicos.size()) {
            // Mejor negarse que entregar un ZIP al que le falta una factura sin
            // decirlo: quien lo descarga lo reenvia sin contarlas.
            throw new RecursoNoEncontradoException(
                    "Alguna de las facturas seleccionadas ya no existe. Actualice la pantalla.");
        }

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ConfiguracionTaller taller = configuracion.obligatoria();

        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            for (Factura f : facturas) {
                byte[] pdf = generadorPdf.generar(armador.factura(f, f.getLineas(), taller));

                zip.putNextEntry(new ZipEntry(nombrePdf(f)));
                zip.write(pdf);
                zip.closeEntry();

                registroEventos.anotar(f, TipoEventoFactura.GENERACION_PDF, usuarioId,
                        "Generacion del PDF de la factura %s en una descarga agrupada"
                                .formatted(f.getNumeroCompleto()),
                        null, null);
            }
        } catch (IOException e) {
            // A memoria; si esto falla, no es un problema de disco ni de red.
            throw new IllegalStateException("No se ha podido armar el ZIP de facturas.", e);
        }

        registroEventos.anotar(null, TipoEventoFactura.EXPORTACION, usuarioId,
                "Descarga agrupada de %d facturas en PDF".formatted(facturas.size()),
                """
                {"formato":"ZIP","facturas":%d}""".formatted(facturas.size()),
                null);

        log.info("Descarga agrupada de {} facturas en PDF", facturas.size());
        return salida.toByteArray();
    }

    /** {@code factura-A-2026-000007.pdf}: la barra del numero no vale en un nombre de fichero. */
    private static String nombrePdf(Factura factura) {
        return "factura-%s.pdf".formatted(factura.getNumeroCompleto().replace('/', '-'));
    }

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
