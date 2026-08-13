package com.motorsport19.taller.estadisticas.service;

import java.time.LocalDate;

/**
 * Las facturas de un periodo partidas en dos por el IVA que llevan.
 *
 * <p>Las dos columnas viajan juntas porque la pantalla las enseña una al lado de
 * la otra y solo se leen comparandolas.
 */
public record InformeIva(
        LocalDate desde,
        LocalDate hasta,
        ColumnaIva conIva,
        ColumnaIva sinIva
) {
}
