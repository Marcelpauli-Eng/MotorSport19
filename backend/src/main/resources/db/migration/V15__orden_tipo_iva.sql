-- =====================================================================
-- Regimen de IVA de la orden de trabajo
--
-- Hasta ahora el IVA de cada linea salia de su origen: la mano de obra
-- nacia con el tipo general y cada pieza con el suyo del catalogo. Eso vale
-- para el trabajo corriente, pero no para una orden que va sin IVA: una
-- exportacion, una entrega intracomunitaria o un trabajo exento.
--
-- La columna guarda el tipo elegido PARA TODA LA ORDEN. Sirve para dos
-- cosas, y la segunda es la importante:
--
--   1. Deja constancia de la decision, en vez de que solo se note mirando
--      linea por linea que porcentaje quedo.
--   2. Las lineas que se añadan DESPUES nacen con ese mismo tipo. Sin esto,
--      quitar el IVA y añadir una pieza mas devolvia una linea al 21 % en
--      mitad de un presupuesto exento, sin avisar a nadie, y el error solo
--      aparecia al sumar la factura.
--
-- Nula significa «cada linea con el suyo», que es como se comportaba el
-- programa hasta hoy: las ordenes que ya existen no cambian de tratamiento.
-- =====================================================================

ALTER TABLE orden_trabajo
    ADD COLUMN tipo_iva VARCHAR(20);

ALTER TABLE orden_trabajo
    ADD CONSTRAINT fk_orden_tipo_iva
        FOREIGN KEY (tipo_iva) REFERENCES tipo_iva (codigo);

COMMENT ON COLUMN orden_trabajo.tipo_iva IS
    'Tipo de IVA impuesto a toda la orden. Nulo = cada linea con el suyo.';
