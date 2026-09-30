-- =====================================================================
-- V30 - Tasas y pluses, en euros o en tanto por ciento
-- =====================================================================
-- Hasta ahora una tasa era siempre un importe fijo por unidad y un plus
-- siempre un descuento en tanto por ciento. El taller quiere las dos cosas
-- en las dos:
--
--   * TASA en euros: una linea aparte de «valor» euros por unidad (lo de siempre).
--   * TASA en %: una linea aparte del «valor» % del precio de la pieza.
--   * PLUS en %: un descuento del «valor» % en la linea de la pieza (lo de siempre).
--   * PLUS en euros: la pieza baja «valor» euros por unidad.
--
-- Las reglas que ya hay siguen igual: las tasas en euros y los pluses en %.
-- ---------------------------------------------------------------------
ALTER TABLE regla_cobro ADD COLUMN unidad VARCHAR(10);

UPDATE regla_cobro SET unidad = CASE tipo WHEN 'TASA' THEN 'EUROS' ELSE 'PORCENTAJE' END;

ALTER TABLE regla_cobro ALTER COLUMN unidad SET NOT NULL;

ALTER TABLE regla_cobro ADD CONSTRAINT ck_regla_cobro_unidad
    CHECK (unidad IN ('EUROS', 'PORCENTAJE'));

-- El tope del 100 % era solo de los pluses; ahora es de todo lo que va en %.
ALTER TABLE regla_cobro DROP CONSTRAINT ck_regla_cobro_plus;
ALTER TABLE regla_cobro ADD CONSTRAINT ck_regla_cobro_valor
    CHECK (valor > 0 AND (unidad <> 'PORCENTAJE' OR valor <= 100));

COMMENT ON COLUMN regla_cobro.unidad IS 'EUROS: importe por unidad. PORCENTAJE: tanto por ciento del precio de la pieza.';
COMMENT ON COLUMN regla_cobro.valor  IS 'Euros por unidad o tanto por ciento, segun unidad.';
