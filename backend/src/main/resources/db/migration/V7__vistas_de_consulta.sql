-- =====================================================================
-- V7 - Vistas de consulta
-- =====================================================================
-- Calculos derivados que no deben duplicarse en la aplicacion.
-- =====================================================================


-- Piezas por debajo del stock minimo: alimenta las alertas de inventario.
CREATE VIEW v_pieza_bajo_minimo AS
SELECT p.id                                   AS pieza_id,
       p.sku,
       p.descripcion,
       p.marca,
       p.ubicacion,
       p.stock_actual,
       p.stock_minimo,
       p.stock_minimo - p.stock_actual        AS unidades_a_reponer,
       p.stock_actual = 0                     AS sin_existencias,
       p.proveedor_id,
       pr.nombre                              AS proveedor_nombre,
       p.precio_coste
  FROM pieza p
  LEFT JOIN proveedor pr ON pr.id = p.proveedor_id
 WHERE p.activo
   AND p.stock_actual <= p.stock_minimo;

COMMENT ON VIEW v_pieza_bajo_minimo IS 'Piezas activas cuyo stock ha caido al minimo o por debajo.';


-- Totales de cada OT calculados a partir de sus lineas.
CREATE VIEW v_orden_trabajo_totales AS
SELECT o.id                                        AS orden_trabajo_id,
       o.codigo,
       o.estado,
       COUNT(l.id)                                 AS num_lineas,
       COALESCE(SUM(l.base_imponible), 0)::NUMERIC(12,2) AS base_imponible,
       COALESCE(SUM(l.cuota_iva), 0)::NUMERIC(12,2)      AS total_iva,
       COALESCE(SUM(l.total), 0)::NUMERIC(12,2)          AS total,
       COALESCE(SUM(l.cantidad) FILTER (WHERE l.tipo = 'MANO_DE_OBRA'), 0)::NUMERIC(12,3) AS horas_mano_obra
  FROM orden_trabajo o
  LEFT JOIN linea_ot l ON l.orden_trabajo_id = o.id
 GROUP BY o.id, o.codigo, o.estado;

COMMENT ON VIEW v_orden_trabajo_totales IS 'Importes de cada OT agregados desde sus lineas. Nunca se almacenan duplicados.';


-- Libro registro de facturas emitidas, en el orden de la cadena de huellas.
CREATE VIEW v_libro_facturas AS
SELECT f.numero_registro,
       f.numero_completo,
       f.tipo,
       f.fecha_emision,
       f.receptor_nombre,
       f.receptor_nif,
       f.base_imponible,
       f.total_iva,
       f.total,
       f.codigo_ot,
       f.matricula,
       rect.numero_completo AS rectifica_a,
       f.huella_anterior,
       f.huella
  FROM factura f
  LEFT JOIN factura rect ON rect.id = f.factura_rectificada_id
 ORDER BY f.numero_registro;

COMMENT ON VIEW v_libro_facturas IS 'Libro registro de facturacion en el orden exacto de la cadena de huellas.';


-- Historial de intervenciones de cada moto.
CREATE VIEW v_historial_moto AS
SELECT m.id                AS moto_id,
       m.matricula,
       m.marca,
       m.modelo,
       o.id                AS orden_trabajo_id,
       o.codigo            AS codigo_ot,
       o.fecha_entrada,
       o.fecha_real_salida,
       o.km_entrada,
       o.estado,
       o.problema_reportado,
       o.diagnostico,
       u.nombre_completo   AS tecnico,
       t.total             AS importe_total,
       f.numero_completo   AS factura
  FROM moto m
  JOIN orden_trabajo o          ON o.moto_id = m.id
  LEFT JOIN usuario u           ON u.id = o.tecnico_id
  LEFT JOIN v_orden_trabajo_totales t ON t.orden_trabajo_id = o.id
  LEFT JOIN factura f           ON f.orden_trabajo_id = o.id AND f.tipo = 'ORDINARIA';

COMMENT ON VIEW v_historial_moto IS 'Historial completo de ordenes de trabajo por moto.';
