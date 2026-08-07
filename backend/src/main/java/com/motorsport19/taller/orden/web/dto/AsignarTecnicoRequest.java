package com.motorsport19.taller.orden.web.dto;

/**
 * Tecnico que se hace cargo de la orden.
 *
 * @param tecnicoId nulo para dejarla sin asignar y devolverla al tablero comun
 */
public record AsignarTecnicoRequest(Long tecnicoId) {
}
