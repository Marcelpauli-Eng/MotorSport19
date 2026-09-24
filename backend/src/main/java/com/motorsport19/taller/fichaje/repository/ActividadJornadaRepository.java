package com.motorsport19.taller.fichaje.repository;

import com.motorsport19.taller.fichaje.domain.Fichaje;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Que hizo alguien durante una jornada concreta.
 *
 * <p><b>No se registra nada nuevo para esto.</b> El rastro ya estaba: cada
 * cambio de estado de una OT guarda quien lo hizo, y toda entidad editable
 * lleva {@code created_by} y {@code created_at}. Montar una tabla de eventos
 * aparte habria significado instrumentar a mano cada operacion —con el riesgo
 * de olvidarse de alguna— y ademas solo sabria contar desde el dia en que se
 * encendiera. Preguntando a lo que ya hay, el historial funciona hacia atras
 * con todo lo que la base lleva guardado desde el primer dia.
 *
 * <p>Lo que no dejaba rastro por si solo —escribir un diagnostico, meter
 * lineas, tocar precios, editar fichas— se apunta en {@code actividad_usuario}
 * desde {@link com.motorsport19.taller.fichaje.service.RegistroActividad}.
 *
 * <p>Va en SQL nativo a proposito: son seis tablas sin ninguna relacion entre
 * si, unidas solo por «lo hizo esta persona en este rato». En JPQL no hay UNION.
 */
@Repository
public interface ActividadJornadaRepository
        extends org.springframework.data.repository.Repository<Fichaje, Long> {

    /**
     * @param desde inclusive; @param hasta exclusive (el fin de la jornada, o ahora si sigue abierta)
     */
    @Query(value = """
            SELECT momento, tipo, destino_id AS destinoId, titulo, detalle FROM (

                -- Todo el ciclo de una OT: abrirla, diagnosticar, presupuestar,
                -- aprobar, reparar, entregar. Es la fuente principal.
                SELECT c.fecha                                                   AS momento,
                       'OT_ESTADO'                                               AS tipo,
                       o.id                                                      AS destino_id,
                       ('OT-' || o.ejercicio || '-' || LPAD(o.numero::text, 5, '0'))::text AS titulo,
                       -- El motivo va detras de «|»: en una espera de piezas es
                       -- justo lo que interesa, que material faltaba.
                       (c.estado_nuevo || COALESCE('|' || c.motivo, ''))::text   AS detalle
                  FROM cambio_estado_ot c
                  JOIN orden_trabajo o ON o.id = c.orden_trabajo_id
                 WHERE c.usuario_id = :usuarioId
                   AND c.fecha >= :desde AND c.fecha < :hasta

                -- Toda la vida de una cita, no solo el alta: confirmarla,
                -- moverla de dia, darla por atendida, cancelarla o anotar que el
                -- cliente no vino. Con created_by solo constaba quien la escribio.
                UNION ALL
                SELECT cc.fecha, 'CITA', cc.cita_id,
                       COALESCE(m.matricula, ci.descripcion_moto, ci.contacto_nombre, '')::text,
                       (cc.estado_nuevo || CASE WHEN cc.estado_anterior IS NOT DISTINCT FROM cc.estado_nuevo
                                                THEN '|MOVIDA' ELSE '' END)::text
                  FROM cambio_estado_cita cc
                  JOIN cita ci ON ci.id = cc.cita_id
                  LEFT JOIN moto m ON m.id = ci.moto_id
                 WHERE cc.usuario_id = :usuarioId
                   AND cc.fecha >= :desde AND cc.fecha < :hasta

                -- La facturacion ya tenia su propia bitacora inmutable desde el
                -- principio (evento_factura). Se lee de ahi y no de created_by,
                -- que solo sabria contar la emision y se perderia el resto.
                -- CONSULTA fuera: abrir una factura para mirarla no es un hecho
                -- del taller, y ademas ahogaria la lista.
                UNION ALL
                SELECT ev.fecha, 'FACTURA', ev.factura_id,
                       COALESCE(f.numero_completo, '')::text, ev.tipo_evento::text
                  FROM evento_factura ev
                  LEFT JOIN factura f ON f.id = ev.factura_id
                 WHERE ev.usuario_id = :usuarioId
                   AND ev.tipo_evento <> 'CONSULTA'
                   AND ev.fecha >= :desde AND ev.fecha < :hasta

                UNION ALL
                SELECT cl.created_at, 'CLIENTE', cl.id,
                       TRIM(cl.nombre || ' ' || COALESCE(cl.apellidos, ''))::text, NULL
                  FROM cliente cl
                 WHERE cl.created_by = :usuarioId
                   AND cl.created_at >= :desde AND cl.created_at < :hasta

                UNION ALL
                SELECT mo.created_at, 'MOTO', mo.id, mo.matricula::text, NULL
                  FROM moto mo
                 WHERE mo.created_by = :usuarioId
                   AND mo.created_at >= :desde AND mo.created_at < :hasta

                UNION ALL
                SELECT p.created_at, 'PIEZA', p.id,
                       (p.sku || ' · ' || p.descripcion)::text, NULL
                  FROM pieza p
                 WHERE p.created_by = :usuarioId
                   AND p.created_at >= :desde AND p.created_at < :hasta

                -- La bitacora: el trabajo que no mueve estados ni crea nada
                -- (diagnosticos, lineas, precios, ediciones de fichas). Se
                -- apunta al hacerlo, asi que no se pisa ni se pierde.
                UNION ALL
                SELECT au.fecha, 'BITACORA:' || au.tipo, au.destino_id, au.texto::text, au.destino_tipo::text
                  FROM actividad_usuario au
                 WHERE au.usuario_id = :usuarioId
                   AND au.fecha >= :desde AND au.fecha < :hasta

                -- Las ediciones de fichas de ANTES de la bitacora. De estas solo
                -- sobrevive la ultima, porque updated_by se pisa a si mismo. Las
                -- que ya estan en la bitacora se descartan para no salir dos veces.
                UNION ALL
                SELECT cl.updated_at, 'EDICION', cl.id,
                       TRIM(cl.nombre || ' ' || COALESCE(cl.apellidos, ''))::text, 'cliente'
                  FROM cliente cl
                 WHERE cl.updated_by = :usuarioId
                   AND cl.updated_at <> cl.created_at
                   AND cl.updated_at >= :desde AND cl.updated_at < :hasta
                   AND NOT EXISTS (
                       SELECT 1 FROM actividad_usuario au
                        WHERE au.destino_tipo = 'cliente' AND au.destino_id = cl.id
                          AND au.usuario_id = cl.updated_by
                          AND au.fecha BETWEEN cl.updated_at - INTERVAL '5 seconds'
                                           AND cl.updated_at + INTERVAL '5 seconds')

                -- La moto y la pieza se «editan» solas todo el rato: al abrir una
                -- OT se les guarda el kilometraje, y al consumir material cambia
                -- el stock. Eso deja updated_by puesto sin que nadie haya tocado
                -- la ficha, y sale una linea «Editó la moto» pegada a la de
                -- «abrió la orden» diciendo lo mismo dos veces. Se descartan las
                -- que coinciden con un hecho ya anotado de la misma persona.
                UNION ALL
                SELECT mo.updated_at, 'EDICION', mo.id, mo.matricula::text, 'moto'
                  FROM moto mo
                 WHERE mo.updated_by = :usuarioId
                   AND mo.updated_at <> mo.created_at
                   AND mo.updated_at >= :desde AND mo.updated_at < :hasta
                   AND NOT EXISTS (
                       SELECT 1 FROM cambio_estado_ot c
                         JOIN orden_trabajo o ON o.id = c.orden_trabajo_id
                        WHERE o.moto_id = mo.id
                          AND c.usuario_id = mo.updated_by
                          AND c.fecha BETWEEN mo.updated_at - INTERVAL '5 seconds'
                                          AND mo.updated_at + INTERVAL '5 seconds')
                   AND NOT EXISTS (
                       SELECT 1 FROM actividad_usuario au
                        WHERE au.destino_tipo = 'moto' AND au.destino_id = mo.id
                          AND au.usuario_id = mo.updated_by
                          AND au.fecha BETWEEN mo.updated_at - INTERVAL '5 seconds'
                                           AND mo.updated_at + INTERVAL '5 seconds')

                UNION ALL
                SELECT p.updated_at, 'EDICION', p.id, p.sku::text, 'inventario'
                  FROM pieza p
                 WHERE p.updated_by = :usuarioId
                   AND p.updated_at <> p.created_at
                   AND p.updated_at >= :desde AND p.updated_at < :hasta
                   AND NOT EXISTS (
                       SELECT 1 FROM movimiento_stock ms
                        WHERE ms.pieza_id = p.id
                          AND ms.usuario_id = p.updated_by
                          AND ms.fecha BETWEEN p.updated_at - INTERVAL '5 seconds'
                                           AND p.updated_at + INTERVAL '5 seconds')
                   AND NOT EXISTS (
                       SELECT 1 FROM actividad_usuario au
                        WHERE au.destino_tipo = 'inventario' AND au.destino_id = p.id
                          AND au.usuario_id = p.updated_by
                          AND au.fecha BETWEEN p.updated_at - INTERVAL '5 seconds'
                                           AND p.updated_at + INTERVAL '5 seconds')

                -- Solo el movimiento de almacen que alguien decide: recepcion de
                -- pedido y ajuste de inventario. Las SALIDAS no: las genera sola
                -- la OT al entrar en reparacion, una por pieza, y ahogarian la
                -- lista repitiendo algo que ya cuenta el cambio de estado.
                UNION ALL
                SELECT ms.fecha, 'STOCK', ms.pieza_id, p.sku::text, ms.tipo::text
                  FROM movimiento_stock ms
                  JOIN pieza p ON p.id = ms.pieza_id
                 WHERE ms.usuario_id = :usuarioId
                   AND ms.tipo IN ('ENTRADA', 'AJUSTE')
                   AND ms.fecha >= :desde AND ms.fecha < :hasta

            ) a ORDER BY momento
            """, nativeQuery = true)
    List<FilaActividad> deLaJornada(@Param("usuarioId") Long usuarioId,
                                    @Param("desde") Instant desde,
                                    @Param("hasta") Instant hasta);

    /** Proyeccion cruda. El texto legible se arma en Java, no en SQL. */
    interface FilaActividad {
        Instant getMomento();
        String getTipo();
        Long getDestinoId();
        String getTitulo();
        String getDetalle();
    }
}
