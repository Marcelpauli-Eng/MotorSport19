-- =====================================================================
-- Familia de piezas
--
-- Un taller acaba con cientos de referencias. Elegir una en un desplegable
-- unico es inviable: hay que recorrerlo entero para encontrar «pastillas
-- delanteras». Con la familia, quien monta un presupuesto elige primero el
-- grupo (Frenos) y despues la pieza concreta, que es como esta ordenado el
-- almacen de verdad.
--
-- Es texto libre y no una tabla aparte a proposito: cada taller agrupa a su
-- manera, y obligarle al catalogo de otro solo estorba. El desplegable se
-- construye con las familias que ya existen, asi que se van creando solas
-- segun se dan de alta piezas.
-- =====================================================================

ALTER TABLE pieza ADD COLUMN familia VARCHAR(60);

COMMENT ON COLUMN pieza.familia IS
    'Grupo al que pertenece la pieza (Frenos, Transmision, Filtros...). Texto libre.';

-- Para el desplegable de familias y el filtro del catalogo.
CREATE INDEX ix_pieza_familia ON pieza (familia) WHERE familia IS NOT NULL;

-- Se rellenan las que ya existen a partir de su SKU, que en la demo sigue un
-- prefijo por tipo. Si una no encaja, se queda sin familia: es opcional.
UPDATE pieza SET familia = CASE
    WHEN sku LIKE 'ACE-%' THEN 'Aceites y líquidos'
    WHEN sku LIKE 'FIL-%' THEN 'Filtros'
    WHEN sku LIKE 'PAS-%' OR sku LIKE 'DIS-%' OR sku LIKE 'LIQ-FRE%' THEN 'Frenos'
    WHEN sku LIKE 'BUJ-%' OR sku LIKE 'BAT-%' OR sku LIKE 'BOM-%' THEN 'Eléctrico y encendido'
    WHEN sku LIKE 'CAD-%' OR sku LIKE 'KIT-%' OR sku LIKE 'COR-%' THEN 'Transmisión'
    WHEN sku LIKE 'NEU-%' THEN 'Neumáticos'
    WHEN sku LIKE 'CAB-%' OR sku LIKE 'MAN-%' OR sku LIKE 'ESP-%' THEN 'Mandos y carrocería'
    WHEN sku LIKE 'JUN-%' OR sku LIKE 'RET-%' THEN 'Juntas y retenes'
    ELSE NULL
END;
