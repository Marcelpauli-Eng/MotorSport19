package com.motorsport19.taller.common.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.motorsport19.taller.factura.domain.TipoFactura;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Datos mal escritos en el cuerpo de la peticion.
 *
 * <p>Una fecha imposible, una letra en un campo numerico o un valor que no esta
 * en la lista son errores del dia a dia, no averias del programa. Antes todos
 * caian en el «error inesperado, avise al administrador»: un 500 que no dice que
 * campo esta mal, no dice que se esperaba, y hace pensar al empleado que ha roto
 * algo cuando solo se ha equivocado tecleando.
 */
@DisplayName("Cuerpo de peticion ilegible")
class CuerpoIlegibleTest {

    private final ManejadorGlobalErrores manejador = new ManejadorGlobalErrores();
    private final HttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/facturas");

    @Test
    @DisplayName("un valor fuera de la lista dice cual es y cuales valen")
    void valorFueraDeLaLista() {
        var causa = InvalidFormatException.from(null,
                "no vale", "ANULACION", TipoFactura.class);
        causa.prependPath(new Object(), "tipo");

        ResponseEntity<RespuestaError> respuesta =
                manejador.cuerpoIlegible(new HttpMessageNotReadableException("x", causa, null), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().mensaje())
                .contains("ANULACION")
                .contains("tipo")
                // Lo que de verdad ayuda: enumerar lo que si se admite.
                .contains("ORDINARIA")
                .contains("RECTIFICATIVA");
        assertThat(respuesta.getBody().detalles()).containsKey("tipo");
    }

    @Test
    @DisplayName("un valor de tipo equivocado dice el campo")
    void tipoEquivocado() {
        var causa = InvalidFormatException.from(null, "no vale", "31/02/2026", java.time.LocalDate.class);
        causa.prependPath(new Object(), "fechaEmision");

        ResponseEntity<RespuestaError> respuesta =
                manejador.cuerpoIlegible(new HttpMessageNotReadableException("x", causa, null), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().mensaje()).contains("31/02/2026").contains("fechaEmision");
    }

    @Test
    @DisplayName("un cuerpo ilegible sin mas detalle pide revisar los datos")
    void cuerpoSinDetalle() {
        ResponseEntity<RespuestaError> respuesta = manejador.cuerpoIlegible(
                new HttpMessageNotReadableException("json roto",
                        new RuntimeException("lo que sea"), null), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Nunca un 500: el dato es del usuario, no una averia del programa.
        assertThat(respuesta.getBody().mensaje()).contains("Revise");
    }

    @Test
    @DisplayName("los tipos de factura de verdad salen en el mensaje")
    void enumeraLosValoresReales() {
        var causa = InvalidFormatException.from(null, "no vale", "X", TipoFactura.class);
        causa.prependPath(new Object(), "tipo");

        String mensaje = manejador.cuerpoIlegible(
                new HttpMessageNotReadableException("x", causa, null), peticion).getBody().mensaje();

        for (TipoFactura tipo : TipoFactura.values()) {
            assertThat(mensaje).contains(tipo.name());
        }
        assertThat(List.of(mensaje)).isNotEmpty();
    }
}
