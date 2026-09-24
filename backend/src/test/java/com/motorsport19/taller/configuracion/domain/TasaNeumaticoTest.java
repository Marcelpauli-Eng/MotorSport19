package com.motorsport19.taller.configuracion.domain;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.support.PiezasDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Que pieza lleva tasa de reciclaje.
 *
 * <p>La tasa acaba en la factura del cliente, asi que las dos equivocaciones
 * cuestan: no ponerla donde toca —la linea que se olvida— y ponerla donde no,
 * que es cobrar de mas.
 */
@DisplayName("Tasa de reciclaje de neumaticos")
class TasaNeumaticoTest {

    private static Pieza pieza(String sku, String familia) {
        Pieza p = PiezasDePrueba.conStock(1L, sku, "10");
        ReflectionTestUtils.setField(p, "familia", familia);
        return p;
    }

    private ConfiguracionTaller configurada() {
        ConfiguracionTaller c = ConfiguracionTaller.sinRellenar();
        c.configurarTasaNeumatico("Neumaticos", pieza("TASA-NEU", "Tasas"));
        return c;
    }

    @Test
    @DisplayName("un neumatico lleva tasa")
    void neumatico() {
        assertThat(configurada().llevaTasaDeReciclaje(pieza("MICH-180", "Neumaticos"))).isTrue();
    }

    @Test
    @DisplayName("la familia se compara sin distinguir mayusculas ni espacios")
    void familiaTolerante() {
        ConfiguracionTaller c = configurada();

        assertThat(c.llevaTasaDeReciclaje(pieza("A", "NEUMATICOS"))).isTrue();
        assertThat(c.llevaTasaDeReciclaje(pieza("B", " neumaticos "))).isTrue();
    }

    @Test
    @DisplayName("lo que no es un neumatico no lleva tasa")
    void otraFamilia() {
        ConfiguracionTaller c = configurada();

        assertThat(c.llevaTasaDeReciclaje(pieza("FIL-1", "Filtros"))).isFalse();
        assertThat(c.llevaTasaDeReciclaje(pieza("SIN", null))).isFalse();
        assertThat(c.llevaTasaDeReciclaje(null)).isFalse();
    }

    @Test
    @DisplayName("sin configurar, ninguna pieza lleva tasa")
    void sinConfigurar() {
        ConfiguracionTaller c = ConfiguracionTaller.sinRellenar();

        assertThat(c.llevaTasaDeReciclaje(pieza("MICH-180", "Neumaticos"))).isFalse();
    }

    @Test
    @DisplayName("la propia tasa no puede ser un neumatico, o se cobraria en cadena")
    void tasaDentroDeLaFamilia() {
        ConfiguracionTaller c = ConfiguracionTaller.sinRellenar();

        assertThatThrownBy(() -> c.configurarTasaNeumatico("Neumaticos", pieza("T", "Neumaticos")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("tasa otra tasa");
    }

    @Test
    @DisplayName("dejar cualquiera de los dos vacios la desactiva")
    void desactivar() {
        ConfiguracionTaller c = configurada();

        c.configurarTasaNeumatico(null, pieza("TASA-NEU", "Tasas"));
        assertThat(c.llevaTasaDeReciclaje(pieza("MICH", "Neumaticos"))).isFalse();
        assertThat(c.getPiezaTasaNeumatico()).isNull();

        c.configurarTasaNeumatico("Neumaticos", null);
        assertThat(c.getFamiliaNeumaticos()).isNull();
    }
}
