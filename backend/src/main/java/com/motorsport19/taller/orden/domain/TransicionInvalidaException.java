package com.motorsport19.taller.orden.domain;

import com.motorsport19.taller.common.error.ReglaNegocioException;
import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Se ha intentado un salto de estado que la maquina de estados no permite.
 *
 * <p>El mensaje incluye a que estados SI se puede ir, para que quien esta en
 * mostrador sepa que hacer en vez de quedarse con un "operacion no permitida".
 */
@Getter
public class TransicionInvalidaException extends ReglaNegocioException {

    private final EstadoOT estadoActual;
    private final EstadoOT estadoSolicitado;
    private final Set<EstadoOT> estadosPosibles;

    public TransicionInvalidaException(String codigoOt, EstadoOT actual, EstadoOT solicitado) {
        super(construirMensaje(codigoOt, actual, solicitado));
        this.estadoActual = actual;
        this.estadoSolicitado = solicitado;
        this.estadosPosibles = actual.siguientesPosibles();
    }

    private static String construirMensaje(String codigoOt, EstadoOT actual, EstadoOT solicitado) {
        String referencia = codigoOt == null ? "La orden de trabajo" : "La orden de trabajo " + codigoOt;

        if (actual.esTerminal()) {
            return "%s esta %s y no admite mas cambios de estado."
                    .formatted(referencia, actual.getDescripcion().toUpperCase());
        }

        String posibles = actual.siguientesPosibles().stream()
                .map(EstadoOT::name)
                .sorted()
                .collect(Collectors.joining(", "));

        return "%s esta en estado %s y no puede pasar a %s. Desde aqui solo puede pasar a: %s."
                .formatted(referencia, actual.name(), solicitado.name(), posibles);
    }
}
