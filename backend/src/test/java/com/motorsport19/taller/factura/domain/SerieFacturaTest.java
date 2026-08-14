package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Series de facturacion.
 *
 * <p>Hasta ahora solo existian en el juego de datos de demostracion, asi que una
 * instalacion de verdad no podia emitir una sola factura: el boton de «Emitir
 * factura» se quedaba muerto y no habia forma de averiguar por que. Se crean
 * desde Ajustes.
 */
@DisplayName("Serie de facturacion")
class SerieFacturaTest {

    @Nested
    @DisplayName("Apertura")
    class Apertura {

        @Test
        @DisplayName("nace activa y con el contador a cero")
        void naceVacia() {
            SerieFactura serie = SerieFactura.crear("A", 2026, "Serie general", TipoFactura.ORDINARIA);

            assertThat(serie.getCodigo()).isEqualTo("A");
            assertThat(serie.getEjercicio()).isEqualTo(2026);
            assertThat(serie.getTipo()).isEqualTo(TipoFactura.ORDINARIA);
            assertThat(serie.isActiva()).isTrue();
            // La primera factura de la serie sera la numero 1.
            assertThat(serie.getUltimoNumero()).isZero();
            assertThat(serie.tieneFacturas()).isFalse();
        }

        @Test
        @DisplayName("el codigo se guarda en mayusculas")
        void codigoEnMayusculas() {
            assertThat(SerieFactura.crear("  a  ", 2026, null, TipoFactura.ORDINARIA).getCodigo())
                    .isEqualTo("A");
        }

        @Test
        @DisplayName("sin descripcion se compone una que identifica la serie")
        void descripcionPorDefecto() {
            SerieFactura serie = SerieFactura.crear("R", 2026, "   ", TipoFactura.RECTIFICATIVA);

            assertThat(serie.getDescripcion()).isEqualTo("Serie R de 2026");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "ESTECODIGOESLARGUISIMO"})
        @DisplayName("rechaza codigos vacios o demasiado largos")
        void codigosInvalidos(String codigo) {
            assertThatThrownBy(() -> SerieFactura.crear(codigo, 2026, null, TipoFactura.ORDINARIA))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {1999, 2201})
        @DisplayName("rechaza ejercicios que no son un año")
        void ejerciciosInvalidos(int ejercicio) {
            assertThatThrownBy(() -> SerieFactura.crear("A", ejercicio, null, TipoFactura.ORDINARIA))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("exige decir si es ordinaria o rectificativa")
        void exigeTipo() {
            assertThatThrownBy(() -> SerieFactura.crear("A", 2026, null, null))
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    @Nested
    @DisplayName("Mantenimiento")
    class Mantenimiento {

        @Test
        @DisplayName("cerrarla no toca la numeracion ya emitida")
        void cerrarConservaLaNumeracion() {
            SerieFactura serie = SerieFactura.crear("A", 2026, "Serie general", TipoFactura.ORDINARIA);

            serie.desactivar();

            assertThat(serie.isActiva()).isFalse();
            assertThat(serie.getUltimoNumero()).isZero();

            serie.activar();
            assertThat(serie.isActiva()).isTrue();
        }

        @Test
        @DisplayName("la descripcion no puede quedar vacia")
        void descripcionObligatoria() {
            SerieFactura serie = SerieFactura.crear("A", 2026, "Serie general", TipoFactura.ORDINARIA);

            assertThatThrownBy(() -> serie.renombrar("  "))
                    .isInstanceOf(ReglaNegocioException.class);
            assertThat(serie.getDescripcion()).isEqualTo("Serie general");
        }

        /**
         * El codigo, el ejercicio y el tipo van impresos en el numero de cada
         * factura ya emitida y forman parte de la cadena de huellas: no hay
         * ningun metodo para cambiarlos, y este test salta si alguien anade uno.
         */
        @Test
        @DisplayName("no hay forma de cambiar codigo, ejercicio ni tipo")
        void loQueVaImpresoNoSeToca() {
            assertThat(SerieFactura.class.getDeclaredMethods())
                    .extracting(java.lang.reflect.Method::getName)
                    .doesNotContain("setCodigo", "setEjercicio", "setTipo",
                            "cambiarCodigo", "cambiarEjercicio", "cambiarTipo");
        }
    }
}
