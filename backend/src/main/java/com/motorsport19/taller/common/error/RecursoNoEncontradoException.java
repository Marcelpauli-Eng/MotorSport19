package com.motorsport19.taller.common.error;

/**
 * Se pide un recurso que no existe. Se traduce a HTTP 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    /** Atajo para el caso habitual: buscar por identificador. */
    public static RecursoNoEncontradoException de(String recurso, Object id) {
        return new RecursoNoEncontradoException("No se ha encontrado %s con identificador %s".formatted(recurso, id));
    }
}
