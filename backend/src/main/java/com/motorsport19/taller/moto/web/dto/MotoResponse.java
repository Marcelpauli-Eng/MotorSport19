package com.motorsport19.taller.moto.web.dto;

import com.motorsport19.taller.moto.domain.Moto;

import java.time.Instant;

public record MotoResponse(
        Long id,
        Long clienteId,
        String clienteNombre,
        String matricula,
        String marca,
        String modelo,
        String descripcion,
        Integer anio,
        Integer cilindrada,
        String color,
        String numeroBastidor,
        Integer kmActual,
        String observaciones,
        boolean activo,
        Instant fechaBaja
) {

    public static MotoResponse de(Moto moto) {
        return new MotoResponse(
                moto.getId(),
                moto.getCliente().getId(),
                moto.getCliente().nombreCompleto(),
                moto.getMatricula(),
                moto.getMarca(),
                moto.getModelo(),
                moto.descripcion(),
                moto.getAnio(),
                moto.getCilindrada(),
                moto.getColor(),
                moto.getNumeroBastidor(),
                moto.getKmActual(),
                moto.getObservaciones(),
                moto.isActivo(),
                moto.getFechaBaja());
    }
}
