package com.motorsport19.taller.estadisticas.repository;

import com.motorsport19.taller.estadisticas.service.FilaMes;
import com.motorsport19.taller.estadisticas.service.FilaMesIva;
import com.motorsport19.taller.estadisticas.service.FilaReparto;
import com.motorsport19.taller.estadisticas.service.TrabajoSinFacturar;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Consultas de agregacion para los informes.
 *
 * <p>Van en SQL nativo a proposito. Son sumas por mes con varias fuentes
 * distintas (facturas, desglose de IVA, movimientos de almacen); escribirlas en
 * JPQL obligaria a traerse las filas a memoria o a inventar entidades que no
 * existen en el dominio. Aqui la base de datos hace lo que sabe hacer.
 *
 * <p>Todas las consultas se apoyan en un calendario de doce meses generado con
 * {@code generate_series}, de modo que un mes sin actividad sale con ceros en
 * lugar de desaparecer. Un grafico al que le faltan meses miente sobre la forma
 * de la curva.
 */
@Repository
public class EstadisticasRepository {

    private final EntityManager em;

    public EstadisticasRepository(EntityManager em) {
        this.em = em;
    }

    /**
     * Un año de facturacion, mes a mes.
     *
     * <p>Cada mes trae lo facturado, lo comprado y el coste del material que se
     * ha ido en las ordenes ya facturadas.
     *
     * <p>Dos decisiones que conviene conocer al leer los numeros:
     *
     * <ul>
     *   <li><b>Las rectificativas restan.</b> Sus importes ya estan guardados en
     *       negativo, asi que basta con sumarlas: un mes con una rectificativa
     *       grande baja, que es justo lo que ha pasado de verdad.</li>
     *   <li><b>El coste del material se imputa al mes de la factura</b>, no al
     *       mes en que salio del almacen. Asi el margen de cada mes compara
     *       ingresos y costes del mismo trabajo. Se calcula desde los
     *       movimientos de la orden, con el precio de coste que tenia la pieza
     *       cuando salio, no con el de hoy.</li>
     * </ul>
     */
    public List<FilaMes> facturacionMensual(int ejercicio) {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery("""
            WITH meses AS (
                SELECT generate_series(1, 12) AS mes
            ),
            -- Lo facturado, con las rectificativas restando.
            facturado AS (
                SELECT EXTRACT(MONTH FROM f.fecha_emision)::int AS mes,
                       SUM(f.base_imponible)                    AS base,
                       SUM(f.total_iva)                         AS iva,
                       SUM(f.total)                             AS total,
                       COUNT(*)                                 AS facturas
                  FROM factura f
                 WHERE EXTRACT(YEAR FROM f.fecha_emision) = :ejercicio
                 GROUP BY 1
            ),
            -- Reparto entre mano de obra y piezas.
            reparto AS (
                SELECT EXTRACT(MONTH FROM f.fecha_emision)::int AS mes,
                       SUM(CASE WHEN l.tipo = 'MANO_DE_OBRA' THEN l.base_imponible ELSE 0 END) AS mano_obra,
                       SUM(CASE WHEN l.tipo = 'PIEZA'        THEN l.base_imponible ELSE 0 END) AS piezas
                  FROM linea_factura l
                  JOIN factura f ON f.id = l.factura_id
                 WHERE EXTRACT(YEAR FROM f.fecha_emision) = :ejercicio
                 GROUP BY 1
            ),
            -- Compras de material: entradas de almacen valoradas a su coste real.
            compras AS (
                SELECT EXTRACT(MONTH FROM m.fecha)::int AS mes,
                       SUM(m.cantidad * COALESCE(m.precio_coste_unitario, p.precio_coste)) AS base,
                       SUM(m.cantidad * COALESCE(m.precio_coste_unitario, p.precio_coste)
                           * COALESCE(t.porcentaje, 0) / 100)                              AS iva
                  FROM movimiento_stock m
                  JOIN pieza p    ON p.id = m.pieza_id
             LEFT JOIN tipo_iva t ON t.codigo = p.tipo_iva
                 WHERE m.tipo = 'ENTRADA'
                   AND m.orden_trabajo_id IS NULL
                   AND EXTRACT(YEAR FROM m.fecha) = :ejercicio
                 GROUP BY 1
            ),
            -- Coste del material consumido, imputado al mes de la factura.
            coste AS (
                SELECT EXTRACT(MONTH FROM f.fecha_emision)::int AS mes,
                       SUM(-m.cantidad * COALESCE(m.precio_coste_unitario, p.precio_coste)) AS coste
                  FROM movimiento_stock m
                  JOIN pieza p   ON p.id = m.pieza_id
                  JOIN factura f ON f.orden_trabajo_id = m.orden_trabajo_id
                 WHERE m.orden_trabajo_id IS NOT NULL
                   AND EXTRACT(YEAR FROM f.fecha_emision) = :ejercicio
                 GROUP BY 1
            ),
            -- Ordenes entradas en el taller cada mes.
            ordenes AS (
                SELECT EXTRACT(MONTH FROM o.fecha_entrada)::int AS mes,
                       COUNT(*)                                 AS abiertas
                  FROM orden_trabajo o
                 WHERE EXTRACT(YEAR FROM o.fecha_entrada) = :ejercicio
                 GROUP BY 1
            )
            SELECT ms.mes,
                   COALESCE(fa.base, 0),
                   COALESCE(fa.iva, 0),
                   COALESCE(fa.total, 0),
                   COALESCE(fa.facturas, 0),
                   COALESCE(re.mano_obra, 0),
                   COALESCE(re.piezas, 0),
                   COALESCE(co.base, 0),
                   COALESCE(co.iva, 0),
                   COALESCE(cs.coste, 0),
                   COALESCE(od.abiertas, 0)
              FROM meses ms
         LEFT JOIN facturado fa ON fa.mes = ms.mes
         LEFT JOIN reparto   re ON re.mes = ms.mes
         LEFT JOIN compras   co ON co.mes = ms.mes
         LEFT JOIN coste     cs ON cs.mes = ms.mes
         LEFT JOIN ordenes   od ON od.mes = ms.mes
             ORDER BY ms.mes
            """)
                .setParameter("ejercicio", ejercicio)
                .getResultList();

        return filas.stream().map(f -> new FilaMes(
                num(f[0]).intValue(),
                dec(f[1]), dec(f[2]), dec(f[3]), num(f[4]).intValue(),
                dec(f[5]), dec(f[6]),
                dec(f[7]), dec(f[8]),
                dec(f[9]),
                num(f[10]).intValue()
        )).toList();
    }

    /**
     * Lo mismo, pero partido en dos por el IVA de la factura y para un rango
     * cualquiera de fechas.
     *
     * <p>Una factura cuenta como <b>sin IVA</b> cuando su cuota es cero. No se
     * mira el porcentaje del desglose, sino lo que se cobro de verdad: es lo que
     * separa las facturas que se emitieron al 0 % de las demas, sin depender de
     * como se llame el tipo de IVA en la configuracion.
     *
     * <p>Diferencia deliberada con {@link #facturacionMensual(int)}: aqui el
     * coste del material solo se imputa a las facturas <b>ordinarias</b>. Si se
     * contara tambien en las rectificativas, un mismo juego de piezas aparecería
     * dos veces, y con una rectificativa al 0 % sobre una factura con IVA el
     * mismo coste caeria ademas en las dos columnas.
     *
     * <p>Los meses salen de un calendario generado sobre el rango pedido, y cada
     * mes aparece en los dos grupos aunque uno de ellos no tenga ninguna factura:
     * dos columnas que no comparten los mismos meses no se pueden comparar.
     */
    public List<FilaMesIva> facturacionPorIva(LocalDate desde, LocalDate hasta) {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery("""
            WITH meses AS (
                SELECT generate_series(date_trunc('month', CAST(:desde AS date)),
                                       date_trunc('month', CAST(:hasta AS date)),
                                       interval '1 month')::date AS inicio
            ),
            grupos AS (
                SELECT unnest(ARRAY[true, false]) AS con_iva
            ),
            -- Cada factura del periodo, ya clasificada y con su mes.
            clasificadas AS (
                SELECT f.id,
                       f.tipo,
                       f.orden_trabajo_id,
                       date_trunc('month', f.fecha_emision)::date AS inicio,
                       (f.total_iva <> 0)                         AS con_iva,
                       f.base_imponible,
                       f.total_iva,
                       f.total
                  FROM factura f
                 WHERE f.fecha_emision BETWEEN :desde AND :hasta
            ),
            facturado AS (
                SELECT inicio, con_iva,
                       SUM(base_imponible) AS base,
                       SUM(total_iva)      AS iva,
                       SUM(total)          AS total,
                       COUNT(*)            AS facturas
                  FROM clasificadas
                 GROUP BY 1, 2
            ),
            reparto AS (
                SELECT c.inicio, c.con_iva,
                       SUM(CASE WHEN l.tipo = 'MANO_DE_OBRA' THEN l.base_imponible ELSE 0 END) AS mano_obra,
                       SUM(CASE WHEN l.tipo = 'PIEZA'        THEN l.base_imponible ELSE 0 END) AS piezas
                  FROM linea_factura l
                  JOIN clasificadas c ON c.id = l.factura_id
                 GROUP BY 1, 2
            ),
            coste AS (
                SELECT c.inicio, c.con_iva,
                       SUM(-m.cantidad * COALESCE(m.precio_coste_unitario, p.precio_coste)) AS coste
                  FROM movimiento_stock m
                  JOIN pieza p        ON p.id = m.pieza_id
                  JOIN clasificadas c ON c.orden_trabajo_id = m.orden_trabajo_id
                 WHERE m.orden_trabajo_id IS NOT NULL
                   AND m.cantidad < 0
                   AND c.tipo = 'ORDINARIA'
                 GROUP BY 1, 2
            )
            SELECT ms.inicio,
                   g.con_iva,
                   COALESCE(fa.base, 0),
                   COALESCE(fa.iva, 0),
                   COALESCE(fa.total, 0),
                   COALESCE(fa.facturas, 0),
                   COALESCE(re.mano_obra, 0),
                   COALESCE(re.piezas, 0),
                   COALESCE(cs.coste, 0)
              FROM meses ms
             CROSS JOIN grupos g
         LEFT JOIN facturado fa ON fa.inicio = ms.inicio AND fa.con_iva = g.con_iva
         LEFT JOIN reparto   re ON re.inicio = ms.inicio AND re.con_iva = g.con_iva
         LEFT JOIN coste     cs ON cs.inicio = ms.inicio AND cs.con_iva = g.con_iva
             ORDER BY g.con_iva DESC, ms.inicio
            """)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();

        return filas.stream().map(f -> {
            LocalDate inicio = ((java.sql.Date) f[0]).toLocalDate();
            return new FilaMesIva(
                    inicio.getYear(),
                    inicio.getMonthValue(),
                    (Boolean) f[1],
                    dec(f[2]), dec(f[3]), dec(f[4]), num(f[5]).intValue(),
                    dec(f[6]), dec(f[7]),
                    dec(f[8]));
        }).toList();
    }

    /**
     * Primera y ultima fecha de emision del libro, o {@code null} si no hay
     * ninguna factura todavia.
     *
     * <p>Sirve para que un informe sin fechas abarque lo que hay de verdad en
     * lugar de dar por hecho el año en curso.
     */
    public LocalDate[] rangoDelLibro() {
        Object[] fila = (Object[]) em.createNativeQuery(
                "SELECT MIN(fecha_emision), MAX(fecha_emision) FROM factura")
                .getSingleResult();

        if (fila[0] == null || fila[1] == null) {
            return null;
        }
        return new LocalDate[]{
                ((java.sql.Date) fila[0]).toLocalDate(),
                ((java.sql.Date) fila[1]).toLocalDate()};
    }

    /**
     * Ordenes terminadas que no tienen factura.
     *
     * <p>Se mira que no exista una ORDINARIA suya: una rectificativa no factura
     * el trabajo, lo corrige.
     *
     * <p>Sin filtro de fechas a proposito. Esto no es un dato del ejercicio que
     * se este mirando, es una lista de tareas: lo que falta por cobrar hoy,
     * aunque el trabajo se hiciera el año pasado.
     */
    public List<TrabajoSinFacturar.Fila> trabajoSinFacturar() {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery("""
            SELECT o.id,
                   o.codigo,
                   o.estado,
                   TRIM(c.nombre || ' ' || COALESCE(c.apellidos, '')),
                   m.matricula,
                   o.fecha_real_salida,
                   COALESCE(SUM(l.total), 0)
              FROM orden_trabajo o
              JOIN cliente c ON c.id = o.cliente_id
              JOIN moto m    ON m.id = o.moto_id
         LEFT JOIN linea_ot l ON l.orden_trabajo_id = o.id
             WHERE o.estado IN ('LISTA', 'ENTREGADA')
               AND NOT EXISTS (SELECT 1 FROM factura f
                                WHERE f.orden_trabajo_id = o.id
                                  AND f.tipo = 'ORDINARIA')
             GROUP BY o.id, o.codigo, o.estado, c.nombre, c.apellidos, m.matricula, o.fecha_real_salida
             ORDER BY 7 DESC
            """).getResultList();

        return filas.stream().map(f -> new TrabajoSinFacturar.Fila(
                ((Number) f[0]).longValue(),
                (String) f[1],
                (String) f[2],
                (String) f[3],
                (String) f[4],
                dia(f[5]),
                dec(f[6]))).toList();
    }

    /** Ejercicios con actividad, para el desplegable de año. */
    @SuppressWarnings("unchecked")
    public List<Integer> ejerciciosConFacturas() {
        List<Number> anios = em.createNativeQuery("""
            SELECT DISTINCT EXTRACT(YEAR FROM fecha_emision)::int
              FROM factura
             ORDER BY 1 DESC
            """).getResultList();
        return anios.stream().map(Number::intValue).toList();
    }

    /** Los clientes que mas facturan en un periodo. */
    public List<FilaReparto> mejoresClientes(LocalDate desde, LocalDate hasta, int limite) {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery("""
            SELECT f.receptor_nombre, SUM(f.total), COUNT(*)
              FROM factura f
             WHERE f.fecha_emision BETWEEN :desde AND :hasta
             GROUP BY f.receptor_nombre
             ORDER BY 2 DESC
             LIMIT :limite
            """)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .setParameter("limite", limite)
                .getResultList();

        return filas.stream()
                .map(f -> new FilaReparto((String) f[0], dec(f[1]), num(f[2]).intValue()))
                .toList();
    }

    /** Las piezas que mas se mueven, por importe consumido en ordenes. */
    public List<FilaReparto> piezasMasUsadas(LocalDate desde, LocalDate hasta, int limite) {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery("""
            SELECT p.descripcion,
                   SUM(-m.cantidad * COALESCE(m.precio_coste_unitario, p.precio_coste)),
                   SUM(-m.cantidad)::int
              FROM movimiento_stock m
              JOIN pieza p ON p.id = m.pieza_id
             WHERE m.orden_trabajo_id IS NOT NULL
               AND m.cantidad < 0
               AND m.fecha::date BETWEEN :desde AND :hasta
             GROUP BY p.descripcion
             ORDER BY 3 DESC
             LIMIT :limite
            """)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .setParameter("limite", limite)
                .getResultList();

        return filas.stream()
                .map(f -> new FilaReparto((String) f[0], dec(f[1]), num(f[2]).intValue()))
                .toList();
    }

    /**
     * Dias que tarda de media una moto en salir del taller, por mes.
     *
     * <p>Solo cuenta las entregadas: una orden abierta todavia no ha tardado
     * nada, y meterla en la media la hundiria.
     */
    public List<Object[]> diasEnTallerPorMes(int ejercicio) {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery("""
            WITH meses AS (SELECT generate_series(1, 12) AS mes)
            SELECT ms.mes,
                   COALESCE(AVG(EXTRACT(EPOCH FROM (o.fecha_real_salida - o.fecha_entrada)) / 86400), 0)
              FROM meses ms
         LEFT JOIN orden_trabajo o
                ON EXTRACT(MONTH FROM o.fecha_real_salida)::int = ms.mes
               AND EXTRACT(YEAR  FROM o.fecha_real_salida)      = :ejercicio
               AND o.fecha_real_salida IS NOT NULL
             GROUP BY ms.mes
             ORDER BY ms.mes
            """)
                .setParameter("ejercicio", ejercicio)
                .getResultList();
        return filas;
    }

    /**
     * Fecha de una consulta nativa.
     *
     * <p>Segun la columna y el driver, la misma fecha puede llegar como
     * {@code Instant}, {@code Timestamp} o {@code Date}. Se aceptan las tres en
     * vez de castear a una y confiar: castear a la equivocada no falla al
     * compilar, falla en cuanto alguien abre la pantalla.
     */
    private static LocalDate dia(Object valor) {
        return switch (valor) {
            case null -> null;
            case java.time.Instant i -> i.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            case java.sql.Timestamp t -> t.toLocalDateTime().toLocalDate();
            case java.sql.Date d -> d.toLocalDate();
            default -> null;
        };
    }

    private static BigDecimal dec(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        return valor instanceof BigDecimal b ? b : BigDecimal.valueOf(((Number) valor).doubleValue());
    }

    private static Number num(Object valor) {
        return valor == null ? 0 : (Number) valor;
    }
}
