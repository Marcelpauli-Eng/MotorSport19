package com.motorsport19.taller.orden.web.dto;

import com.motorsport19.taller.orden.domain.EstadoOT;
import com.motorsport19.taller.orden.service.PiezaFaltante;
import com.motorsport19.taller.orden.service.ResultadoConsumo;

import java.util.List;

/**
 * Resultado de intentar entrar en reparacion.
 *
 * <p>Cuando falta material la peticion NO falla: la orden queda en
 * {@code ESPERANDO_PIEZAS} y aqui viene el detalle de lo que hay que pedir al
 * proveedor. Es informacion util, no un error.
 *
 * @param estado     estado en el que ha quedado la orden
 * @param consumidas lineas de pieza que se han podido servir
 * @param faltantes  lo que no habia en almacen, con las unidades que faltan
 * @param mensaje    resumen listo para mostrar en pantalla
 */
public record ResultadoConsumoResponse(
        EstadoOT estado,
        String estadoDescripcion,
        boolean completo,
        int consumidas,
        List<PiezaFaltante> faltantes,
        String mensaje
) {

    public static ResultadoConsumoResponse de(ResultadoConsumo resultado) {
        String mensaje = resultado.completo()
                ? "Material servido: la orden entra en reparacion."
                : resultado.descripcionDeFaltantes();

        return new ResultadoConsumoResponse(
                resultado.estadoResultante(),
                resultado.estadoResultante().getDescripcion(),
                resultado.completo(),
                resultado.consumidas(),
                resultado.faltantes(),
                mensaje);
    }
}
