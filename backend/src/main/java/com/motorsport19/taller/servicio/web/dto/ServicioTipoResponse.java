package com.motorsport19.taller.servicio.web.dto;

import com.motorsport19.taller.servicio.domain.LineaServicioTipo;
import com.motorsport19.taller.servicio.domain.ServicioTipo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ficha de una plantilla.
 *
 * <p>Lleva {@code horasTotales} y {@code numeroDePiezas} calculados: es lo que
 * se lee al elegir un servicio en la OT («esto son 2,5 h y 4 piezas») y
 * pedirle al navegador que lo sume de las lineas seria repetir en TypeScript
 * una regla que ya vive en el dominio.
 */
public record ServicioTipoResponse(
        Long id,
        String nombre,
        String descripcion,
        boolean activo,
        BigDecimal horasTotales,
        long numeroDePiezas,
        List<LineaServicioTipoResponse> lineas
) {

    public static ServicioTipoResponse de(ServicioTipo servicio) {
        return new ServicioTipoResponse(
                servicio.getId(),
                servicio.getNombre(),
                servicio.getDescripcion(),
                servicio.isActivo(),
                servicio.horasTotales(),
                servicio.numeroDePiezas(),
                servicio.getLineas().stream().map(LineaServicioTipoResponse::de).toList());
    }

    public record LineaServicioTipoResponse(
            Long id,
            Integer numeroLinea,
            String tipo,
            /** Texto que se enseña: el propio en mano de obra, el del catalogo en piezas. */
            String descripcion,
            Long piezaId,
            String piezaSku,
            BigDecimal cantidad
    ) {

        public static LineaServicioTipoResponse de(LineaServicioTipo linea) {
            return new LineaServicioTipoResponse(
                    linea.getId(),
                    linea.getNumeroLinea(),
                    linea.getTipo().name(),
                    linea.textoVisible(),
                    linea.getPieza() == null ? null : linea.getPieza().getId(),
                    linea.getPieza() == null ? null : linea.getPieza().getSku(),
                    linea.getCantidad());
        }
    }
}
