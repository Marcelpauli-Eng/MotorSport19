-- =====================================================================
-- Familias del catalogo de demostracion
--
-- Va como migracion REPETIBLE y no dentro de V900 por dos motivos:
--
--   1. Las repetibles corren DESPUES de todas las versionadas, asi que esta
--      alcanza a las piezas que inserta V900. La V9, que es la que anadio la
--      columna y la rellena por prefijo de SKU, corre antes que V900 en una
--      instalacion nueva: no encuentra ninguna pieza y la demo se quedaria sin
--      grupos, que es justo lo que se quiere enseñar.
--   2. Tocar V900 cambiaria su checksum y Flyway se negaria a arrancar contra
--      una base que ya la tenia aplicada.
--
-- Solo rellena huecos (WHERE familia IS NULL): si alguien reagrupa una pieza a
-- mano, este fichero no se la vuelve a cambiar.
-- =====================================================================

UPDATE pieza SET familia = CASE
    WHEN sku LIKE 'ACE-%' OR sku LIKE 'LIQ-REF%' THEN 'Aceites y líquidos'
    WHEN sku LIKE 'FIL-%'                        THEN 'Filtros'
    WHEN sku LIKE 'PAS-%' OR sku LIKE 'DIS-%'
      OR sku LIKE 'LIQ-FRE%'                     THEN 'Frenos'
    WHEN sku LIKE 'BUJ-%' OR sku LIKE 'BAT-%'
      OR sku LIKE 'LAM-%'                        THEN 'Eléctrico y encendido'
    WHEN sku LIKE 'KIT-TRA%' OR sku LIKE 'CAD-%' THEN 'Transmisión'
    WHEN sku LIKE 'KIT-REV%'                     THEN 'Kits de revisión'
    WHEN sku LIKE 'NEU-%'                        THEN 'Neumáticos'
    WHEN sku LIKE 'CAB-%' OR sku LIKE 'ESP-%'    THEN 'Mandos y carrocería'
    WHEN sku LIKE 'JUN-%' OR sku LIKE 'RET-%'    THEN 'Juntas y retenes'
    ELSE NULL
END
WHERE familia IS NULL;
