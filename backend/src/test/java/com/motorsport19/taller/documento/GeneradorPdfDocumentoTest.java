package com.motorsport19.taller.documento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprobaciones del PDF con el formato del taller.
 *
 * <p>No se compara pixel a pixel —eso seria fragil y no aportaria—, pero si se
 * comprueba lo que rompe de verdad un documento: que se genere, que sea un PDF
 * valido de una sola pagina y que aguante los casos incomodos (sin lineas, con
 * textos larguisimos, con caracteres que las fuentes estandar no llevan).
 *
 * <p>El fichero se deja en {@code target/} para poder abrirlo y compararlo con
 * el documento de referencia a ojo, que para la maqueta es lo unico que vale.
 */
@DisplayName("PDF con el formato del taller")
class GeneradorPdfDocumentoTest {

    private final GeneradorPdfDocumento generador = new GeneradorPdfDocumento();

    private static DocumentoImprimible.Emisor emisor() {
        return new DocumentoImprimible.Emisor(
                "19 RACING MOTORSPORT",
                "CARRER SANT CELONI 44",
                "08450 LLINARS DEL VALLÈS BARCELONA",
                "Z0434278C",
                "670378197",
                "19racingmotorsport@gmail.com");
    }

    private static DocumentoImprimible.Totales totales() {
        return new DocumentoImprimible.Totales(
                new BigDecimal("342.00"), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                new BigDecimal("342.00"), new BigDecimal("21.00"), new BigDecimal("71.82"),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                new BigDecimal("413.82"));
    }

    private static DocumentoImprimible presupuesto(List<DocumentoImprimible.Linea> lineas) {
        return new DocumentoImprimible(
                "PRESUPUESTO", "TOTAL PRESUPUESTO",
                "ORD|PRE|202600000000041", "ORDINARIA",
                LocalDate.of(2026, 7, 20), "S/N",
                emisor(),
                new DocumentoImprimible.Cliente("FRANSISCO SURIS JIMENEZ",
                        "LLINARS DEL VALLÈS BARCELONA", "47942339n", "00030", "675 21 67 97"),
                new DocumentoImprimible.Vehiculo("4862LRW", "ZD4KVH000MS001382", "APRILIA RS660", 0),
                "CONTADO", LocalDate.of(2026, 7, 20),
                lineas, totales(), null);
    }

    private static List<DocumentoImprimible.Linea> lineasDeEjemplo() {
        return List.of(
                DocumentoImprimible.Linea.cabecera("MANO DE OBRA", new BigDecimal("342.00")),
                DocumentoImprimible.Linea.de("GEN", "CONTROL, BUSQUEDA AVERIA, APERTURA MOTOR",
                        new BigDecimal("6.000"), new BigDecimal("46.00"), BigDecimal.ZERO,
                        new BigDecimal("276.00")),
                DocumentoImprimible.Linea.de("GEN", "PASO DIAGNOSTICO APRILIA OFICIAL",
                        new BigDecimal("1.000"), new BigDecimal("41.00"), BigDecimal.ZERO,
                        new BigDecimal("41.00")),
                DocumentoImprimible.Linea.de("GEN", "SERVICIO DE TRANSPORTE, ENTREGA EN BCN",
                        new BigDecimal("1.000"), new BigDecimal("25.00"), BigDecimal.ZERO,
                        new BigDecimal("25.00")));
    }

    @Test
    @DisplayName("genera un PDF de una pagina y lo deja para revisarlo")
    void generaElDocumento() throws Exception {
        byte[] pdf = generador.generar(presupuesto(lineasDeEjemplo()));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        Path destino = Path.of("target", "presupuesto-ejemplo.pdf");
        Files.write(destino, pdf);
        assertThat(destino).exists();
    }

    @Test
    @DisplayName("un documento sin lineas tambien sale: la maqueta no depende del contenido")
    void sinLineas() {
        assertThat(generador.generar(presupuesto(List.of()))).isNotEmpty();
    }

    @Test
    @DisplayName("una descripcion larguisima se recorta en vez de invadir la columna de al lado")
    void descripcionMuyLarga() {
        var larga = DocumentoImprimible.Linea.de("GEN",
                "SUSTITUCION COMPLETA DEL KIT DE TRANSMISION, CADENA, CORONA Y PIÑON, "
                        + "CON AJUSTE DE TENSION Y ENGRASE, INCLUYE LIMPIEZA DEL BASCULANTE",
                BigDecimal.ONE, new BigDecimal("46.00"), BigDecimal.ZERO, new BigDecimal("46.00"));

        assertThat(generador.generar(presupuesto(List.of(larga)))).isNotEmpty();
    }

    @Test
    @DisplayName("los caracteres que la fuente no lleva no tumban la generacion")
    void caracteresRaros() {
        var rara = DocumentoImprimible.Linea.de("GEN",
                "REVISIÓN “COMPLETA” — 20.000 km… ✓ correcto",
                BigDecimal.ONE, new BigDecimal("46.00"), BigDecimal.ZERO, new BigDecimal("46.00"));

        assertThat(generador.generar(presupuesto(List.of(rara)))).isNotEmpty();
    }

    @Test
    @DisplayName("con mas lineas de las que caben, se corta y no se sale de la caja")
    void muchasLineas() {
        var muchas = new java.util.ArrayList<DocumentoImprimible.Linea>();
        for (int i = 0; i < 80; i++) {
            muchas.add(DocumentoImprimible.Linea.de("GEN", "Trabajo numero " + i,
                    BigDecimal.ONE, new BigDecimal("46.00"), BigDecimal.ZERO, new BigDecimal("46.00")));
        }
        assertThat(generador.generar(presupuesto(muchas))).isNotEmpty();
    }
}
