package com.motorsport19.taller.factura.service;

import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.SerieFactura;
import com.motorsport19.taller.factura.domain.TipoFactura;
import com.motorsport19.taller.factura.domain.TipoRectificativa;
import com.motorsport19.taller.support.FacturasDePrueba;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la generacion del PDF.
 *
 * <p>No se comprueba el aspecto sino el contenido: que el PDF diga exactamente
 * lo que dice la factura registrada. Un PDF bonito con un total distinto al
 * registrado seria mucho peor que uno feo.
 */
@DisplayName("PDF de la factura")
class GeneradorPdfFacturaTest {

    private final GeneradorPdfFactura generador = new GeneradorPdfFactura();
    private final SerieFactura serieA = FacturasDePrueba.serie(1L, "A", TipoFactura.ORDINARIA, 0);
    private final SerieFactura serieR = FacturasDePrueba.serie(2L, "R", TipoFactura.RECTIFICATIVA, 0);

    private String textoDe(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    private Factura facturaDeEjemplo() {
        Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS, List.of(
                FacturasDePrueba.manoDeObra("2.500", "45.0000"),
                FacturasDePrueba.pieza("ACE-10W40-1L", "3.000", "12.9000")));
        FacturasDePrueba.simularNumeroCompleto(f);
        return f;
    }

    @Test
    @DisplayName("genera un PDF valido de una pagina")
    void generaPdfValido() throws IOException {
        Factura f = facturaDeEjemplo();

        byte[] pdf = generador.generar(f, f.getLineas(), f.getDesgloseIva());

        assertThat(pdf).isNotEmpty();
        // Cabecera magica de todo PDF.
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("imprime los datos fiscales de emisor y receptor")
    void datosFiscales() throws IOException {
        Factura f = facturaDeEjemplo();

        String texto = textoDe(generador.generar(f, f.getLineas(), f.getDesgloseIva()));

        assertThat(texto)
                .contains("MotorSport19 Taller S.L.")
                .contains(FacturasDePrueba.NIF_TALLER)
                .contains("Carlos Nunez Prieto")
                .contains("12345678Z")
                .contains("Calle de Alcala 145");
    }

    @Test
    @DisplayName("imprime el numero, la fecha y el total que constan en el registro")
    void identificacionEImportes() throws IOException {
        Factura f = facturaDeEjemplo();

        String texto = textoDe(generador.generar(f, f.getLineas(), f.getDesgloseIva()));

        assertThat(texto).contains("A/2026/000001");
        assertThat(texto).contains("15/05/2026");
        assertThat(texto).contains("TOTAL");

        // El importe impreso se deriva del que consta en la factura, no de un
        // numero escrito a mano aqui: lo que importa es que el papel y el
        // registro digan lo mismo.
        String totalEnFormatoEspanol = f.getTotal().toPlainString().replace('.', ',');
        assertThat(texto).contains(totalEnFormatoEspanol);
        assertThat(f.getTotal()).isEqualByComparingTo("182.96");
    }

    @Test
    @DisplayName("imprime cada linea con su descripcion y su SKU")
    void lineas() throws IOException {
        Factura f = facturaDeEjemplo();

        String texto = textoDe(generador.generar(f, f.getLineas(), f.getDesgloseIva()));

        assertThat(texto)
                .contains("Trabajo de taller")
                .contains("ACE-10W40-1L");
    }

    @Test
    @DisplayName("imprime la huella y la huella anterior para poder verificar el papel")
    void selloDeVerificacion() throws IOException {
        Factura f = facturaDeEjemplo();

        String texto = textoDe(generador.generar(f, f.getLineas(), f.getDesgloseIva()));

        // Sin la huella impresa, un PDF que circula por ahi no se puede
        // contrastar con el registro.
        assertThat(texto)
                .contains("REGISTRO DE FACTURACION")
                .contains(f.getHuella())
                .contains(f.getHuellaAnterior())
                .contains("SHA-256");
    }

    @Test
    @DisplayName("una rectificativa se identifica como tal y dice a quien corrige")
    void rectificativa() throws IOException {
        Factura original = facturaDeEjemplo();
        Factura rect = FacturasDePrueba.rectificativa(serieR, 1, 2, original.getHuella(), original,
                TipoRectificativa.POR_SUSTITUCION, "Error en las horas de mano de obra",
                List.of(FacturasDePrueba.manoDeObra("1.000", "45.0000")));
        FacturasDePrueba.simularNumeroCompleto(rect);

        String texto = textoDe(generador.generar(rect, rect.getLineas(), rect.getDesgloseIva()));

        assertThat(texto)
                .contains("FACTURA RECTIFICATIVA")
                .contains("Motivo de la rectificacion")
                .contains("Error en las horas de mano de obra");
    }

    @Test
    @DisplayName("un caracter que la fuente no sabe dibujar no tumba la factura")
    void caracterNoRepresentable() {
        Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS, List.of(
                FacturasDePrueba.conIva("Pieza con simbolo 中文 raro", "1", "10.0000",
                        "GENERAL", "21.00")));
        FacturasDePrueba.simularNumeroCompleto(f);

        // Un nombre exotico en una descripcion no puede impedir facturar.
        assertThat(generador.generar(f, f.getLineas(), f.getDesgloseIva())).isNotEmpty();
    }

    @Test
    @DisplayName("el QR se genera y es un PNG")
    void codigoQr() {
        byte[] png = GeneradorQr.generarPng(
                "https://verifactu.motorsport19.example/verifica?nif=B87654323&numserie=A/2026/000001");

        assertThat(png).isNotEmpty();
        // Firma PNG: 89 50 4E 47
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(new String(png, 1, 3)).isEqualTo("PNG");
    }
}
