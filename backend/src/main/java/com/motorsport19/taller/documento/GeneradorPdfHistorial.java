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
 * Imprime hojas de vida: la de una moto o la de un cliente con todas las suyas.
 *
 * <p>Los dos casos son el mismo recorrido, porque el documento es una lista de
 * bloques de moto y el de una moto suelta tiene un bloque. Lo unico que cambia
 * es que el del cliente lleva su ficha arriba y un acumulado de todas.
 *
 * <p>Va aparte de {@link GeneradorPdfDocumento} porque son dos documentos con
 * dos problemas distintos. Aquel dibuja un impreso de una hoja con las bandas
 * en coordenadas fijas, copiando el papel de siempre del taller; este es un
 * listado que crece: un cliente con cuatro motos y quince años de
 * mantenimiento ocupa varias paginas, y hay que poder partirlas por donde toque
 * sin cortar ninguna intervencion por la mitad ni dejar la ficha de una moto
 * sola al pie de la hoja.
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

    /*
      Recuadro de una ficha —la del cliente o la de una moto—: dos filas de
      rotulo pequeño con su valor debajo.

      Las distancias van medidas desde el borde de arriba hasta la LINEA BASE
      del valor; el rotulo lo coloca 9 pt mas arriba `etiqueta()`.

      Estan puestas para que sobre y falte lo mismo por arriba que por abajo,
      unos 6 pt a cada lado:

        6,5  aire
        13   linea base del rotulo de arriba (mayusculas de 6,5)
        22   linea base de su valor
        34   linea base del rotulo de abajo
        43   linea base de su valor (los rabos de las letras bajan ~2)
        51   borde de abajo

      Con la primera fila a 14, como estaba, el rotulo se comia la raya de
      arriba mientras abajo sobraba medio renglon.
    */
    private static final float FICHA_ALTO = 51f;
    private static final float FICHA_FILA1 = 22f;
    private static final float FICHA_FILA2 = 43f;
    /** Sangria del texto dentro del recuadro, para que no se pegue al filo. */
    private static final float FICHA_SANGRIA = 8f;

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

            // El resumen general solo va arriba cuando el sujeto del documento es
            // el cliente. En el de una moto, lo primero tiene que ser de que moto
            // habla el papel; su resumen va pegado a su ficha, dentro del bloque.
            if (h.cliente() != null) {
                dibujarFichaCliente(h);
                dibujarResumen(h, h.resumen(), h.motos().size() > 1);
            }

            for (HistorialImprimible.BloqueMoto moto : h.motos()) {
                dibujarBloque(h, moto);
            }

            if (h.motos().isEmpty()) {
                texto(normal, 8, IZQ, y, "Este cliente no tiene ninguna moto dada de alta.");
            }

            cerrarPagina(h);
        }

        /**
         * Una moto entera: su ficha y todas sus intervenciones.
         *
         * <p>La ficha nunca se queda sola al final de una hoja. Si no cabe con
         * al menos una intervencion debajo, la moto empieza en la pagina
         * siguiente: un recuadro huerfano al pie obliga a pasar la hoja para
         * saber de que moto hablaba.
         */
        private void dibujarBloque(HistorialImprimible h, HistorialImprimible.BloqueMoto moto)
                throws IOException {
            float primera = moto.intervenciones().isEmpty()
                    ? 0f
                    : altoDe(moto.intervenciones().get(0), h.conImportes());

            if (y - (altoFicha() + primera) < Y_MINIMA) {
                cerrarPagina(h);
                abrirPagina(h);
            }

            dibujarFichaMoto(moto);

            // Cada moto lleva su acumulado pegado a su ficha: en el historial de
            // una es el resumen del documento, y en el de un cliente es lo que
            // deja comparar una moto con otra.
            dibujarResumen(h, moto.resumen(), false);

            texto(negrita, 9, IZQ, y, "INTERVENCIONES");
            y -= INTERLINEA * 1.4f;

            if (moto.intervenciones().isEmpty()) {
                texto(normal, 8, IZQ, y,
                        "Esta moto todavía no tiene ninguna intervención registrada en el taller.");
                y -= INTERLINEA * 2f;
                return;
            }

            for (HistorialImprimible.Intervencion i : moto.intervenciones()) {
                float alto = altoDe(i, h.conImportes());
                if (y - alto < Y_MINIMA) {
                    cerrarPagina(h);
                    abrirPagina(h);
                }
                dibujarIntervencion(i, h.conImportes());
            }
            y -= INTERLINEA;
        }

        // ==================================================================
        // Paginacion
        // ==================================================================

        /**
         * Simula el recorrido para saber cuantas hojas van a salir.
         *
         * <p>Recorre exactamente los mismos saltos que {@link #dibujar}, asi que
         * cualquier cambio en las alturas hay que hacerlo en {@link #altoDe} y en
         * las constantes que usan las dos pasadas, y siguen de acuerdo.
         */
        private int contarPaginas(HistorialImprimible h) {
            int paginas = 1;
            float cursor = Y_INICIAL_PRIMERA;

            // altoFicha() ya incluye la separacion que deja debajo.
            if (h.cliente() != null) {
                cursor -= altoFicha() + altoResumen(h.motos().size() > 1);
            }

            for (HistorialImprimible.BloqueMoto moto : h.motos()) {
                float primera = moto.intervenciones().isEmpty()
                        ? 0f
                        : altoDe(moto.intervenciones().get(0), h.conImportes());

                if (cursor - (altoFicha() + primera) < Y_MINIMA) {
                    paginas++;
                    cursor = Y_INICIAL_SIGUIENTE;
                }
                cursor -= altoFicha() + altoResumen(false) + INTERLINEA * 1.4f;

                if (moto.intervenciones().isEmpty()) {
                    cursor -= INTERLINEA * 2f;
                    continue;
                }
                for (HistorialImprimible.Intervencion i : moto.intervenciones()) {
                    float alto = altoDe(i, h.conImportes());
                    if (cursor - alto < Y_MINIMA) {
                        paginas++;
                        cursor = Y_INICIAL_SIGUIENTE;
                    }
                    cursor -= alto;
                }
                cursor -= INTERLINEA;
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
                texto(negrita, 9, IZQ, 800f, h.titulo());
                derecha(normal, 8, DER, 800f, continuidad(h));
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

            texto(negrita, 14, IZQ, 714.27f, h.titulo());
        }

        /** Matricula, moto y bastidor: con lo que se comprueba que el papel es de esa moto. */
        private void dibujarFichaMoto(HistorialImprimible.BloqueMoto moto) throws IOException {
            var v = moto.vehiculo();
            float arriba = y;
            float abajo = y - FICHA_ALTO;

            recuadro(arriba, abajo);

            float col1 = IZQ + FICHA_SANGRIA;
            float col2 = 200f;
            float col3 = 400f;
            float fila1 = arriba - FICHA_FILA1;
            float fila2 = arriba - FICHA_FILA2;

            etiqueta(col1, fila1, "MATRÍCULA", v.matricula(), 11);
            etiqueta(col2, fila1, "MOTO", "%s %s".formatted(valorOVacio(v.marca()), valorOVacio(v.modelo())).trim(), 9);
            etiqueta(col3, fila1, "AÑO", v.anio() == null ? "—" : String.valueOf(v.anio()), 9);

            etiqueta(col1, fila2, "BASTIDOR", valorOGuion(v.bastidor()), 8);
            etiqueta(col2, fila2, "KILÓMETROS", v.kmActual() == null ? "—" : entero(v.kmActual()) + " km", 9);
            etiqueta(col3, fila2, "TITULAR", recortar(valorOGuion(moto.propietario()), 150f, 8), 8);

            y = abajo - INTERLINEA * 1.6f;
        }

        /**
         * Las cifras de un tramo del documento.
         *
         * @param conMotos añade cuantas motos entran en el acumulado. Solo tiene
         *                 sentido en el resumen general de un cliente con varias
         */
        private void dibujarResumen(HistorialImprimible h, HistorialImprimible.Resumen r,
                                    boolean conMotos) throws IOException {
            texto(negrita, 9, IZQ, y, "RESUMEN");
            y -= INTERLINEA * 1.2f;

            if (conMotos) {
                texto(normal, 8.5f, IZQ, y,
                        "%d motos de este cliente con historial en el taller".formatted(r.motos()));
                y -= INTERLINEA;
            }

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
         * Ficha del titular, en el historial de un cliente.
         *
         * <p>Aqui el sujeto del documento es la persona, asi que va arriba y sus
         * motos cuelgan debajo. En el historial de una moto suelta no se dibuja:
         * el titular ya sale en la ficha de la moto.
         */
        private void dibujarFichaCliente(HistorialImprimible h) throws IOException {
            var c = h.cliente();
            float arriba = y;
            float abajo = y - FICHA_ALTO;

            recuadro(arriba, abajo);

            float fila1 = arriba - FICHA_FILA1;
            float fila2 = arriba - FICHA_FILA2;

            etiqueta(IZQ + FICHA_SANGRIA, fila1, "CLIENTE", recortar(c.nombre(), 300f, 11), 11);
            etiqueta(400f, fila1, "NIF", valorOGuion(c.documento()), 9);
            etiqueta(IZQ + FICHA_SANGRIA, fila2, "TELÉFONO", valorOGuion(c.telefono()), 9);
            etiqueta(400f, fila2, "POBLACIÓN", recortar(valorOGuion(c.poblacion()), 150f, 9), 9);

            y = abajo - INTERLINEA * 1.6f;
        }

        /** Lo que va arriba a la derecha en las hojas siguientes a la primera. */
        private String continuidad(HistorialImprimible h) {
            String sujeto = h.cliente() != null
                    ? h.cliente().nombre()
                    : h.motos().isEmpty() ? "" : h.motos().get(0).vehiculo().matricula();

            return "%s · %s".formatted(sujeto, h.emisor().razonSocial());
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

        /** Alto del recuadro de una ficha, la del cliente o la de una moto. */
        private float altoFicha() {
            return FICHA_ALTO + INTERLINEA * 1.6f;
        }

        /**
         * Alto del bloque de resumen.
         *
         * <p>Se calcula por lo alto —contando siempre el renglon de kilometros y
         * el de importes, aunque no salgan— porque esta medida solo decide donde
         * cortar la pagina. Pasarse deja algo de aire al pie; quedarse corto
         * empuja una intervencion fuera de la hoja.
         */
        private float altoResumen(boolean conMotos) {
            return INTERLINEA * (conMotos ? 5.8f : 4.8f);
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
