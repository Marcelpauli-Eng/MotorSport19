package com.motorsport19.taller.solicitud.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.motorsport19.taller.common.error.RespuestaError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * La unica puerta de la API que no pide usuario: la de las solicitudes de la web.
 *
 * <p>No lleva token porque quien llama no es una persona del taller sino la
 * funcion de la web publica. A cambio tiene que presentar una clave compartida
 * en la cabecera {@value #CABECERA}. Por delante esta ademas Cloudflare Access,
 * que solo deja pasar a esa funcion con su token de servicio; esta clave es la
 * segunda cerradura, por si algun dia alguien quita la primera.
 *
 * <p>Sin clave configurada la puerta esta cerrada: un taller que no usa la web
 * no tiene por que tener nada abierto.
 */
@Component
public class FiltroClaveWeb extends OncePerRequestFilter {

    static final String CABECERA = "X-Clave-Web";
    static final String RUTA = "/publico/";

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private final byte[] clave;

    public FiltroClaveWeb(@Value("${motorsport19.solicitudes-web.clave:}") String clave) {
        this.clave = comprobar(clave);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return !peticion.getRequestURI().substring(peticion.getContextPath().length()).startsWith(RUTA);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        if (clave == null) {
            responder(respuesta, peticion, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Entrada cerrada", "Este taller no recibe solicitudes de la web.");
            return;
        }
        String recibida = peticion.getHeader(CABECERA);
        // Comparacion en tiempo constante: medir cuanto tarda en fallar no dice
        // cuantos caracteres se han acertado.
        if (recibida == null || !MessageDigest.isEqual(clave, recibida.getBytes(StandardCharsets.UTF_8))) {
            responder(respuesta, peticion, HttpServletResponse.SC_UNAUTHORIZED,
                    "No autorizado", "Falta la clave de la web o no es la buena.");
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private static void responder(HttpServletResponse respuesta, HttpServletRequest peticion,
                                  int estado, String error, String mensaje) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        JSON.writeValue(respuesta.getOutputStream(), RespuestaError.de(estado, error, mensaje, peticion.getRequestURI()));
    }

    /**
     * Vacia cierra la puerta; corta impide arrancar, igual que la clave de los
     * tokens: una clave que se adivina es peor que no tener puerta.
     */
    static byte[] comprobar(String clave) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        byte[] bytes = clave.trim().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "La clave de las solicitudes web debe tener al menos 32 caracteres. "
                    + "Genere una con: openssl rand -base64 48");
        }
        return bytes;
    }
}
