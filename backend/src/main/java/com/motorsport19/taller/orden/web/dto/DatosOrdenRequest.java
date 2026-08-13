package com.motorsport19.taller.orden.web.dto;

import java.time.LocalDate;

/**
 * Datos sueltos de la cabecera de la orden.
 *
 * @param fechaEstimadaSalida cuando se le dijo al cliente que la tendria
 * @param observaciones       notas internas del taller; no salen en el
 *                            presupuesto ni en la factura
 */
public record DatosOrdenRequest(LocalDate fechaEstimadaSalida, String observaciones) {
}
