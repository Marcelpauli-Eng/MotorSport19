package com.motorsport19.taller.cliente.domain;

/**
 * Tipo de documento fiscal identificativo del cliente.
 */
public enum TipoDocumento {

    /** Numero de identificacion fiscal de persona fisica espanola. */
    NIF,

    /** Codigo de identificacion fiscal de persona juridica. */
    CIF,

    /** Numero de identidad de extranjero. */
    NIE,

    PASAPORTE,

    OTRO
}
