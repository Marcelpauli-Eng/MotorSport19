package com.motorsport19.taller.factura.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del calculo de huellas.
 *
 * <p>Aqui se comprueba lo mas fragil de todo el sistema: si el formato de la
 * cadena canonica cambiase sin querer, las facturas nuevas seguirian
 * encadenandose entre si pero ya no se podrian verificar contra las antiguas, y
 * nadie se daria cuenta hasta una inspeccion.
 */
@DisplayName("Calculo de la huella encadenada")
class CalculadoraHuellaTest {

    private static final String NIF = "B87654323";
    private static final Instant EMISION = ZonedDateTime
            .of(2026, 5, 15, 18, 25, 0, 0, ZoneId.of("Europe/Madrid"))
            .toInstant();

    private String cadenaDeEjemplo(String huellaAnterior) {
        return CalculadoraHuella.cadenaCanonica(NIF, "A/2026/000001", LocalDate.of(2026, 5, 15),
                TipoFactura.ORDINARIA, new BigDecimal("42.48"), new BigDecimal("244.68"),
                huellaAnterior, EMISION);
    }

    @Nested
    @DisplayName("Cadena canonica")
    class Cadena {

        @Test
        @DisplayName("tiene exactamente el formato acordado")
        void formatoExacto() {
            // Este literal es el contrato. Si cambia, las facturas ya emitidas
            // dejan de poder verificarse: cualquier modificacion aqui exige
            // decidir a conciencia que hacer con el historico.
            assertThat(cadenaDeEjemplo(CalculadoraHuella.HUELLA_GENESIS)).isEqualTo(
                    "NIFEmisor=B87654323"
                    + "&NumSerieFactura=A/2026/000001"
                    + "&FechaExpedicion=15-05-2026"
                    + "&TipoFactura=ORDINARIA"
                    + "&CuotaTotal=42.48"
                    + "&ImporteTotal=244.68"
                    + "&Huella=" + "0".repeat(64)
                    + "&FechaHoraHusoGenRegistro=2026-05-15T18:25:00+02");
        }

        @Test
        @DisplayName("los importes van con dos decimales y punto, sin separador de miles")
        void formatoDeImportes() {
            String cadena = CalculadoraHuella.cadenaCanonica(NIF, "A/2026/000002",
                    LocalDate.of(2026, 6, 5), TipoFactura.ORDINARIA,
                    new BigDecimal("117.1"), new BigDecimal("1674.9"),
                    CalculadoraHuella.HUELLA_GENESIS, EMISION);

            assertThat(cadena).contains("CuotaTotal=117.10").contains("ImporteTotal=1674.90");
            assertThat(cadena).doesNotContain("1,674").doesNotContain("1.674,");
        }

        @Test
        @DisplayName("el huso cambia entre invierno y verano")
        void husoHorario() {
            Instant invierno = ZonedDateTime
                    .of(2026, 1, 15, 12, 0, 0, 0, ZoneId.of("Europe/Madrid")).toInstant();

            assertThat(CalculadoraHuella.marcaTemporal(invierno)).isEqualTo("2026-01-15T12:00:00+01");
            assertThat(CalculadoraHuella.marcaTemporal(EMISION)).isEqualTo("2026-05-15T18:25:00+02");
        }
    }

    @Nested
    @DisplayName("Huella SHA-256")
    class Huella {

        @Test
        @DisplayName("son 64 caracteres hexadecimales en minusculas")
        void formatoDeLaHuella() {
            assertThat(CalculadoraHuella.calcular(cadenaDeEjemplo(CalculadoraHuella.HUELLA_GENESIS)))
                    .hasSize(64)
                    .matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("es determinista: la misma cadena da siempre la misma huella")
        void esDeterminista() {
            String cadena = cadenaDeEjemplo(CalculadoraHuella.HUELLA_GENESIS);
            assertThat(CalculadoraHuella.calcular(cadena))
                    .isEqualTo(CalculadoraHuella.calcular(cadena));
        }

        @Test
        @DisplayName("coincide con el SHA-256 conocido de la cadena")
        void valorConocido() {
            // Vector fijo: si el algoritmo o la codificacion cambiaran, esto salta.
            assertThat(CalculadoraHuella.calcular("MotorSport19"))
                    .isEqualTo(java.util.HexFormat.of().formatHex(
                            hash("MotorSport19".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }

        @Test
        @DisplayName("cambiar un solo centimo cambia la huella entera")
        void efectoAvalancha() {
            String original = CalculadoraHuella.calcular(cadenaDeEjemplo(CalculadoraHuella.HUELLA_GENESIS));
            String alterada = CalculadoraHuella.calcular(CalculadoraHuella.cadenaCanonica(
                    NIF, "A/2026/000001", LocalDate.of(2026, 5, 15), TipoFactura.ORDINARIA,
                    new BigDecimal("42.48"), new BigDecimal("244.69"),  // un centimo mas
                    CalculadoraHuella.HUELLA_GENESIS, EMISION));

            assertThat(alterada).isNotEqualTo(original);
        }

        @Test
        @DisplayName("cambiar solo la huella anterior tambien cambia la huella")
        void dependeDeLaAnterior() {
            String conGenesis = CalculadoraHuella.calcular(
                    cadenaDeEjemplo(CalculadoraHuella.HUELLA_GENESIS));
            String conOtra = CalculadoraHuella.calcular(cadenaDeEjemplo("a".repeat(64)));

            // Esto es lo que hace que la cadena sea una cadena y no una lista.
            assertThat(conOtra).isNotEqualTo(conGenesis);
        }

        @Test
        @DisplayName("verifica una huella correcta y rechaza una manipulada")
        void verificacion() {
            String cadena = cadenaDeEjemplo(CalculadoraHuella.HUELLA_GENESIS);
            String huella = CalculadoraHuella.calcular(cadena);

            assertThat(CalculadoraHuella.huellaCoincide(cadena, huella)).isTrue();
            assertThat(CalculadoraHuella.huellaCoincide(cadena, huella.toUpperCase())).isTrue();
            assertThat(CalculadoraHuella.huellaCoincide(cadena, "f".repeat(64))).isFalse();
            assertThat(CalculadoraHuella.huellaCoincide(cadena, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Encadenamiento")
    class Encadenamiento {

        @Test
        @DisplayName("una cadena de cinco facturas verifica de extremo a extremo")
        void cadenaCompleta() {
            String[] huellas = new String[5];
            String[] cadenas = new String[5];
            String anterior = CalculadoraHuella.HUELLA_GENESIS;

            for (int i = 0; i < 5; i++) {
                cadenas[i] = CalculadoraHuella.cadenaCanonica(NIF,
                        "A/2026/%06d".formatted(i + 1), LocalDate.of(2026, 5, 15 + i),
                        TipoFactura.ORDINARIA, new BigDecimal("21.00"), new BigDecimal("121.00"),
                        anterior, EMISION.plusSeconds(i * 3600L));
                huellas[i] = CalculadoraHuella.calcular(cadenas[i]);
                anterior = huellas[i];
            }

            // Recorrido de verificacion, como el que hace el servicio.
            String esperada = CalculadoraHuella.HUELLA_GENESIS;
            for (int i = 0; i < 5; i++) {
                assertThat(CalculadoraHuella.huellaCoincide(cadenas[i], huellas[i]))
                        .as("la huella %d corresponde a su contenido", i + 1)
                        .isTrue();
                assertThat(cadenas[i]).contains("Huella=" + esperada);
                esperada = huellas[i];
            }
        }

        @Test
        @DisplayName("todas las huellas de una cadena son distintas entre si")
        void sinColisiones() {
            java.util.Set<String> vistas = new java.util.HashSet<>();
            String anterior = CalculadoraHuella.HUELLA_GENESIS;

            for (int i = 0; i < 50; i++) {
                String cadena = CalculadoraHuella.cadenaCanonica(NIF,
                        "A/2026/%06d".formatted(i + 1), LocalDate.of(2026, 1, 1).plusDays(i),
                        TipoFactura.ORDINARIA, new BigDecimal("21.00"), new BigDecimal("121.00"),
                        anterior, EMISION.plusSeconds(i * 60L));
                String huella = CalculadoraHuella.calcular(cadena);

                assertThat(vistas.add(huella)).as("la huella %d se repite", i + 1).isTrue();
                anterior = huella;
            }
        }
    }

    private static byte[] hash(byte[] datos) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(datos);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
