package com.motorsport19.taller.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La provincia de un codigo postal.
 *
 * <p>Sale de los dos primeros digitos y de una tabla fija, no de una consulta.
 * Eso es lo que permite rellenar el campo cuando el usuario todavia esta
 * tecleando, y que siga funcionando con el taller sin internet.
 */
@DisplayName("Provincia por codigo postal")
class ProvinciasTest {

    @ParameterizedTest(name = "{0} es {1}")
    @CsvSource({
            "01001, Araba/Álava",
            "08820, Barcelona",
            "15001, A Coruña",
            "28001, Madrid",
            "38001, Santa Cruz de Tenerife",
            "46001, Valencia/València",
            "51001, Ceuta",
            "52001, Melilla",
    })
    @DisplayName("cada prefijo devuelve su provincia, con la grafia oficial")
    void prefijos(String codigo, String provincia) {
        assertThat(Provincias.de(codigo)).contains(provincia);
    }

    @ParameterizedTest(name = "«{0}» ya dice Barcelona")
    @ValueSource(strings = {"08", "088", "0882", "08820"})
    @DisplayName("con dos digitos ya contesta: por eso se puede rellenar mientras se teclea")
    void aMedioEscribir(String parcial) {
        assertThat(Provincias.de(parcial)).contains("Barcelona");
    }

    @ParameterizedTest(name = "«{0}» no da provincia")
    @ValueSource(strings = {"", "0", "8", "x", "abc"})
    @DisplayName("con menos de dos digitos no se inventa nada")
    void demasiadoCorto(String parcial) {
        assertThat(Provincias.de(parcial)).isEmpty();
    }

    @Test
    @DisplayName("un codigo nulo no revienta")
    void nulo() {
        assertThat(Provincias.de(null)).isEmpty();
        assertThat(Provincias.esCompleto(null)).isFalse();
        assertThat(Provincias.normalizar(null)).isEmpty();
    }

    @ParameterizedTest(name = "el prefijo {0} no existe")
    @ValueSource(strings = {"00123", "53001", "60001", "99999"})
    @DisplayName("los prefijos que no son de ninguna provincia se quedan vacios")
    void prefijosInexistentes(String codigo) {
        assertThat(Provincias.de(codigo)).isEmpty();
        assertThat(Provincias.esCompleto(codigo)).isFalse();
    }

    @Test
    @DisplayName("se admite tal y como se teclea, con espacios o guion")
    void tolerante() {
        assertThat(Provincias.de(" 08820 ")).contains("Barcelona");
        assertThat(Provincias.de("08-820")).contains("Barcelona");
        assertThat(Provincias.normalizar("08 820")).contains("08820");
    }

    @Test
    @DisplayName("solo se considera completo con cinco digitos")
    void completo() {
        assertThat(Provincias.esCompleto("08820")).isTrue();
        assertThat(Provincias.esCompleto("0882")).isFalse();
        assertThat(Provincias.esCompleto("088201")).isFalse();
        assertThat(Provincias.normalizar("0882")).isEmpty();
    }

    @Test
    @DisplayName("estan las 52 provincias, ninguna de menos")
    void tablaCompleta() {
        for (int prefijo = 1; prefijo <= 52; prefijo++) {
            String codigo = "%02d001".formatted(prefijo);
            assertThat(Provincias.de(codigo))
                    .as("falta la provincia con prefijo %02d", prefijo)
                    .isPresent();
        }
    }
}
