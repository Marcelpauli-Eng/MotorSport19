package com.motorsport19.taller.common.util;

import com.motorsport19.taller.cliente.domain.TipoDocumento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Validador de documentos fiscales")
class ValidadorDocumentoTest {

    @ParameterizedTest(name = "{0} es un NIF valido")
    @ValueSource(strings = {"12345678Z", "45678912S", "23456789D", "34567890V", "11223344B", "87654321X"})
    @DisplayName("acepta NIF con digito de control correcto")
    void nifValidos(String documento) {
        assertThat(ValidadorDocumento.esValido(documento)).isTrue();
        assertThat(ValidadorDocumento.deducirTipo(documento)).isEqualTo(TipoDocumento.NIF);
    }

    @ParameterizedTest(name = "{0} no es un NIF valido")
    @ValueSource(strings = {"12345678A", "12345678", "1234567Z", "123456789Z", "ABCDEFGHZ"})
    @DisplayName("rechaza NIF con letra o longitud incorrectas")
    void nifInvalidos(String documento) {
        assertThat(ValidadorDocumento.esValido(documento)).isFalse();
    }

    @ParameterizedTest(name = "{0} es un NIE valido")
    @ValueSource(strings = {"X1234567L", "Y2345678Z", "Z1234567R"})
    @DisplayName("acepta NIE con digito de control correcto")
    void nieValidos(String documento) {
        assertThat(ValidadorDocumento.esValido(documento)).isTrue();
        assertThat(ValidadorDocumento.deducirTipo(documento)).isEqualTo(TipoDocumento.NIE);
    }

    @Test
    @DisplayName("rechaza un NIE con la letra de control cambiada")
    void nieInvalido() {
        assertThat(ValidadorDocumento.esValido("X1234567A")).isFalse();
    }

    @ParameterizedTest(name = "{0} es un CIF valido")
    @ValueSource(strings = {"B87654323", "A28184562", "B61234563", "B45678901", "B86543212"})
    @DisplayName("acepta CIF con digito de control correcto")
    void cifValidos(String documento) {
        assertThat(ValidadorDocumento.esValido(documento)).isTrue();
        assertThat(ValidadorDocumento.deducirTipo(documento)).isEqualTo(TipoDocumento.CIF);
    }

    @ParameterizedTest(name = "{0} no es un CIF valido")
    @ValueSource(strings = {"B87654321", "A28184560", "I12345678"})
    @DisplayName("rechaza CIF con control incorrecto o letra inicial no valida")
    void cifInvalidos(String documento) {
        assertThat(ValidadorDocumento.esValido(documento)).isFalse();
    }

    @ParameterizedTest(name = "'{0}' se normaliza a '{1}'")
    @CsvSource({
            "'12345678z', 12345678Z",
            "'12345678-Z', 12345678Z",
            "' 12345678 Z ', 12345678Z",
            "'b-87.654.323', B87654323"
    })
    @DisplayName("normaliza quitando espacios, puntos y guiones")
    void normalizacion(String entrada, String esperado) {
        assertThat(ValidadorDocumento.normalizar(entrada)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("normaliza a nulo lo vacio o solo con separadores")
    void normalizacionDeVacios() {
        assertThat(ValidadorDocumento.normalizar(null)).isNull();
        assertThat(ValidadorDocumento.normalizar("   ")).isNull();
        assertThat(ValidadorDocumento.normalizar("--")).isNull();
    }

    @Test
    @DisplayName("distingue un documento no verificable de uno invalido")
    void documentoExtranjero() {
        // Un numero de IVA extranjero no es un documento espanol: no se puede
        // verificar, pero tampoco hay que darlo por erroneo.
        assertThat(ValidadorDocumento.esDocumentoEspanol("FR40303265045")).isFalse();
        // En cambio esto SI tiene forma de NIF y el control esta mal.
        assertThat(ValidadorDocumento.esDocumentoEspanol("12345678A")).isTrue();
        assertThat(ValidadorDocumento.esValido("12345678A")).isFalse();
    }
}
