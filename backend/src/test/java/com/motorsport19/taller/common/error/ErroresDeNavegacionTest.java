package com.motorsport19.taller.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.query.sqm.PathElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dos formas de llegar a un sitio que no existe.
 *
 * <p>Ninguna de las dos la teclea nadie a mano: llegan de una pantalla que quedo
 * abierta, de un enlace guardado en favoritos o de una version vieja del
 * navegador en cache. Las dos respondian «error inesperado, avise al
 * administrador» —un 500— cuando lo unico que pasa es que eso ahi no existe.
 */
@DisplayName("Navegacion hacia sitios que no existen")
class ErroresDeNavegacionTest {

    private final ManejadorGlobalErrores manejador = new ManejadorGlobalErrores();
    private final HttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/clientes");

    @Test
    @DisplayName("ordenar por una columna inexistente dice cual es y no revienta")
    void ordenacionPorColumnaQueNoExiste() {
        var causa = new InvalidDataAccessApiUsageException(
                "no se pudo",
                new PathElementException("Could not resolve attribute 'noExiste' of 'Cliente'"));

        ResponseEntity<RespuestaError> respuesta = manejador.ordenacionInvalida(causa, peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().mensaje()).contains("noExiste");
    }

    @Test
    @DisplayName("si no se sabe la columna, al menos dice como salir del atasco")
    void ordenacionSinNombreDeColumna() {
        var causa = new InvalidDataAccessApiUsageException("algo raro", new RuntimeException());

        String mensaje = manejador.ordenacionInvalida(causa, peticion).getBody().mensaje();

        assertThat(mensaje).contains("Quite la ordenacion");
    }

    @Test
    @DisplayName("un metodo que esa direccion no admite da 405 y enumera los que si")
    void metodoNoPermitido() {
        var ex = new HttpRequestMethodNotSupportedException(
                "DELETE", List.of(HttpMethod.GET.name(), HttpMethod.PUT.name()));

        ResponseEntity<RespuestaError> respuesta = manejador.metodoNoPermitido(ex, peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(respuesta.getBody().mensaje())
                .contains("DELETE")
                .contains("GET")
                .contains("PUT");
    }

    @Test
    @DisplayName("y si no hay lista de metodos, tampoco se cae")
    void metodoNoPermitidoSinAlternativas() {
        var ex = new HttpRequestMethodNotSupportedException("PATCH");

        ResponseEntity<RespuestaError> respuesta = manejador.metodoNoPermitido(ex, peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(respuesta.getBody().mensaje()).contains("PATCH");
    }
}
