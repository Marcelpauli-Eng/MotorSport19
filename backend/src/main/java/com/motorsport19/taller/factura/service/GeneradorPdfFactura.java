package com.motorsport19.taller.factura.service;

import com.motorsport19.taller.factura.domain.DesgloseIvaFactura;
import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.LineaFactura;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generacion del PDF de una factura.
 *
 * <p>Se dibuja a mano con PDFBox en vez de usar una plantilla: la factura tiene
 * que reflejar exactamente lo que se sello (numero, totales, huella) y aqui no
 * hay ninguna capa intermedia que pueda reinterpretar los datos.
 *
 * <p>El PDF es un reflejo de la factura, no la factura. El documento que vale es
 * la fila de la base de datos; este fichero se puede regenerar tantas veces como
 * haga falta y siempre saldra identico.
 */
@Component
public class GeneradorPdfFactura {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Los importes de la factura se escriben siempre en formato espanol. */
    private static final java.util.Locale ESPANA = java.util.Locale.of("es", "ES");

    private static final float MARGEN = 45f;
    private static final float ANCHO_UTIL = PDRectangle.A4.getWidth() - 2 * MARGEN;

    private static final Color GRIS_TEXTO = new Color(70, 70, 70);
    private static final Color GRIS_LINEA = new Color(200, 200, 200);
    private static final Color FONDO_CABECERA = new Color(238, 238, 238);

    // Columnas de la tabla de lineas, como desplazamiento desde el margen.
    private static final float COL_DESCRIPCION = 0f;
    private static final float COL_CANTIDAD = 268f;
    private static final float COL_PRECIO = 330f;
    private static final float COL_DTO = 392f;
    private static final float COL_IVA = 432f;
    private static final float COL_TOTAL = 478f;

    private final PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /**
     * Genera el PDF completo de la factura.
     *
     * @param lineas   lineas ya cargadas de la factura
     * @param desglose desglose de IVA ya cargado
     */
    public byte[] generar(Factura factura, List<LineaFactura> lineas, List<DesgloseIvaFactura> desglose) {
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);

            try (PDPageContentStream lienzo = new PDPageContentStream(documento, pagina)) {
                float y = pagina.getMediaBox().getHeight() - MARGEN;

                y = dibujarCabecera(lienzo, factura, y);
                y = dibujarPartes(lienzo, factura, y - 18);
                y = dibujarVehiculo(lienzo, factura, y - 14);
                y = dibujarLineas(lienzo, lineas, y - 18);
                y = dibujarTotales(lienzo, factura, desglose, y - 14);
                dibujarSelloYQr(documento, lienzo, factura, y - 24);
            }

            documento.save(salida);
            return salida.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se ha podido generar el PDF de la factura " + factura.numeroVisible(), e);
        }
    }

    // ------------------------------------------------------------------

    private float dibujarCabecera(PDPageContentStream lienzo, Factura factura, float y) throws IOException {
        var emisor = factura.getEmisor();

        texto(lienzo, negrita, 15, MARGEN, y, emisor.getNombre());
        texto(lienzo, normal, 9, MARGEN, y - 14, "NIF: " + emisor.getNif(), GRIS_TEXTO);
        texto(lienzo, normal, 9, MARGEN, y - 25, emisor.getDireccion(), GRIS_TEXTO);
        texto(lienzo, normal, 9, MARGEN, y - 36,
                "%s %s (%s)".formatted(emisor.getCodigoPostal(), emisor.getCiudad(), emisor.getProvincia()),
                GRIS_TEXTO);

        // Bloque de identificacion de la factura, alineado a la derecha.
        String titulo = factura.esRectificativa() ? "FACTURA RECTIFICATIVA" : "FACTURA";
        textoDerecha(lienzo, negrita, 16, MARGEN + ANCHO_UTIL, y, titulo);
        textoDerecha(lienzo, negrita, 12, MARGEN + ANCHO_UTIL, y - 20, factura.numeroVisible());
        textoDerecha(lienzo, normal, 9, MARGEN + ANCHO_UTIL, y - 34,
                "Fecha: " + FECHA.format(factura.getFechaEmision()), GRIS_TEXTO);

        if (factura.esRectificativa() && factura.getFacturaRectificada() != null) {
            textoDerecha(lienzo, normal, 9, MARGEN + ANCHO_UTIL, y - 45,
                    "Rectifica a: " + factura.getFacturaRectificada().numeroVisible(), GRIS_TEXTO);
            y -= 11;
        }

        linea(lienzo, MARGEN, y - 50, MARGEN + ANCHO_UTIL, y - 50);
        return y - 50;
    }

    private float dibujarPartes(PDPageContentStream lienzo, Factura factura, float y) throws IOException {
        var receptor = factura.getDatosReceptor();

        texto(lienzo, negrita, 9, MARGEN, y, "CLIENTE");
        texto(lienzo, normal, 10, MARGEN, y - 14, receptor.getNombre());
        texto(lienzo, normal, 9, MARGEN, y - 26, "NIF/CIF: " + receptor.getNif(), GRIS_TEXTO);
        texto(lienzo, normal, 9, MARGEN, y - 37, receptor.getDireccion(), GRIS_TEXTO);
        texto(lienzo, normal, 9, MARGEN, y - 48,
                "%s %s (%s)".formatted(receptor.getCodigoPostal(), receptor.getCiudad(),
                        receptor.getProvincia()), GRIS_TEXTO);
        return y - 48;
    }

    private float dibujarVehiculo(PDPageContentStream lienzo, Factura factura, float y) throws IOException {
        if (factura.getMatricula() == null && factura.getCodigoOt() == null) {
            return y;
        }
        StringBuilder linea = new StringBuilder();
        if (factura.getMatricula() != null) {
            linea.append("Vehiculo: ").append(factura.getDescripcionVehiculo())
                    .append("  -  Matricula: ").append(factura.getMatricula());
        }
        if (factura.getCodigoOt() != null) {
            if (!linea.isEmpty()) {
                linea.append("  -  ");
            }
            linea.append("Orden de trabajo: ").append(factura.getCodigoOt());
        }
        texto(lienzo, normal, 9, MARGEN, y, linea.toString(), GRIS_TEXTO);
        return y - 6;
    }

    private float dibujarLineas(PDPageContentStream lienzo, List<LineaFactura> lineas, float y)
            throws IOException {
        // Cabecera de la tabla con fondo gris.
        lienzo.setNonStrokingColor(FONDO_CABECERA);
        lienzo.addRect(MARGEN, y - 14, ANCHO_UTIL, 18);
        lienzo.fill();
        lienzo.setNonStrokingColor(Color.BLACK);

        texto(lienzo, negrita, 8, MARGEN + COL_DESCRIPCION + 3, y - 9, "DESCRIPCION");
        textoDerecha(lienzo, negrita, 8, MARGEN + COL_CANTIDAD + 45, y - 9, "CANT.");
        textoDerecha(lienzo, negrita, 8, MARGEN + COL_PRECIO + 50, y - 9, "PRECIO");
        textoDerecha(lienzo, negrita, 8, MARGEN + COL_DTO + 30, y - 9, "DTO.");
        textoDerecha(lienzo, negrita, 8, MARGEN + COL_IVA + 32, y - 9, "IVA");
        textoDerecha(lienzo, negrita, 8, MARGEN + ANCHO_UTIL - 3, y - 9, "IMPORTE");

        float fila = y - 28;
        for (LineaFactura l : lineas) {
            String descripcion = l.getPiezaSku() != null
                    ? "%s  [%s]".formatted(l.getDescripcion(), l.getPiezaSku())
                    : l.getDescripcion();

            texto(lienzo, normal, 9, MARGEN + COL_DESCRIPCION + 3, fila, recortar(descripcion, 52));
            textoDerecha(lienzo, normal, 9, MARGEN + COL_CANTIDAD + 45, fila, cantidad(l.getCantidad()));
            textoDerecha(lienzo, normal, 9, MARGEN + COL_PRECIO + 50, fila, euros(l.getPrecioUnitario()));
            textoDerecha(lienzo, normal, 9, MARGEN + COL_DTO + 30, fila,
                    l.getDescuentoPct().signum() == 0 ? "-" : porcentaje(l.getDescuentoPct()));
            textoDerecha(lienzo, normal, 9, MARGEN + COL_IVA + 32, fila, porcentaje(l.getPorcentajeIva()));
            textoDerecha(lienzo, normal, 9, MARGEN + ANCHO_UTIL - 3, fila,
                    euros(l.importes().baseImponible()));

            fila -= 15;
        }

        linea(lienzo, MARGEN, fila + 5, MARGEN + ANCHO_UTIL, fila + 5);
        return fila;
    }

    private float dibujarTotales(PDPageContentStream lienzo, Factura factura,
                                 List<DesgloseIvaFactura> desglose, float y) throws IOException {
        float xEtiqueta = MARGEN + ANCHO_UTIL - 170;
        float xValor = MARGEN + ANCHO_UTIL - 3;

        texto(lienzo, normal, 9, xEtiqueta, y, "Base imponible");
        textoDerecha(lienzo, normal, 9, xValor, y, euros(factura.getBaseImponible()));
        y -= 13;

        for (DesgloseIvaFactura d : desglose) {
            texto(lienzo, normal, 9, xEtiqueta, y,
                    "IVA %s sobre %s".formatted(porcentaje(d.getPorcentajeIva()),
                            euros(d.getBaseImponible())));
            textoDerecha(lienzo, normal, 9, xValor, y, euros(d.getCuotaIva()));
            y -= 13;
        }

        linea(lienzo, xEtiqueta, y + 4, xValor, y + 4);
        y -= 6;

        texto(lienzo, negrita, 12, xEtiqueta, y, "TOTAL");
        textoDerecha(lienzo, negrita, 12, xValor, y, euros(factura.getTotal()));
        return y - 10;
    }

    /**
     * Pie con la huella encadenada y el QR de verificacion.
     *
     * <p>Imprimir la huella no es decorativo: permite comprobar que un PDF que
     * circula por ahi corresponde de verdad a la factura registrada.
     */
    private void dibujarSelloYQr(PDDocument documento, PDPageContentStream lienzo, Factura factura,
                                 float y) throws IOException {
        float yPie = Math.min(y, 150f);

        linea(lienzo, MARGEN, yPie + 10, MARGEN + ANCHO_UTIL, yPie + 10);

        if (factura.esRectificativa() && factura.getMotivoRectificacion() != null) {
            texto(lienzo, negrita, 8, MARGEN, yPie - 2, "Motivo de la rectificacion:");
            texto(lienzo, normal, 8, MARGEN, yPie - 13,
                    recortar(factura.getMotivoRectificacion(), 75), GRIS_TEXTO);
            yPie -= 24;
        }

        texto(lienzo, negrita, 7, MARGEN, yPie - 4, "REGISTRO DE FACTURACION");
        texto(lienzo, normal, 7, MARGEN, yPie - 14,
                "Posicion en el registro: " + factura.getNumeroRegistro(), GRIS_TEXTO);
        texto(lienzo, normal, 6, MARGEN, yPie - 24,
                "Huella (%s): %s".formatted(factura.getAlgoritmoHuella(), factura.getHuella()), GRIS_TEXTO);
        texto(lienzo, normal, 6, MARGEN, yPie - 33,
                "Huella anterior: " + factura.getHuellaAnterior(), GRIS_TEXTO);
        texto(lienzo, normal, 6, MARGEN, yPie - 45,
                "Emitida con %s %s".formatted(factura.getSoftwareNombre(), factura.getSoftwareVersion()),
                GRIS_TEXTO);

        if (factura.getQrContenido() != null && !factura.getQrContenido().isBlank()) {
            byte[] png = GeneradorQr.generarPng(factura.getQrContenido());
            PDImageXObject imagen = PDImageXObject.createFromByteArray(documento, png,
                    "qr-" + factura.numeroVisible());

            float lado = 82f;
            float xQr = MARGEN + ANCHO_UTIL - lado;
            lienzo.drawImage(imagen, xQr, yPie - 48, lado, lado);
            textoDerecha(lienzo, normal, 6, MARGEN + ANCHO_UTIL, yPie - 57, "Verifique esta factura",
                    GRIS_TEXTO);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades de dibujo
    // ------------------------------------------------------------------

    private void texto(PDPageContentStream lienzo, PDFont fuente, float tamano, float x, float y,
                       String valor) throws IOException {
        texto(lienzo, fuente, tamano, x, y, valor, Color.BLACK);
    }

    private void texto(PDPageContentStream lienzo, PDFont fuente, float tamano, float x, float y,
                       String valor, Color color) throws IOException {
        if (valor == null) {
            return;
        }
        lienzo.setNonStrokingColor(color);
        lienzo.beginText();
        lienzo.setFont(fuente, tamano);
        lienzo.newLineAtOffset(x, y);
        lienzo.showText(saneado(valor));
        lienzo.endText();
        lienzo.setNonStrokingColor(Color.BLACK);
    }

    private void textoDerecha(PDPageContentStream lienzo, PDFont fuente, float tamano, float xDerecha,
                              float y, String valor) throws IOException {
        textoDerecha(lienzo, fuente, tamano, xDerecha, y, valor, Color.BLACK);
    }

    private void textoDerecha(PDPageContentStream lienzo, PDFont fuente, float tamano, float xDerecha,
                              float y, String valor, Color color) throws IOException {
        if (valor == null) {
            return;
        }
        String limpio = saneado(valor);
        float ancho = fuente.getStringWidth(limpio) / 1000 * tamano;
        texto(lienzo, fuente, tamano, xDerecha - ancho, y, limpio, color);
    }

    private void linea(PDPageContentStream lienzo, float x1, float y1, float x2, float y2)
            throws IOException {
        lienzo.setStrokingColor(GRIS_LINEA);
        lienzo.setLineWidth(0.6f);
        lienzo.moveTo(x1, y1);
        lienzo.lineTo(x2, y2);
        lienzo.stroke();
        lienzo.setStrokingColor(Color.BLACK);
    }

    /**
     * Sustituye lo que las fuentes estandar de PDF no saben dibujar.
     *
     * <p>Helvetica usa WinAnsi, que cubre los acentos y la enye del castellano
     * pero no todo Unicode. Un caracter raro en el nombre de un cliente no puede
     * tumbar la generacion de su factura.
     */
    private String saneado(String valor) {
        StringBuilder limpio = new StringBuilder(valor.length());
        for (char c : valor.toCharArray()) {
            limpio.append(c < 256 || "€".indexOf(c) >= 0 ? c : '?');
        }
        return limpio.toString();
    }

    private String recortar(String valor, int maximo) {
        if (valor == null) {
            return "";
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo - 1) + "...";
    }

    /**
     * Importe en formato espanol: 1.234,56 EUR.
     *
     * <p>El locale va FIJADO a proposito. Con el locale por defecto de la JVM, la
     * misma factura saldria "1.234,56" en un portatil espanol y "1,234.56" en un
     * contenedor con locale C, y el papel que recibe el cliente no puede depender
     * de como este configurado el servidor.
     */
    private String euros(BigDecimal valor) {
        return String.format(ESPANA, "%,.2f EUR", valor);
    }

    private String porcentaje(BigDecimal valor) {
        return "%s%%".formatted(valor.stripTrailingZeros().toPlainString().replace('.', ','));
    }

    private String cantidad(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
