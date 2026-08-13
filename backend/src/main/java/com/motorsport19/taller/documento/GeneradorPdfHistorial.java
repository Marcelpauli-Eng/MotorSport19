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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Imprime la hoja de vida de una moto.
 *
 * <p>Va aparte de {@link GeneradorPdfDocumento} porque son dos documentos con
 * dos problemas distintos. Aquel dibuja un impreso de una hoja con las bandas
 * en coordenadas fijas, copiando el papel de siempre del taller; este es un
 * listado que crece: una moto con quince años de mantenimiento ocupa varias
 * paginas, y las intervenciones tienen que poder partirse por donde toque sin
 * que se corte ninguna por la mitad.
 *
 * <p>Se conserva a proposito el aire del otro documento —misma cabecera, misma
 * letra, mismos grosores— para que quien lo recibe vea que sale del mismo
 * taller.
 *
 * <p>El eje Y de PDFBox crece hacia arriba: las Y grandes son la parte alta.
 */
@Component
public class GeneradorPdfHistorial {

    private static final float IZQ = 28.35f;
    private static final float DER = 563.72f;

    /** Donde arranca el contenido en la primera hoja, bajo la cabecera del taller. */
    private static final float Y_INICIAL_PRIMERA = 690f;
    /** En las siguientes no se repite el bloque del taller, solo un renglon. */
    private static final float Y_INICIAL_SIGUIENTE = 780f;
    /** Por debajo de aqui empieza el pie: nada de contenido. */
    private static final float Y_MINIMA = 70f;

    private static final float GROSOR = 0.75f;
    private static final float INTERLINEA = 10.5f;

    private static final Locale ESPANA = Locale.of("es", "ES");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generar(HistorialImprimible historial) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            new Maqueta(pdf).dibujar(historial);
            pdf.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se ha podido generar el historial en PDF.");
        }
    }

    /**
     * El documento a medio dibujar.
     *
     * <p>Igual que en el generador de facturas, va aparte del componente: las
     * fuentes incrustadas pertenecen a un PDF concreto y el bean atiende a
     * varias peticiones a la vez.
     */
    private static final class Maqueta {

        private final PDDocument pdf;
        private final PDFont normal;
        private final PDFont negrita;

        private PDPageContentStream lienzo;
        private float y;
        private int pagina;
        private int totalPaginas;

        private Maqueta(PDDocument pdf) throws IOException {
            this.pdf = pdf;
            this.normal = cargar(pdf, "LiberationSans-Regular.ttf", Standard14Fonts.FontName.HELVETICA);
            this.negrita = cargar(pdf, "LiberationSans-Bold.ttf", Standard14Fonts.FontName.HELVETICA_BOLD);
        }

        private static PDFont cargar(PDDocument pdf, String fichero, Standard14Fonts.FontName respaldo)
                throws IOException {
            try (InputStream ttf = Maqueta.class.getResourceAsStream("/plantilla/" + fichero)) {
                return ttf == null ? new PDType1Font(respaldo) : PDType0Font.load(pdf, ttf, true);
            }
        }

        private void dibujar(HistorialImprimible h) throws IOException {
            // Se cuentan las paginas por adelantado para poder numerarlas «1 de 3»
            // desde la primera. Sin esto habria que reabrir cada hoja al final.
            this.totalPaginas = contarPaginas(h);

            abrirPagina(h);
            dibujarFichaMoto(h);
            dibujarResumen(h);

            texto(negrita, 9, IZQ, y, "INTERVENCIONES");
            y -= INTERLINEA * 1.4f;

            if (h.intervenciones().isEmpty()) {
                texto(normal, 8, IZQ, y,
                        "Esta moto todavía no tiene ninguna intervención registrada en el taller.");
                y -= INTERLINEA;
            }
            for (HistorialImprimible.Intervencion i : h.intervenciones()) {
                float alto = altoDe(i, h.conImportes());
                if (y - alto < Y_MINIMA) {
                    cerrarPagina(h);
                    abrirPagina(h);
                }
                dibujarIntervencion(i, h.conImportes());
            }

            cerrarPagina(h);
        }

        // ==================================================================
        // Paginacion
        // ==================================================================

        /**
         * Simula el recorrido para saber cuantas hojas van a salir.
         *
         * <p>Recorre exactamente las mismas alturas que {@link #dibujar}, asi que
         * cualquier cambio en el alto de una intervencion hay que hacerlo en
         * {@link #altoDe} y las dos pasadas siguen de acuerdo.
         */
        private int contarPaginas(HistorialImprimible h) {
            int paginas = 1;
            float cursor = Y_INICIAL_PRIMERA - altoFicha(h) - altoResumen() - INTERLINEA * 2.4f;

            for (HistorialImprimible.Intervencion i : h.intervenciones()) {
                float alto = altoDe(i, h.conImportes());
                if (cursor - alto < Y_MINIMA) {
                    paginas++;
                    cursor = Y_INICIAL_SIGUIENTE;
                }
                cursor -= alto;
            }
            return paginas;
        }

        private void abrirPagina(HistorialImprimible h) throws IOException {
            PDPage hoja = new PDPage(PDRectangle.A4);
            pdf.addPage(hoja);
            lienzo = new PDPageContentStream(pdf, hoja);
            lienzo.setLineWidth(GROSOR);
            pagina++;

            if (pagina == 1) {
                dibujarCabecera(h);
                y = Y_INICIAL_PRIMERA;
            } else {
                // En las hojas siguientes basta un renglon de continuidad: quien la
                // tiene en la mano ya sabe de que moto y de que taller le hablan.
                texto(negrita, 9, IZQ, 800f, "HISTORIAL DE SERVICIO");
                derecha(normal, 8, DER, 800f,
                        "%s · %s".formatted(h.vehiculo().matricula(), h.emisor().razonSocial()));
                linea(IZQ, 793f, DER, 793f);
                y = Y_INICIAL_SIGUIENTE;
            }
        }

        private void cerrarPagina(HistorialImprimible h) throws IOException {
            dibujarPie(h);
            lienzo.close();
        }

        // ==================================================================
        // Bandas
        // ==================================================================

        private void dibujarCabecera(HistorialImprimible h) throws IOException {
            var e = h.emisor();

            texto(negrita, 12, IZQ, 809.26f, e.razonSocial());

            float renglon = 779.4f;
            texto(normal, 7.5f, IZQ, renglon, e.direccion());
            renglon -= 8.5f;
            texto(normal, 7.5f, IZQ, renglon, e.poblacion());
            renglon -= 8.5f;
            texto(normal, 7.5f, IZQ, renglon, "CIF: " + e.nif());
            renglon -= 8.5f;
            if (e.telefono() != null) {
                texto(normal, 7.5f, IZQ, renglon, "TELEFONO: " + e.telefono());
                renglon -= 8.5f;
            }
            if (e.email() != null) {
                texto(normal, 7.5f, IZQ, renglon, "CORREO: " + e.email());
            }

            try (InputStream img = getClass().getResourceAsStream("/plantilla/cabecera.png")) {
                if (img != null) {
                    PDImageXObject logo = PDImageXObject.createFromByteArray(pdf, img.readAllBytes(), "cabecera");
                    float ancho = 241f;
                    lienzo.drawImage(logo, 564.1f - ancho, 736.3f,
                            ancho, ancho * logo.getHeight() / logo.getWidth());
                }
            }

            texto(negrita, 14, IZQ, 714.27f, "HISTORIAL DE SERVICIO");
        }

        /** Matricula, moto y bastidor: con lo que se comprueba que el papel es de esa moto. */
        private void dibujarFichaMoto(HistorialImprimible h) throws IOException {
            var v = h.vehiculo();
            float arriba = y;
            float abajo = y - 44f;

            recuadro(arriba, abajo);

            float col1 = IZQ + 8f;
            float col2 = 200f;
            float col3 = 400f;
            float fila1 = arriba - 14f;
            float fila2 = arriba - 32f;

            etiqueta(col1, fila1, "MATRÍCULA", v.matricula(), 11);
            etiqueta(col2, fila1, "MOTO", "%s %s".formatted(valorOVacio(v.marca()), valorOVacio(v.modelo())).trim(), 9);
            etiqueta(col3, fila1, "AÑO", v.anio() == null ? "—" : String.valueOf(v.anio()), 9);

            etiqueta(col1, fila2, "BASTIDOR", valorOGuion(v.bastidor()), 8);
            etiqueta(col2, fila2, "KILÓMETROS", v.kmActual() == null ? "—" : entero(v.kmActual()) + " km", 9);
            etiqueta(col3, fila2, "TITULAR", recortar(valorOGuion(h.propietario()), 150f, 8), 8);

            y = abajo - INTERLINEA * 1.6f;
        }

        private void dibujarResumen(HistorialImprimible h) throws IOException {
            var r = h.resumen();

            texto(negrita, 9, IZQ, y, "RESUMEN");
            y -= INTERLINEA * 1.2f;

            String visitas = r.intervenciones() == 1 ? "1 intervención" : r.intervenciones() + " intervenciones";
            if (r.primeraVisita() != null) {
                visitas += " entre el %s y el %s".formatted(
                        FECHA.format(r.primeraVisita()), FECHA.format(r.ultimaVisita()));
            }
            texto(normal, 8.5f, IZQ, y, visitas);
            y -= INTERLINEA;

            if (r.kmRecorridos() != null) {
                texto(normal, 8.5f, IZQ, y,
                        "Kilómetros recorridos entre la primera y la última visita: %s km"
                                .formatted(entero(r.kmRecorridos())));
                y -= INTERLINEA;
            }
            if (h.conImportes()) {
                texto(normal, 8.5f, IZQ, y,
                        "Invertido en mantenimiento y reparaciones: " + euros(r.totalInvertido()));
                y -= INTERLINEA;
            }
            y -= INTERLINEA * 0.6f;
        }

        /**
         * Una intervencion: su renglon de cabecera y debajo lo que se hizo.
         *
         * <p>Los trabajos y las piezas van en dos listas separadas y rotuladas.
         * Quien compra una moto de segunda mano busca lo segundo: si se cambio la
         * correa, los frenos o las ruedas, y cuando.
         */
        private void dibujarIntervencion(HistorialImprimible.Intervencion i, boolean conImportes)
                throws IOException {
            float arriba = y;

            // Renglon de cabecera, con fondo suave para separar una visita de otra.
            lienzo.setNonStrokingColor(new Color(242, 244, 247));
            lienzo.addRect(IZQ, arriba - 13f, DER - IZQ, 13f);
            lienzo.fill();
            lienzo.setNonStrokingColor(Color.BLACK);

            float base = arriba - 9.5f;
            texto(negrita, 8.5f, IZQ + 4f, base, FECHA.format(i.fecha()));
            texto(normal, 8, IZQ + 66f, base, i.codigo());
            if (i.km() != null) {
                texto(normal, 8, IZQ + 150f, base, entero(i.km()) + " km");
            }
            if (i.tecnico() != null) {
                texto(normal, 7.5f, IZQ + 230f, base, "Técnico: " + recortar(i.tecnico(), 120f, 7.5f));
            }
            if (conImportes && i.importe() != null) {
                derecha(negrita, 8.5f, DER - 4f, base, euros(i.importe()));
            }
            y = arriba - 13f - INTERLINEA;

            if (i.motivo() != null) {
                y = parrafo(normal, 8, IZQ + 4f, "Motivo: " + i.motivo());
            }
            if (i.diagnostico() != null) {
                y = parrafo(normal, 8, IZQ + 4f, "Diagnóstico: " + i.diagnostico());
            }

            if (!i.trabajos().isEmpty()) {
                texto(negrita, 7.5f, IZQ + 4f, y, "TRABAJOS");
                y -= INTERLINEA * 0.9f;
                for (String t : i.trabajos()) {
                    y = parrafo(normal, 8, IZQ + 12f, "· " + t);
                }
            }
            if (!i.piezas().isEmpty()) {
                texto(negrita, 7.5f, IZQ + 4f, y, "MATERIAL SUSTITUIDO");
                y -= INTERLINEA * 0.9f;
                for (String p : i.piezas()) {
                    y = parrafo(normal, 8, IZQ + 12f, "· " + p);
                }
            }

            y -= INTERLINEA * 0.5f;
            linea(IZQ, y, DER, y);
            y -= INTERLINEA * 0.8f;
        }

        private void dibujarPie(HistorialImprimible h) throws IOException {
            linea(IZQ, 58f, DER, 58f);

            texto(normal, 7, IZQ, 48f,
                    "Documento informativo emitido por %s el %s. No es una factura."
                            .formatted(h.emisor().razonSocial(), FECHA.format(h.fechaEmision())));
            texto(normal, 7, IZQ, 39f,
                    "Recoge las intervenciones registradas en este taller. Puede haber trabajos "
                    + "hechos en otro sitio que no figuren aquí.");

            derecha(normal, 7, DER, 48f, "Página %d de %d".formatted(pagina, totalPaginas));
        }

        // ==================================================================
        // Medidas
        // ==================================================================

        /** Alto que va a ocupar una intervencion. Lo usan el dibujado y el conteo. */
        private float altoDe(HistorialImprimible.Intervencion i, boolean conImportes) {
            float alto = 13f + INTERLINEA;

            if (i.motivo() != null) alto += renglonesDe("Motivo: " + i.motivo()) * INTERLINEA;
            if (i.diagnostico() != null) alto += renglonesDe("Diagnóstico: " + i.diagnostico()) * INTERLINEA;

            if (!i.trabajos().isEmpty()) {
                alto += INTERLINEA * 0.9f;
                for (String t : i.trabajos()) alto += renglonesDe("· " + t) * INTERLINEA;
            }
            if (!i.piezas().isEmpty()) {
                alto += INTERLINEA * 0.9f;
                for (String p : i.piezas()) alto += renglonesDe("· " + p) * INTERLINEA;
            }
            return alto + INTERLINEA * 1.3f;
        }

        private float altoFicha(HistorialImprimible h) {
            return 44f + INTERLINEA * 1.6f;
        }

        private float altoResumen() {
            return INTERLINEA * 4.8f;
        }

        private int renglonesDe(String texto) {
            return partir(texto, DER - IZQ - 20f, 8).size();
        }

        // ==================================================================
        // Dibujo basico
        // ==================================================================

        /** Escribe un texto largo en varios renglones y devuelve la Y resultante. */
        private float parrafo(PDFont fuente, float tamano, float x, String valor) throws IOException {
            for (String renglon : partir(valor, DER - x - 8f, tamano)) {
                texto(fuente, tamano, x, y, renglon);
                y -= INTERLINEA;
            }
            return y;
        }

        /** Parte por palabras para que quepa en el ancho dado. */
        private List<String> partir(String valor, float ancho, float tamano) {
            List<String> renglones = new ArrayList<>();
            String limpio = saneado(valor).trim();
            if (limpio.isEmpty()) {
                return renglones;
            }

            StringBuilder actual = new StringBuilder();
            for (String palabra : limpio.split("\\s+")) {
                String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
                if (anchoDe(normal, tamano, prueba) <= ancho) {
                    actual = new StringBuilder(prueba);
                } else {
                    if (!actual.isEmpty()) renglones.add(actual.toString());
                    actual = new StringBuilder(palabra);
                }
            }
            if (!actual.isEmpty()) renglones.add(actual.toString());
            return renglones;
        }

        private void etiqueta(float x, float y, String rotulo, String valor, float tamano)
                throws IOException {
            texto(normal, 6.5f, x, y + 9f, rotulo);
            texto(negrita, tamano, x, y, valor);
        }

        private void recuadro(float arriba, float abajo) throws IOException {
            linea(IZQ, arriba, DER, arriba);
            linea(IZQ, abajo, DER, abajo);
            linea(IZQ, arriba, IZQ, abajo);
            linea(DER, arriba, DER, abajo);
        }

        private void linea(float x1, float y1, float x2, float y2) throws IOException {
            lienzo.setStrokingColor(Color.BLACK);
            lienzo.moveTo(x1, y1);
            lienzo.lineTo(x2, y2);
            lienzo.stroke();
        }

        private void texto(PDFont fuente, float tamano, float x, float y, String valor) throws IOException {
            if (valor == null || valor.isEmpty()) return;
            lienzo.beginText();
            lienzo.setFont(fuente, tamano);
            lienzo.setNonStrokingColor(Color.BLACK);
            lienzo.newLineAtOffset(x, y);
            lienzo.showText(saneado(valor));
            lienzo.endText();
        }

        private void derecha(PDFont fuente, float tamano, float xDerecha, float y, String valor)
                throws IOException {
            if (valor == null || valor.isEmpty()) return;
            texto(fuente, tamano, xDerecha - anchoDe(fuente, tamano, valor), y, valor);
        }

        private float anchoDe(PDFont fuente, float tamano, String valor) {
            try {
                return fuente.getStringWidth(saneado(valor)) / 1000 * tamano;
            } catch (IOException e) {
                return valor.length() * tamano * 0.5f;
            }
        }

        private String recortar(String valor, float ancho, float tamano) {
            if (valor == null) return "";
            String v = saneado(valor);
            if (anchoDe(normal, tamano, v) <= ancho) return v;
            while (v.length() > 1 && anchoDe(normal, tamano, v + "...") > ancho) {
                v = v.substring(0, v.length() - 1);
            }
            return v + "...";
        }

        /**
         * Deja el texto en lo que sabe pintar la fuente de respaldo.
         *
         * <p>Mismo criterio que en el generador de facturas: se cambian las
         * comillas y los guiones tipograficos por los de siempre y se quita lo
         * que no entre en Latin-1. Un documento con un acento raro perdido es
         * mucho mejor que uno que no se genera.
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

        private String euros(BigDecimal valor) {
            DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ESPANA));
            return f.format(valor == null ? BigDecimal.ZERO : valor) + "€";
        }

        private String entero(Number valor) {
            DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(ESPANA));
            return f.format(valor == null ? 0 : valor);
        }

        private String valorOVacio(String valor) {
            return valor == null ? "" : valor;
        }

        private String valorOGuion(String valor) {
            return valor == null || valor.isBlank() ? "—" : valor;
        }
    }
}
