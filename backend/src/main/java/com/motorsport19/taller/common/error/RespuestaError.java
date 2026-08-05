package com.motorsport19.taller.common.error;

import java.time.Instant;
import java.util.Map;

/**
 * Cuerpo uniforme de las respuestas de error de la API.
 *
 * @param momento   instante en que se produjo el error
 * @param estado    codigo HTTP
 * @param error     nombre corto del tipo de error
 * @param mensaje   explicacion en espanol, apta para mostrar al usuario
 * @param ruta      endpoint que fallo
 * @param detalles  errores por campo en los fallos de validacion; vacio en el resto
 */
public record RespuestaError(
        Instant momento,
        int estado,
        String error,
        String mensaje,
        String ruta,
        Map<String, String> detalles
) {

    public static RespuestaError de(int estado, String error, String mensaje, String ruta) {
        return new RespuestaError(Instant.now(), estado, error, mensaje, ruta, Map.of());
    }

    public static RespuestaError deValidacion(String mensaje, String ruta, Map<String, String> detalles) {
        return new RespuestaError(Instant.now(), 400, "Datos no validos", mensaje, ruta, detalles);
    }
}
