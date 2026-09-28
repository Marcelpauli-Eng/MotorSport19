-- Las motos de carreras no tienen matricula. Lo que identifica a toda moto es
-- el bastidor, que ya era unico (ux_moto_bastidor) y la aplicacion exige al
-- dar de alta. No se pone NOT NULL: hay motos fichadas antes sin el.
ALTER TABLE moto ALTER COLUMN matricula DROP NOT NULL;
