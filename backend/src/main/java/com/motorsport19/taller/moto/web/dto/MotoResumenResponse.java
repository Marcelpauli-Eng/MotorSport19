package com.motorsport19.taller.moto.web.dto;

import com.motorsport19.taller.moto.domain.Moto;

/**
 * Version reducida para listados y desplegables.
 *
 * <p>Lleva el nombre del propietario porque en el mostrador se busca por el
 * cliente antes que por la matricula: quien atiende recuerda «la Yamaha de
 * Carlos», no «la 1234 JKL».
 */
public record MotoResumenResponse(
        Long id,
        String matricula,
        String descripcion,
        Integer anio,
        Integer kmActual,
        Long clienteId,
        String clienteNombre,
        boolean activo
) {

    public static MotoResumenResponse de(Moto moto) {
        return new MotoResumenResponse(
                moto.getId(),
                moto.getMatricula(),
                moto.descripcion(),
                moto.getAnio(),
                moto.getKmActual(),
                moto.getCliente().getId(),
                moto.getCliente().nombreCompleto(),
                moto.isActivo());
    }
}
