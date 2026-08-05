package com.motorsport19.taller.moto.web.dto;

import com.motorsport19.taller.moto.domain.Moto;

/** Version reducida para listados y desplegables. */
public record MotoResumenResponse(
        Long id,
        String matricula,
        String descripcion,
        Integer anio,
        Integer kmActual,
        boolean activo
) {

    public static MotoResumenResponse de(Moto moto) {
        return new MotoResumenResponse(
                moto.getId(),
                moto.getMatricula(),
                moto.descripcion(),
                moto.getAnio(),
                moto.getKmActual(),
                moto.isActivo());
    }
}
