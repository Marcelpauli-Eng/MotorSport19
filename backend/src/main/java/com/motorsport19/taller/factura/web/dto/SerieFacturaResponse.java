package com.motorsport19.taller.factura.web.dto;

import com.motorsport19.taller.factura.domain.SerieFactura;
import com.motorsport19.taller.factura.domain.TipoFactura;

/**
 * @param proximoNumero numero que llevara la siguiente factura de esta serie
 */
public record SerieFacturaResponse(
        Long id,
        String codigo,
        Integer ejercicio,
        String descripcion,
        TipoFactura tipo,
        Integer ultimoNumero,
        Integer proximoNumero,
        boolean activa,
        /** Serie reservada a facturas simplificadas. */
        boolean simplificada
) {

    public static SerieFacturaResponse de(SerieFactura serie) {
        return new SerieFacturaResponse(
                serie.getId(), serie.getCodigo(), serie.getEjercicio(), serie.getDescripcion(),
                serie.getTipo(), serie.getUltimoNumero(), serie.getUltimoNumero() + 1,
                serie.isActiva(),
                serie.isSimplificada());
    }
}
