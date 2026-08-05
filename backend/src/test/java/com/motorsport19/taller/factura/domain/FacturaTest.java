package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.support.FacturasDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la emision de facturas.
 *
 * <p>Se comprueba que la factura sale sellada y cuadrada de una sola vez, y que
 * no hay ninguna via para dejarla a medias o para tocarla despues.
 */
@DisplayName("Factura")
class FacturaTest {

    private final SerieFactura serieA = FacturasDePrueba.serie(1L, "A", TipoFactura.ORDINARIA, 0);
    private final SerieFactura serieR = FacturasDePrueba.serie(2L, "R", TipoFactura.RECTIFICATIVA, 0);

    @Nested
    @DisplayName("Importes")
    class Importes {

        @Test
        @DisplayName("calcula base, IVA y total como los calcula PostgreSQL")
        void importesDeLaFactura() {
            // 2,5 h x 45,00 = 112,50 base; IVA 21% = 23,625 -> 23,63
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("2.500", "45.0000")));

            assertThat(f.getBaseImponible()).isEqualByComparingTo("112.50");
            assertThat(f.getTotalIva()).isEqualByComparingTo("23.63");
            assertThat(f.getTotal()).isEqualByComparingTo("136.13");
        }

        @Test
        @DisplayName("el total es siempre base mas IVA")
        void totalCuadra() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS, List.of(
                    FacturasDePrueba.manoDeObra("2.500", "45.0000"),
                    FacturasDePrueba.pieza("ACE-10W40-1L", "3.000", "12.9000"),
                    FacturasDePrueba.pieza("FIL-ACE-HF204", "1.000", "9.5000")));

            assertThat(f.getTotal())
                    .isEqualByComparingTo(f.getBaseImponible().add(f.getTotalIva()));
        }

        @Test
        @DisplayName("reproduce al centimo la factura A/2026/000001 de los datos demo")
        void coincideConLosDatosDemo() {
            // Los mismos conceptos que carga la migracion de demostracion.
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS, List.of(
                    FacturasDePrueba.manoDeObra("2.500", "45.0000"),
                    FacturasDePrueba.pieza("ACE-10W40-1L", "3.000", "12.9000"),
                    FacturasDePrueba.pieza("FIL-ACE-HF204", "1.000", "9.5000"),
                    FacturasDePrueba.pieza("BUJ-CR8E", "2.000", "8.7500"),
                    FacturasDePrueba.pieza("FIL-AIR-HFA1618", "1.000", "24.0000")));

            assertThat(f.getBaseImponible()).isEqualByComparingTo("202.20");
            assertThat(f.getTotalIva()).isEqualByComparingTo("42.48");
            assertThat(f.getTotal()).isEqualByComparingTo("244.68");
        }

        @Test
        @DisplayName("aplica el descuento antes de calcular el IVA")
        void conDescuento() {
            LineaAFacturar conDto = new LineaAFacturar(
                    com.motorsport19.taller.orden.domain.TipoLinea.PIEZA, "Pieza con descuento", "SKU-1",
                    new BigDecimal("2.000"), new BigDecimal("100.0000"), new BigDecimal("10.00"),
                    "GENERAL", new BigDecimal("21.00"));

            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS, List.of(conDto));

            // 2 x 100 = 200, menos 10% = 180; IVA 21% = 37,80
            assertThat(f.getBaseImponible()).isEqualByComparingTo("180.00");
            assertThat(f.getTotalIva()).isEqualByComparingTo("37.80");
        }
    }

    @Nested
    @DisplayName("Desglose de IVA")
    class Desglose {

        @Test
        @DisplayName("agrupa por tipo y suma las lineas, sin recalcular sobre el total")
        void agrupaPorTipo() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS, List.of(
                    FacturasDePrueba.conIva("Al 21%", "1", "100.0000", "GENERAL", "21.00"),
                    FacturasDePrueba.conIva("Al 21% otra", "1", "50.0000", "GENERAL", "21.00"),
                    FacturasDePrueba.conIva("Al 10%", "1", "80.0000", "REDUCIDO", "10.00")));

            assertThat(f.getDesgloseIva()).hasSize(2);

            var general = f.getDesgloseIva().stream()
                    .filter(d -> d.getPorcentajeIva().compareTo(new BigDecimal("21.00")) == 0)
                    .findFirst().orElseThrow();
            assertThat(general.getBaseImponible()).isEqualByComparingTo("150.00");
            assertThat(general.getCuotaIva()).isEqualByComparingTo("31.50");

            var reducido = f.getDesgloseIva().stream()
                    .filter(d -> d.getPorcentajeIva().compareTo(new BigDecimal("10.00")) == 0)
                    .findFirst().orElseThrow();
            assertThat(reducido.getBaseImponible()).isEqualByComparingTo("80.00");
            assertThat(reducido.getCuotaIva()).isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("el desglose suma exactamente los totales de la cabecera")
        void desgloseCuadraConCabecera() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS, List.of(
                    FacturasDePrueba.conIva("Al 21%", "3", "13.3300", "GENERAL", "21.00"),
                    FacturasDePrueba.conIva("Al 10%", "7", "4.9900", "REDUCIDO", "10.00"),
                    FacturasDePrueba.conIva("Exento", "1", "25.0000", "EXENTO", "0.00")));

            BigDecimal sumaBases = f.getDesgloseIva().stream()
                    .map(DesgloseIvaFactura::getBaseImponible)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sumaCuotas = f.getDesgloseIva().stream()
                    .map(DesgloseIvaFactura::getCuotaIva)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Es exactamente lo que comprueba el trigger diferido al hacer commit.
            assertThat(sumaBases).isEqualByComparingTo(f.getBaseImponible());
            assertThat(sumaCuotas).isEqualByComparingTo(f.getTotalIva());
        }
    }

    @Nested
    @DisplayName("Sellado")
    class Sellado {

        @Test
        @DisplayName("la huella corresponde al contenido de la factura")
        void huellaCoherente() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));

            assertThat(f.huellaEsCoherente()).isTrue();
            assertThat(f.getHuella()).matches("[0-9a-f]{64}");
            assertThat(f.getAlgoritmoHuella()).isEqualTo("SHA-256");
        }

        @Test
        @DisplayName("la primera factura encadena con la huella genesis")
        void primeraDeLaCadena() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));

            assertThat(f.getHuellaAnterior()).isEqualTo("0".repeat(64));
            assertThat(f.enlazaCon(Factura.HUELLA_GENESIS)).isTrue();
        }

        @Test
        @DisplayName("la cadena canonica guardada permite reverificar la huella")
        void cadenaGuardada() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));

            // Este es el punto: con la cadena almacenada y un SHA-256, cualquiera
            // puede verificar la factura sin este programa.
            assertThat(CalculadoraHuella.calcular(f.getCadenaHuella())).isEqualTo(f.getHuella());
            assertThat(f.getCadenaHuella())
                    .contains("NIFEmisor=" + FacturasDePrueba.NIF_TALLER)
                    .contains("ImporteTotal=" + f.getTotal().toPlainString());
        }

        @Test
        @DisplayName("detecta que la huella ha sido manipulada")
        void huellaManipulada() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));

            FacturasDePrueba.manipularHuella(f, "f".repeat(64));

            assertThat(f.huellaEsCoherente()).isFalse();
        }

        @Test
        @DisplayName("detecta que un importe ha sido manipulado sin recalcular la huella")
        void importeManipulado() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));
            FacturasDePrueba.simularNumeroCompleto(f);
            assertThat(f.contenidoCoincideConElSello()).isTrue();

            // Alguien entra por psql, desactiva el trigger y baja el total.
            FacturasDePrueba.manipularTotal(f, "1.00");

            // La huella sigue cuadrando con su propia cadena canonica -- eso no
            // basta -- pero la cadena ya no describe la fila: ahi salta.
            assertThat(f.huellaEsCoherente()).isTrue();
            assertThat(f.contenidoCoincideConElSello()).isFalse();
            assertThat(f.getCadenaHuella()).doesNotContain("ImporteTotal=1.00");
        }

        @Test
        @DisplayName("un atacante que ademas cuadre las lineas tampoco se libra")
        void manipulacionCoherente() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));
            FacturasDePrueba.simularNumeroCompleto(f);

            // Aunque reescribiese las lineas para que sumasen el importe falso,
            // la cadena sellada sigue diciendo el original.
            FacturasDePrueba.manipularTotal(f, "1.00");

            assertThat(f.contenidoCoincideConElSello())
                    .as("el sello cubre el importe, no solo la coherencia interna")
                    .isFalse();
        }

        @Test
        @DisplayName("el QR lleva los datos de verificacion de la factura")
        void contenidoDelQr() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));

            assertThat(f.getQrContenido())
                    .startsWith("https://verifactu.motorsport19.example/verifica?")
                    .contains("nif=" + FacturasDePrueba.NIF_TALLER)
                    .contains("numserie=A/2026/000001")
                    .contains("importe=" + f.getTotal().toPlainString());
        }

        @Test
        @DisplayName("rechaza una huella anterior que no sea un SHA-256 valido")
        void huellaAnteriorInvalida() {
            assertThatThrownBy(() -> FacturasDePrueba.ordinaria(serieA, 1, 1, "no-es-una-huella",
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000"))))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("64 caracteres");
        }
    }

    @Nested
    @DisplayName("Reglas de emision")
    class Reglas {

        @Test
        @DisplayName("no se emite una factura sin lineas")
        void sinLineas() {
            assertThatThrownBy(() -> FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of()))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("sin lineas");
        }

        @Test
        @DisplayName("una ordinaria no puede emitirse en una serie de rectificativas")
        void serieEquivocada() {
            assertThatThrownBy(() -> FacturasDePrueba.ordinaria(serieR, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000"))))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("serie");
        }

        @Test
        @DisplayName("una rectificativa debe decir a que factura rectifica y por que")
        void rectificativaIncompleta() {
            Factura original = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));

            assertThatThrownBy(() -> Factura.emitir(
                    FacturasDePrueba.datos(serieR, 1, 2, TipoFactura.RECTIFICATIVA, null, null, null),
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")), original.getHuella()))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("a que factura rectifica");

            assertThatThrownBy(() -> Factura.emitir(
                    FacturasDePrueba.datos(serieR, 1, 2, TipoFactura.RECTIFICATIVA, original,
                            TipoRectificativa.POR_SUSTITUCION, "  "),
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")), original.getHuella()))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("motivo");
        }

        @Test
        @DisplayName("una rectificativa por diferencias admite cantidades negativas")
        void rectificativaNegativa() {
            Factura original = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1.500", "45.0000")));

            Factura rect = FacturasDePrueba.rectificativa(serieR, 1, 2, original.getHuella(), original,
                    TipoRectificativa.POR_DIFERENCIAS, "Se facturaron 1,5 h en lugar de 1 h",
                    List.of(FacturasDePrueba.manoDeObra("-0.500", "45.0000")));

            assertThat(rect.getBaseImponible()).isEqualByComparingTo("-22.50");
            assertThat(rect.getTotalIva()).isEqualByComparingTo("-4.73");
            assertThat(rect.getTotal()).isEqualByComparingTo("-27.23");
            assertThat(rect.esRectificativa()).isTrue();
            assertThat(rect.huellaEsCoherente()).isTrue();
        }

        @Test
        @DisplayName("las colecciones de la factura son de solo lectura")
        void coleccionesInmutables() {
            Factura f = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));

            assertThatThrownBy(() -> f.getLineas().add(null))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> f.getDesgloseIva().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Cadena de varias facturas")
    class Cadena {

        @Test
        @DisplayName("tres facturas encadenadas verifican de extremo a extremo")
        void cadenaDeTres() {
            Factura f1 = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));
            Factura f2 = FacturasDePrueba.ordinaria(serieA, 2, 2, f1.getHuella(),
                    List.of(FacturasDePrueba.pieza("SKU-1", "2", "30.0000")));
            Factura f3 = FacturasDePrueba.rectificativa(serieR, 1, 3, f2.getHuella(), f2,
                    TipoRectificativa.POR_SUSTITUCION, "Precio incorrecto",
                    List.of(FacturasDePrueba.pieza("SKU-1", "2", "25.0000")));

            assertThat(f1.enlazaCon(Factura.HUELLA_GENESIS)).isTrue();
            assertThat(f2.enlazaCon(f1.getHuella())).isTrue();
            assertThat(f3.enlazaCon(f2.getHuella())).isTrue();

            assertThat(List.of(f1, f2, f3)).allSatisfy(f ->
                    assertThat(f.huellaEsCoherente()).isTrue());

            // Todas las huellas distintas: la cadena no colisiona.
            assertThat(List.of(f1.getHuella(), f2.getHuella(), f3.getHuella()))
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("alterar una factura del medio rompe el enlace con la siguiente")
        void alteracionEnMedio() {
            Factura f1 = FacturasDePrueba.ordinaria(serieA, 1, 1, Factura.HUELLA_GENESIS,
                    List.of(FacturasDePrueba.manoDeObra("1", "45.0000")));
            Factura f2 = FacturasDePrueba.ordinaria(serieA, 2, 2, f1.getHuella(),
                    List.of(FacturasDePrueba.pieza("SKU-1", "2", "30.0000")));

            // Alguien cambia la huella de la primera.
            FacturasDePrueba.manipularHuella(f1, "a".repeat(64));

            // La segunda deja de enlazar: la manipulacion se detecta aunque el
            // atacante no haya tocado la segunda factura para nada.
            assertThat(f2.enlazaCon(f1.getHuella())).isFalse();
            assertThat(f1.huellaEsCoherente()).isFalse();
        }
    }
}
