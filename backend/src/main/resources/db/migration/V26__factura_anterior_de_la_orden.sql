-- =====================================================================
-- V26 - La orden de trabajo que ya se facturo con el programa anterior
-- =====================================================================
-- Los presupuestos de NEXTGO se cargan como ordenes de trabajo, y los que
-- ya se facturaron alli entran ENTREGADA. Sin nada que lo diga, cada una
-- parecia trabajo entregado y sin cobrar: salia en «Trabajo sin facturar» y
-- ofrecia emitir su factura, que seria la segunda del mismo trabajo.
--
-- La factura de NEXTGO apunta a su orden. Con eso la orden cuenta como
-- facturada, y el historial de la moto la cuenta una vez y no dos.
-- ---------------------------------------------------------------------
ALTER TABLE factura_anterior
    ADD COLUMN orden_trabajo_id BIGINT REFERENCES orden_trabajo (id);

-- Una orden, una factura: como las de este programa (V16).
CREATE UNIQUE INDEX ux_factura_anterior_orden ON factura_anterior (orden_trabajo_id)
    WHERE orden_trabajo_id IS NOT NULL;

COMMENT ON COLUMN factura_anterior.orden_trabajo_id IS
    'Orden de trabajo que factura, si se cargo tambien su presupuesto. Nula en las demas.';
