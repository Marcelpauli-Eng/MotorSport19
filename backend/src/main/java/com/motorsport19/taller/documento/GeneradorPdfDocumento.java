package com.motorsport19.taller.documento;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Imprime presupuestos y facturas con el formato de siempre del taller.
 *
 * <p>La maqueta reproduce el documento que 19 Racing Motorsport venia usando:
 * mismas bandas, mismas columnas y mismos rotulos. Las coordenadas no estan
 * puestas a ojo, salen del propio PDF de referencia —margen de 28,35 pt, rayas
 * de 0,75 y cabeceras de tabla a 6,75— para que al ponerlos uno al lado del
 * otro cuadren.
 *
 * <p>Que el formato se respete no es capricho: el taller lleva años entregando
 * este papel, sus clientes lo reconocen y su gestoria sabe donde mirar. Cambiar
 * la maqueta al cambiar de programa obliga a todo el mundo a reaprender algo
 * que ya funcionaba.
 *
 * <p>El eje Y de PDFBox crece hacia arriba, asi que las Y grandes son la parte
 * alta de la hoja. Las constantes van nombradas por la banda a la que
 * pertenecen y en el mismo orden en que se leen en el papel.
 */
@Component
public class GeneradorPdfDocumento {

    // ----- Hoja -----
    private static final float IZQ = 28.35f;
    private static final float DER = 563.72f;

    // ----- Banda: datos del documento -----
    private static final float DOC_ARRIBA = 709.8f;
    private static final float DOC_MEDIO = 699.8f;
    private static final float DOC_ABAJO = 689.8f;
    private static final float[] DOC_COLS = {28.7f, 119.6f, 192.7f, 246.0f, 300.1f};

    // ----- Banda: CIF, numero de cliente y telefono -----
    private static final float CIF_ARRIBA = 678.6f;
    private static final float CIF_ABAJO = 664.0f;
    private static final float[] CIF_COLS = {28.7f, 125.1f, 154.2f, 301.6f};

    // ----- Banda: vehiculo -----
    private static final float VEH_ARRIBA = 644.6f;
    private static final float VEH_MEDIO = 633.8f;
    private static final float VEH_ABAJO = 614.0f;
    private static final float[] VEH_COLS = {28.7f, 112.6f, 261.9f, 490.7f, 563.72f};

    // ----- Banda: lineas -----
    private static final float LIN_ARRIBA = 616.2f;
    private static final float LIN_CABECERA = 600.9f;
    private static final float LIN_ABAJO = 355.4f;
    private static final float[] LIN_COLS = {28.7f, 112.6f, 327.3f, 381.8f, 436.2f, 490.7f, 563.72f};

    // ----- Banda: totales -----
    private static final float TOT_ARRIBA = 355.4f;
    private static final float TOT_CABECERA = 343.6f;
    private static final float TOT_VALORES = 300.5f;
    private static final float TOT_TOTALES = 289.0f;
    private static final float TOT_FIRMAS = 277.5f;
    private static final float TOT_ABAJO = 214.4f;
    private static final float[] TOT_COLS =
            {28.7f, 98.7f, 168.6f, 231.7f, 314.3f, 375.1f, 449.6f, 501.2f, 563.72f};

    private static final float GROSOR = 0.75f;
    private static final float ALTO_FILA = 11.34f;

    /** Separacion entre los renglones del bloque del taller, en la cabecera. */
    private static final float INTERLINEA_EMISOR = 8.5f;

    private static final Locale ESPANA = Locale.of("es", "ES");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generar(DocumentoImprimible doc) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            pdf.addPage(pagina);

            new Maqueta(pdf).dibujar(pdf, pagina, doc);

            pdf.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se ha podido generar el PDF del documento.");
        }
    }

    /**
     * El documento a medio dibujar, con sus dos fuentes.
     *
     * <p>Va aparte del componente porque las fuentes incrustadas pertenecen a un
     * PDF concreto y no se pueden compartir entre documentos: el generador es un
     * bean unico que atiende a varias peticiones a la vez, y guardarlas en el
     * mezclaria los tipos de un documento con los de otro.
     */
    private static final class Maqueta {

        private final PDFont normal;
        private final PDFont negrita;

        private Maqueta(PDDocument pdf) throws IOException {
            this.normal = cargar(pdf, "LiberationSans-Regular.ttf", Standard14Fonts.FontName.HELVETICA);
            this.negrita = cargar(pdf, "LiberationSans-Bold.ttf", Standard14Fonts.FontName.HELVETICA_BOLD);
        }

        /**
         * La letra del documento, incrustada dentro del PDF.
         *
         * <p>Incrustarla es lo que hace que el papel se vea igual en el movil
         * del cliente que en la impresora del taller: si el PDF solo nombra la
         * fuente, cada visor pone la que se le parece y se pierden hasta las
         * negritas.
         *
         * <p>Si el fichero no estuviera, se tira de las fuentes de serie del
         * formato. Se ve casi igual —miden lo mismo— y es mejor que quedarse sin
         * documento por una tipografia.
         */
        private static PDFont cargar(PDDocument pdf, String fichero, Standard14Fonts.FontName respaldo)
                throws IOException {
            try (InputStream ttf = Maqueta.class.getResourceAsStream("/plantilla/" + fichero)) {
                return ttf == null ? new PDType1Font(respaldo) : PDType0Font.load(pdf, ttf, true);
            }
        }

        private void dibujar(PDDocument pdf, PDPage pagina, DocumentoImprimible doc) throws IOException {
            try (PDPageContentStream l = new PDPageContentStream(pdf, pagina)) {
                l.setLineWidth(GROSOR);
                dibujarEncabezado(pdf, l, doc);
                dibujarDatosDocumento(l, doc);
                dibujarVehiculo(l, doc);
                dibujarLineas(l, doc);
                dibujarTotales(l, doc);
                dibujarPie(l, doc);
            }
        }

        // ==================================================================
        // Cabecera: datos del taller a la izquierda, logo a la derecha
        // ==================================================================

        private void dibujarEncabezado(PDDocument pdf, PDPageContentStream l, DocumentoImprimible doc)
                throws IOException {
            var e = doc.emisor();

            texto(l, negrita, 12, IZQ, 809.26f, e.razonSocial());

            // Del bloque del taller solo va en negrita el nombre; el resto son
            // renglones corridos a 8,5 de interlinea, que es lo que deja sitio
            // justo para que el rotulo del documento entre debajo sin pisarlo.
            float y = 779.4f;
            texto(l, negrita, 7.5f, IZQ, y, e.razonSocial());
            y -= INTERLINEA_EMISOR;
            texto(l, normal, 7.5f, IZQ, y, e.direccion());
            y -= INTERLINEA_EMISOR;
            texto(l, normal, 7.5f, IZQ, y, e.poblacion());
            y -= INTERLINEA_EMISOR;
            texto(l, normal, 7.5f, IZQ, y, "CIF: " + e.nif());
            y -= INTERLINEA_EMISOR;
            if (e.telefono() != null) {
                texto(l, normal, 7.5f, IZQ, y, "TELEFONO: " + e.telefono());
                y -= INTERLINEA_EMISOR;
            }
            if (e.email() != null) {
                texto(l, normal, 7.5f, IZQ, y, "CORREO: " + e.email());
            }

            // El logo se escala conservando su proporcion: viene de un PNG de
            // 1600 px de ancho y hay que llevarlo a los 241 pt del hueco.
            try (InputStream img = getClass().getResourceAsStream("/plantilla/cabecera.png")) {
                if (img != null) {
                    PDImageXObject logo = PDImageXObject.createFromByteArray(pdf, img.readAllBytes(), "cabecera");
                    float ancho = 241f;
                    float alto = ancho * logo.getHeight() / logo.getWidth();
                    l.drawImage(logo, 564.1f - ancho, 736.3f, ancho, alto);
                }
            }

            // Rotulo del documento a la izquierda y destinatario a la derecha, los
            // dos a la altura en que arranca la parte rellenable de la hoja.
            texto(l, negrita, 10.5f, IZQ, 714.27f, doc.titulo());

            var c = doc.cliente();
            texto(l, negrita, 7.5f, 325.4f, 717.03f, c.nombre());
            if (c.poblacion() != null) {
                texto(l, normal, 7.5f, 325.4f, 708.41f, c.poblacion());
            }
        }

        // ==================================================================
        // Banda de datos del documento
        // ==================================================================

        private void dibujarDatosDocumento(PDPageContentStream l, DocumentoImprimible doc) throws IOException {
            rejilla(l, DOC_COLS, DOC_ARRIBA, DOC_ABAJO, DOC_MEDIO);

            String[] rotulos = {"DOCUMENTO Nº", "SERIE", "FECHA", "SINIESTRO"};
            for (int i = 0; i < rotulos.length; i++) {
                centrado(l, negrita, 6.75f, DOC_COLS[i], DOC_COLS[i + 1], DOC_ARRIBA - 7.75f, rotulos[i]);
            }

            String[] valores = {
                    doc.numeroDocumento(), doc.serie(),
                    doc.fecha().format(FECHA), doc.siniestro(),
            };
            for (int i = 0; i < valores.length; i++) {
                centrado(l, normal, 6.75f, DOC_COLS[i], DOC_COLS[i + 1], DOC_MEDIO - 7, valores[i]);
            }

            // Fila de CIF, numero de cliente y telefono. Los rotulos van en negrita
            // y el dato en redonda, como en el documento de siempre.
            rejilla(l, CIF_COLS, CIF_ARRIBA, CIF_ABAJO);
            var c = doc.cliente();
            float yFila = CIF_ABAJO + 4.68f;
            etiquetaYValor(l, 7.5f, CIF_COLS[0] + 3, yFila, "CIF:", valorOGuion(c.nif()));
            centrado(l, normal, 7.5f, CIF_COLS[1], CIF_COLS[2], yFila, valorOVacio(c.numero()));
            etiquetaYValor(l, 7.5f, CIF_COLS[2] + 2.63f, yFila, "TELEFONO:", valorOGuion(c.telefono()));

            // Forma de pago, validez y numero de pagina, sin recuadro.
            float y = 651.84f;
            texto(l, negrita, 7.5f, IZQ, y, "FORMA PAGO: " + doc.formaPago());
            if (doc.fechaValidez() != null) {
                etiquetaYValor(l, 7.5f, 325.98f, y, "FECHA VALIDEZ:", doc.fechaValidez().format(FECHA));
            }
            texto(l, normal, 7.5f, 513.06f, y, "PAGINA Nº 1/1");
        }

        /** Rotulo en negrita y, pegado detras, su dato en redonda. */
        private void etiquetaYValor(PDPageContentStream l, float tamano, float x, float y,
                                    String etiqueta, String valor) throws IOException {
            texto(l, negrita, tamano, x, y, etiqueta);
            texto(l, normal, tamano, x + anchoDe(negrita, tamano, etiqueta), y, " " + valor);
        }

        // ==================================================================
        // Banda del vehiculo
        // ==================================================================

        private void dibujarVehiculo(PDPageContentStream l, DocumentoImprimible doc) throws IOException {
            rejilla(l, VEH_COLS, VEH_ARRIBA, VEH_ABAJO, VEH_MEDIO);

            String[] rotulos = {"MATRÍCULA", "BASTIDOR", "MODELO", "KM"};
            for (int i = 0; i < rotulos.length; i++) {
                centrado(l, negrita, 6.75f, VEH_COLS[i], VEH_COLS[i + 1], VEH_ARRIBA - 7.74f, rotulos[i]);
            }

            var v = doc.vehiculo();
            String[] valores = {
                    valorOVacio(v.matricula()), valorOVacio(v.bastidor()), valorOVacio(v.modelo()),
                    v.km() == null ? "0" : entero(v.km()),
            };
            for (int i = 0; i < valores.length; i++) {
                centrado(l, normal, 7.5f, VEH_COLS[i], VEH_COLS[i + 1], VEH_MEDIO - 12.51f, valores[i]);
            }
        }

        // ==================================================================
        // Lineas
        // ==================================================================

        private void dibujarLineas(PDPageContentStream l, DocumentoImprimible doc) throws IOException {
            rejilla(l, LIN_COLS, LIN_ARRIBA, LIN_ABAJO, LIN_CABECERA);

            String[] rotulos = {"Código", "Descripción", "Cantidad", "Precio", "Dto.", "Total"};
            for (int i = 0; i < rotulos.length; i++) {
                centrado(l, negrita, 7.5f, LIN_COLS[i], LIN_COLS[i + 1], LIN_ARRIBA - 10.63f, rotulos[i]);
            }

            // El primer renglon no arranca pegado a la cabecera: el documento deja
            // un hueco de un poco mas de una fila antes de empezar a listar.
            float y = LIN_CABECERA - 23.24f;
            for (DocumentoImprimible.Linea li : doc.lineas()) {
                if (y < LIN_ABAJO + 6) {
                    break;
                }

                if (li.cabecera()) {
                    texto(l, negrita, 7.5f, LIN_COLS[1] + 2.65f, y, li.descripcion());
                    if (li.total() != null) {
                        derecha(l, negrita, 7.5f, DER - 3, y, cantidad(li.total()));
                    }
                } else {
                    texto(l, normal, 7.5f, LIN_COLS[0] + 3.4f, y, valorOVacio(li.codigo()));
                    texto(l, normal, 7.5f, LIN_COLS[1] + 3.4f, y,
                            recortar(li.descripcion(), LIN_COLS[2] - LIN_COLS[1] - 7, 7.5f));
                    derecha(l, normal, 7.5f, LIN_COLS[3] - 3.5f, y, cantidad(li.cantidad()));
                    derecha(l, normal, 7.5f, LIN_COLS[4] - 3.2f, y, euros(li.precio()));
                    if (li.descuentoPct() != null && li.descuentoPct().signum() > 0) {
                        derecha(l, normal, 7.5f, LIN_COLS[5] - 3.2f, y, porcentaje(li.descuentoPct()));
                    }
                    derecha(l, normal, 7.5f, DER - 3, y, cantidad(li.total()));
                }
                y -= ALTO_FILA;
            }
        }

        // ==================================================================
        // Totales y firmas
        // ==================================================================

        private void dibujarTotales(PDPageContentStream l, DocumentoImprimible doc) throws IOException {
            var t = doc.totales();

            // Cabecera de la banda y fila de valores.
            rejilla(l, TOT_COLS, TOT_ARRIBA, TOT_VALORES, TOT_CABECERA);

            String[] rotulos = {"IMPORTE", "DTO. LINEA", "TASAS", "DESCUENTOS", "PORTES",
                    "BASE IMPONIBLE", "% IMP", "IMPUESTOS"};
            for (int i = 0; i < rotulos.length; i++) {
                centrado(l, negrita, 6.75f, TOT_COLS[i], TOT_COLS[i + 1], TOT_ARRIBA - 8.43f, rotulos[i]);
            }

            // Esta fila lleva mas decimales que el resto del papel —tres en los
            // importes y cuatro en la base y la cuota— porque es la que cuadra la
            // gestoria: los redondeos se hacen al final, en la fila de TOTALES.
            String[] valores = {
                    cantidadEuros(t.importe()),
                    t.descuentoLinea().signum() == 0 ? "" : cantidadEuros(t.descuentoLinea()),
                    cantidadEuros(t.tasas()),
                    t.descuentos().signum() == 0 ? "" : cantidadEuros(t.descuentos()),
                    cantidadEuros(t.portes()),
                    euros4(t.baseImponible()),
                    porcentaje3(t.porcentajeIva()),
                    euros4(t.impuestos()),
            };
            for (int i = 0; i < valores.length; i++) {
                centrado(l, normal, 6.75f, TOT_COLS[i], TOT_COLS[i + 1], TOT_CABECERA - 8.14f, valores[i]);
            }

            // Fila de TOTALES, con el IRPF a la izquierda. No lleva todas las
            // verticales: el rotulo ocupa las tres primeras casillas y el IRPF las
            // dos siguientes, asi que sus separadores no se trazan.
            float yTotales = TOT_TOTALES + 3.45f;
            linea(l, IZQ, TOT_TOTALES, DER, TOT_TOTALES);
            linea(l, IZQ, TOT_VALORES, DER, TOT_VALORES);
            for (int i : new int[] {0, 2, 4, 5, 6, 7, 8}) {
                linea(l, TOT_COLS[i], TOT_VALORES, TOT_COLS[i], TOT_TOTALES);
            }
            derecha(l, negrita, 6.75f, TOT_COLS[2] - 1.8f, yTotales, "TOTALES");
            etiquetaYValor(l, 6.75f, TOT_COLS[2] + 41.9f, yTotales,
                    "IRPF (" + porcentaje(t.irpfPorcentaje()) + "):", euros(t.irpf()));
            centrado(l, negrita, 6.75f, TOT_COLS[5], TOT_COLS[6], yTotales, euros(t.baseImponible()));
            centrado(l, negrita, 6.75f, TOT_COLS[7], TOT_COLS[8], yTotales, euros(t.impuestos()));

            // Rotulos de las tres cajas de abajo, cada uno en su casilla.
            linea(l, IZQ, TOT_FIRMAS, DER, TOT_FIRMAS);
            for (int i : new int[] {0, 4, 6, 8}) {
                linea(l, TOT_COLS[i], TOT_TOTALES, TOT_COLS[i], TOT_FIRMAS);
            }
            texto(l, negrita, 6.75f, IZQ + 2.25f, TOT_FIRMAS + 3.43f, "OBSERVACIONES DEL DOCUMENTO");
            centrado(l, negrita, 6.75f, TOT_COLS[4], TOT_COLS[6], TOT_FIRMAS + 3.43f, "FIRMA DE LA EMPRESA");
            centrado(l, negrita, 6.75f, TOT_COLS[6], TOT_COLS[8], TOT_FIRMAS + 3.43f, "FIRMA DEL CLIENTE");

            // Marco de las cajas de firma.
            linea(l, IZQ, TOT_ABAJO, DER, TOT_ABAJO);
            linea(l, IZQ, TOT_FIRMAS, IZQ, TOT_ABAJO);
            linea(l, TOT_COLS[4], TOT_FIRMAS, TOT_COLS[4], TOT_ABAJO);
            linea(l, TOT_COLS[6], TOT_FIRMAS, TOT_COLS[6], TOT_ABAJO);
            linea(l, DER, TOT_FIRMAS, DER, TOT_ABAJO);

            if (doc.observaciones() != null && !doc.observaciones().isBlank()) {
                float y = TOT_FIRMAS - 12;
                for (String renglon : partir(doc.observaciones(), TOT_COLS[4] - IZQ - 8, 7f)) {
                    if (y < TOT_ABAJO + 4) break;
                    texto(l, normal, 7, IZQ + 4, y, renglon);
                    y -= 9;
                }
            }
        }

        private void dibujarPie(PDPageContentStream l, DocumentoImprimible doc) throws IOException {
            var c = doc.cliente();
            var t = doc.totales();

            texto(l, negrita, 6.75f, IZQ + 1.87f, TOT_ABAJO - 15.02f,
                    "CLIENTE: %s CIF: %s TOTAL A PAGAR: %s"
                            .formatted(c.nombre(), valorOGuion(c.nif()), euros(t.total())));

            derecha(l, negrita, 10.5f, DER - 5.15f, TOT_ABAJO - 15.3f,
                    doc.rotuloTotal() + ": " + euros(t.total()));
        }

        // ==================================================================
        // Dibujo
        // ==================================================================

        /**
         * Marco de una banda con sus separadores verticales.
         *
         * <p>{@code reglasInternas} son las horizontales de dentro, que separan la
         * cabecera de los valores. Los verticales se trazan de arriba abajo de toda
         * la banda, que es como esta el documento original.
         */
        private void rejilla(PDPageContentStream l, float[] columnas, float arriba, float abajo,
                             float... reglasInternas) throws IOException {
            linea(l, columnas[0], arriba, columnas[columnas.length - 1], arriba);
            linea(l, columnas[0], abajo, columnas[columnas.length - 1], abajo);
            for (float y : reglasInternas) {
                linea(l, columnas[0], y, columnas[columnas.length - 1], y);
            }
            for (float x : columnas) {
                linea(l, x, arriba, x, abajo);
            }
        }

        private void linea(PDPageContentStream l, float x1, float y1, float x2, float y2) throws IOException {
            l.setStrokingColor(Color.BLACK);
            l.moveTo(x1, y1);
            l.lineTo(x2, y2);
            l.stroke();
        }

        private void texto(PDPageContentStream l, PDFont fuente, float tamano, float x, float y, String valor)
                throws IOException {
            if (valor == null || valor.isEmpty()) return;
            l.beginText();
            l.setFont(fuente, tamano);
            l.setNonStrokingColor(Color.BLACK);
            l.newLineAtOffset(x, y);
            l.showText(saneado(valor));
            l.endText();
        }

        private void derecha(PDPageContentStream l, PDFont fuente, float tamano, float xDerecha, float y,
                             String valor) throws IOException {
            if (valor == null || valor.isEmpty()) return;
            texto(l, fuente, tamano, xDerecha - anchoDe(fuente, tamano, valor), y, valor);
        }

        private void centrado(PDPageContentStream l, PDFont fuente, float tamano, float xIzq, float xDer,
                              float y, String valor) throws IOException {
            if (valor == null || valor.isEmpty()) return;
            float x = xIzq + (xDer - xIzq - anchoDe(fuente, tamano, valor)) / 2;
            texto(l, fuente, tamano, x, y, valor);
        }

        private float anchoDe(PDFont fuente, float tamano, String valor) {
            try {
                return fuente.getStringWidth(saneado(valor)) / 1000 * tamano;
            } catch (IOException e) {
                return valor.length() * tamano * 0.5f;
            }
        }

        /** Recorta con puntos suspensivos para que no se salga de su columna. */
        private String recortar(String valor, float anchoDisponible, float tamano) {
            if (valor == null) return "";
            String v = saneado(valor);
            if (anchoDe(normal, tamano, v) <= anchoDisponible) return v;
            while (v.length() > 1 && anchoDe(normal, tamano, v + "...") > anchoDisponible) {
                v = v.substring(0, v.length() - 1);
            }
            return v + "...";
        }

        private List<String> partir(String texto, float ancho, float tamano) {
            java.util.List<String> renglones = new java.util.ArrayList<>();
            StringBuilder actual = new StringBuilder();
            for (String palabra : saneado(texto).split("\\s+")) {
                String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
                if (anchoDe(normal, tamano, prueba) > ancho && !actual.isEmpty()) {
                    renglones.add(actual.toString());
                    actual = new StringBuilder(palabra);
                } else {
                    actual = new StringBuilder(prueba);
                }
            }
            if (!actual.isEmpty()) renglones.add(actual.toString());
            return renglones;
        }

        /**
         * Las fuentes Type1 estandar no llevan mas alla de WinAnsi.
         *
         * <p>Se sustituye lo que no encaja en vez de dejar que reviente al escribir:
         * un PDF que no sale por una comilla tipografica en unas observaciones es
         * mucho peor que uno con la comilla cambiada.
         *
         * <p>El euro se deja pasar aunque caiga fuera de Latin-1: en WinAnsi ocupa
         * el 0x80 y se imprime sin problema. Filtrarlo por rango, como se hacia
         * antes, dejaba todos los importes del documento sin simbolo.
         */
        private String saneado(String valor) {
            if (valor == null) return "";
            return valor
                    .replace('‘', '\'').replace('’', '\'')
                    .replace('“', '"').replace('”', '"')
                    .replace('–', '-').replace('—', '-')
                    .replace('…', '.')
                    .replaceAll("[^\\x20-\\x7E\\xA0-\\xFF€]", "");
        }

        // ==================================================================
        // Formatos
        // ==================================================================

        private String euros(BigDecimal valor) {
            return formato("#,##0.00").format(valor == null ? BigDecimal.ZERO : valor) + "€";
        }

        private String euros4(BigDecimal valor) {
            return formato("#,##0.0000").format(valor == null ? BigDecimal.ZERO : valor) + "€";
        }

        /** Importe a tres decimales, como en la banda de totales. */
        private String cantidadEuros(BigDecimal valor) {
            return cantidad(valor) + "€";
        }

        private String cantidad(BigDecimal valor) {
            return formato("#,##0.000").format(valor == null ? BigDecimal.ZERO : valor);
        }

        private String porcentaje(BigDecimal valor) {
            return formato("#,##0.00").format(valor == null ? BigDecimal.ZERO : valor) + "%";
        }

        private String porcentaje3(BigDecimal valor) {
            return formato("#,##0.000").format(valor == null ? BigDecimal.ZERO : valor) + "%";
        }

        private String entero(Number valor) {
            return formato("#,##0").format(valor);
        }

        private DecimalFormat formato(String patron) {
            return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(ESPANA));
        }

        private String valorOVacio(String valor) {
            return valor == null ? "" : valor;
        }

        private String valorOGuion(String valor) {
            return valor == null || valor.isBlank() ? "-" : valor;
        }
    }
}
