-- =====================================================================
-- V28 - La numeracion sigue donde la dejo el programa anterior
-- =====================================================================
-- El taller cambio de programa a mitad de 2026: NEXTGO llego hasta la
-- factura 41 del ejercicio. La primera de aqui tiene que ser la 42, no la 1:
-- dos facturas «1» del mismo año, una de cada programa, son justo lo que no
-- quiere ver la gestoria.
--
-- Hasta ahora una serie solo podia empezar en el 1: el trigger de V6 exigia
-- que la primera factura fuera MAX(numero) + 1 con MAX = 0. Ahora, mientras
-- la serie no tiene ninguna factura, manda su contador (ultimo_numero): una
-- serie con el contador en 41 empieza en la 42. En cuanto hay una factura,
-- vuelve a mandar la ultima emitida, como siempre, y no puede haber huecos.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_factura_validar_insercion() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_serie            serie_factura%ROWTYPE;
    v_ultimo_numero    INTEGER;
    v_ultimo_registro  BIGINT;
    v_huella_previa    VARCHAR(64);
    c_genesis CONSTANT VARCHAR(64) := repeat('0', 64);
BEGIN
    -- Bloquea la serie: serializa las emisiones concurrentes de la misma serie.
    SELECT * INTO v_serie FROM serie_factura WHERE id = NEW.serie_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'No existe la serie de facturacion con id %', NEW.serie_id;
    END IF;

    IF NOT v_serie.activa THEN
        RAISE EXCEPTION 'La serie de facturacion %/% esta inactiva', v_serie.codigo, v_serie.ejercicio;
    END IF;

    IF NEW.serie_codigo <> v_serie.codigo OR NEW.ejercicio <> v_serie.ejercicio THEN
        RAISE EXCEPTION 'Los datos de serie copiados en la factura (%/%) no coinciden con la serie referenciada (%/%)',
            NEW.serie_codigo, NEW.ejercicio, v_serie.codigo, v_serie.ejercicio;
    END IF;

    IF NEW.tipo <> v_serie.tipo THEN
        RAISE EXCEPTION 'Una factura de tipo % no puede emitirse en la serie %, que es de tipo %',
            NEW.tipo, v_serie.codigo, v_serie.tipo;
    END IF;

    -- 1) Correlatividad SIN HUECOS dentro de la serie. La primera factura
    --    continua el contador de la serie (V28); las demas, la ultima emitida.
    SELECT COALESCE(MAX(numero), v_serie.ultimo_numero) INTO v_ultimo_numero
      FROM factura WHERE serie_id = NEW.serie_id;

    IF NEW.numero <> v_ultimo_numero + 1 THEN
        RAISE EXCEPTION 'Numeracion no correlativa en la serie %: se esperaba el numero %, llego %',
            v_serie.codigo, v_ultimo_numero + 1, NEW.numero
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    -- 2) Correlatividad de la posicion en el registro global de facturacion.
    SELECT COALESCE(MAX(numero_registro), 0) INTO v_ultimo_registro FROM factura;

    IF NEW.numero_registro <> v_ultimo_registro + 1 THEN
        RAISE EXCEPTION 'Posicion no correlativa en el registro de facturacion: se esperaba %, llego %',
            v_ultimo_registro + 1, NEW.numero_registro
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    -- 3) Encadenamiento de huellas.
    IF v_ultimo_registro = 0 THEN
        v_huella_previa := c_genesis;
    ELSE
        SELECT huella INTO v_huella_previa
          FROM factura WHERE numero_registro = v_ultimo_registro;
    END IF;

    -- Ojo: numero_completo es una columna generada y todavia no tiene valor en un
    -- BEFORE INSERT, asi que el mensaje compone la referencia a mano.
    IF NEW.huella_anterior <> v_huella_previa THEN
        RAISE EXCEPTION 'Cadena de huellas rota en la factura %/%/%: se esperaba la huella anterior %, llego %',
            NEW.serie_codigo, NEW.ejercicio, LPAD(NEW.numero::text, 6, '0'),
            v_huella_previa, NEW.huella_anterior
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    -- 4) Una rectificativa solo puede rectificar una factura ya existente y anterior.
    IF NEW.tipo = 'RECTIFICATIVA' THEN
        IF NOT EXISTS (SELECT 1 FROM factura WHERE id = NEW.factura_rectificada_id) THEN
            RAISE EXCEPTION 'La factura rectificada (id %) no existe', NEW.factura_rectificada_id;
        END IF;
    END IF;

    -- Consume el numero de la serie dentro de la MISMA transaccion: si esta
    -- hace rollback, el contador vuelve atras y no queda ningun hueco.
    UPDATE serie_factura SET ultimo_numero = NEW.numero WHERE id = NEW.serie_id;
    UPDATE contador_registro_facturacion SET ultimo_numero = NEW.numero_registro WHERE id = 1;

    RETURN NEW;
END;
$$;


-- ---------------------------------------------------------------------
-- La serie ordinaria de cada año con facturas de NEXTGO sigue por la
-- ultima de ellas. Solo las que aun no tienen ninguna factura: una serie
-- que ya ha emitido no se renumera nunca. Sin facturas de NEXTGO (un taller
-- que empieza de cero) no cambia nada.
--
-- NEXTGO numera «ORD|FAC|202600000000041»: el año y, detras, el numero.
-- ---------------------------------------------------------------------
UPDATE serie_factura s
   SET ultimo_numero = n.ultimo
  FROM (SELECT EXTRACT(YEAR FROM fecha)::int                              AS ejercicio,
               MAX(substring(numero FROM '\|\d{4}(\d+)$')::int)           AS ultimo
          FROM factura_anterior
         WHERE origen = 'NEXTGO'
           AND numero ~ '\|\d{4}\d+$'
         GROUP BY 1) n
 WHERE s.ejercicio = n.ejercicio
   AND s.tipo = 'ORDINARIA'
   AND NOT s.simplificada
   AND s.ultimo_numero < n.ultimo
   AND NOT EXISTS (SELECT 1 FROM factura f WHERE f.serie_id = s.id);
