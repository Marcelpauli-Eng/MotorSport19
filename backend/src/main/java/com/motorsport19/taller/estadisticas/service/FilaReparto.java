package com.motorsport19.taller.estadisticas.service;

import java.math.BigDecimal;

/** Una linea de un ranking: un nombre, un importe y un recuento. */
public record FilaReparto(String nombre, BigDecimal importe, int unidades) {
}
