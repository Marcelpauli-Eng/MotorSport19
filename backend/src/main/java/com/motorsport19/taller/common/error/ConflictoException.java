package com.motorsport19.taller.common.error;

/**
 * La operacion choca con el estado actual de los datos: un duplicado, una baja
 * ya aplicada, una edicion concurrente. Se traduce a HTTP 409.
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
