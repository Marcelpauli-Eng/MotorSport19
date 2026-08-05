package com.motorsport19.taller.common.error;

/**
 * La peticion es sintacticamente correcta pero rompe una regla del negocio.
 * Se traduce a HTTP 422.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
