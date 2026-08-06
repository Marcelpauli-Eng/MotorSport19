package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.common.error.RespuestaError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Respuesta cuando falta el token o no vale.
 *
 * <p>Devuelve el mismo formato de error que el resto de la API en vez de la
 * pagina de login de Spring: el cliente es una aplicacion Angular, no un
 * navegador esperando un formulario.
 */
public class PuntoEntradaNoAutenticado implements AuthenticationEntryPoint {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta,
                         AuthenticationException excepcion) throws IOException {

        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");

        JSON.writeValue(respuesta.getOutputStream(), RespuestaError.de(
                HttpStatus.UNAUTHORIZED.value(),
                "No autenticado",
                "Su sesion ha caducado o no ha iniciado sesion. Vuelva a entrar.",
                peticion.getRequestURI()));
    }
}
