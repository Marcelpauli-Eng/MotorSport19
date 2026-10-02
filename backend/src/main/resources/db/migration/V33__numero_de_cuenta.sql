-- =====================================================================
-- V33 - Numero de cuenta del taller
-- =====================================================================
-- Sale en la factura como forma de pago, en lugar de «CONTADO». Vacio, la
-- factura sigue diciendo «CONTADO».
-- ---------------------------------------------------------------------
ALTER TABLE configuracion_taller
    ADD COLUMN numero_cuenta VARCHAR(60);

COMMENT ON COLUMN configuracion_taller.numero_cuenta IS
    'Cuenta bancaria del taller. Se imprime como forma de pago en las facturas.';
