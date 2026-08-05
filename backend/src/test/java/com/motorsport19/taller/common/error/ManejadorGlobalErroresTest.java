package com.motorsport19.taller.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del traductor de errores.
 *
 * <p>Lo que se comprueba aqui importa mas de lo que parece: las reglas de
 * integridad que impone PostgreSQL mediante triggers (inmutabilidad de las
 * facturas, stock que no puede quedar negativo, OT entregadas que no se tocan)
 * llegan a Java como una excepcion generica de acceso a datos. Si no se
 * traducen, el usuario ve un 500 sin explicacion en vez del motivo real, que
 * ademas ya viene redactado en espanol desde la base de datos.
 */
@DisplayName("Traductor de errores a respuestas HTTP")
class ManejadorGlobalErroresTest {

    private ManejadorGlobalErrores manejador;
    private HttpServletRequest peticion;

    @BeforeEach
    void preparar() {
        manejador = new ManejadorGlobalErrores();
        peticion = new MockHttpServletRequest("POST", "/api/inventario/movimientos");
    }

    /** Simula como llega a Java un RAISE EXCEPTION lanzado por un trigger. */
    private DataIntegrityViolationException errorDeTrigger(String sqlState, String mensajeTrigger) {
        SQLException sql = new SQLException(
                "ERROR: " + mensajeTrigger + "\n  Where: PL/pgSQL function fn_bloquear_modificacion() line 3 at RAISE",
                sqlState);
        return new DataIntegrityViolationException("could not execute statement", sql);
    }

    @Test
    @DisplayName("la inmutabilidad de una factura se convierte en 422 con el mensaje del trigger")
    void inmutabilidadDeFactura() {
        ResponseEntity<RespuestaError> respuesta = manejador.integridad(
                errorDeTrigger("23001", "Los registros de \"factura\" son inmutables: no se permite UPDATE (id 1)"),
                peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().mensaje())
                .isEqualTo("Los registros de \"factura\" son inmutables: no se permite UPDATE (id 1)");
        // Ni rastro del "Where: PL/pgSQL function..." ni del prefijo ERROR:
        assertThat(respuesta.getBody().mensaje()).doesNotContain("PL/pgSQL");
    }

    @Test
    @DisplayName("el stock insuficiente del trigger se convierte en 422")
    void stockNegativoBloqueadoPorLaBaseDeDatos() {
        ResponseEntity<RespuestaError> respuesta = manejador.integridad(
                errorDeTrigger("23514",
                        "Stock insuficiente para la pieza ESP-RET-DER (16): disponible 0.000, solicitado 1.000"),
                peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(respuesta.getBody().mensaje()).contains("ESP-RET-DER");
    }

    @Test
    @DisplayName("una clave duplicada se convierte en 409")
    void claveDuplicada() {
        ResponseEntity<RespuestaError> respuesta = manejador.integridad(
                errorDeTrigger("23505", "duplicate key value violates unique constraint \"ux_cliente_documento\""),
                peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().error()).isEqualTo("Registro duplicado");
    }

    @Test
    @DisplayName("una clave ajena en uso se convierte en 409")
    void claveAjena() {
        ResponseEntity<RespuestaError> respuesta = manejador.integridad(
                errorDeTrigger("23503", "update or delete on table \"pieza\" violates foreign key constraint"),
                peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().mensaje()).contains("referenciado");
    }

    @Test
    @DisplayName("cualquier otra violacion de integridad se trata como regla de negocio")
    void otraViolacionDeIntegridad() {
        ResponseEntity<RespuestaError> respuesta = manejador.integridad(
                errorDeTrigger("23000", "Numeracion no correlativa en la serie A: se esperaba el numero 4, llego 5"),
                peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(respuesta.getBody().mensaje()).contains("Numeracion no correlativa");
    }

    @Test
    @DisplayName("un error sin SQLSTATE identificable no revienta el traductor")
    void sinSqlState() {
        ResponseEntity<RespuestaError> respuesta = manejador.integridad(
                new DataIntegrityViolationException("fallo generico"), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().mensaje()).isNotBlank();
    }

    @Test
    @DisplayName("la edicion concurrente pide recargar en vez de mostrar la excepcion tecnica")
    void edicionConcurrente() {
        ResponseEntity<RespuestaError> respuesta = manejador.edicionConcurrente(
                new OptimisticLockingFailureException("Row was updated by another transaction"), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().mensaje()).contains("Vuelva a cargarlo");
    }

    @Test
    @DisplayName("las excepciones de dominio conservan su codigo HTTP")
    void excepcionesDeDominio() {
        assertThat(manejador.noEncontrado(
                RecursoNoEncontradoException.de("la pieza", 99), peticion).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(manejador.conflicto(
                new ConflictoException("Ya existe"), peticion).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(manejador.reglaNegocio(
                new ReglaNegocioException("Regla incumplida"), peticion).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
