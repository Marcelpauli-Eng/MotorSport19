package com.motorsport19.taller.seguridad;

import com.motorsport19.taller.common.error.RespuestaError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Respuesta cuando el usuario esta autenticado pero su rol no le permite hacer
 * lo que ha pedido.
 */
public class ManejadorAccesoDenegado implements AccessDeniedHandler {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuesta,
                       AccessDeniedException excepcion) throws IOException {

        respuesta.setStatus(HttpStatus.FORBIDDEN.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");

        JSON.writeValue(respuesta.getOutputStream(), RespuestaError.de(
                HttpStatus.FORBIDDEN.value(),
                "Sin permisos",
                "Su usuario no tiene permiso para realizar esta operacion.",
                peticion.getRequestURI()));
    }
}
