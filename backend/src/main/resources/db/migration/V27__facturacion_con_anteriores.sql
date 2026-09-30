-- =====================================================================
-- V27 - Lo facturado, contando tambien lo del programa anterior
-- =====================================================================
-- Los informes, el panel y las columnas de IVA leian solo la tabla factura.
-- Tras cambiar de programa salian a cero: el año tenia meses de trabajo
-- facturados con NEXTGO que no contaban en ningun sitio.
--
-- Estas vistas suman las dos cosas y son SOLO para las cifras. El libro
-- registro, la cadena de huellas y la exportacion a la gestoria siguen
-- leyendo unicamente la tabla factura: las de NEXTGO ya las declaro aquel
-- programa, y entregarlas otra vez las contaria dos veces.
--
-- Las anteriores llevan el id en negativo para que nunca coincida con el de
-- una factura de aqui al cruzarlas con sus lineas.
-- ---------------------------------------------------------------------
CREATE VIEW v_factura_cifras AS
SELECT f.id,
       f.tipo,
       f.orden_trabajo_id,
       f.fecha_emision,
       f.receptor_nombre,
       f.base_imponible,
       f.total_iva,
       f.total
  FROM factura f
UNION ALL
SELECT -fa.id,
       'ORDINARIA',
       fa.orden_trabajo_id,
       fa.fecha,
       fa.receptor_nombre,
       fa.base_imponible,
       fa.total_iva,
       fa.total
  FROM factura_anterior fa;

COMMENT ON VIEW v_factura_cifras IS
    'Facturas de este programa y de NEXTGO juntas, para informes. No sirve para el libro ni la exportacion.';


-- Las lineas de NEXTGO van sin el descuento general de la factura, que aquel
-- programa restaba al final. Se reparte en proporcion para que las lineas
-- sumen la base de su factura, como las de aqui.
CREATE VIEW v_linea_factura_cifras AS
SELECT l.factura_id,
       l.tipo,
       l.base_imponible
  FROM linea_factura l
UNION ALL
SELECT -la.factura_anterior_id,
       la.tipo,
       ROUND(la.importe * fa.base_imponible
             / NULLIF(SUM(la.importe) OVER (PARTITION BY la.factura_anterior_id), 0), 2)
  FROM linea_factura_anterior la
  JOIN factura_anterior fa ON fa.id = la.factura_anterior_id;

COMMENT ON VIEW v_linea_factura_cifras IS
    'Lineas de las facturas de v_factura_cifras, con el mismo id de factura.';
