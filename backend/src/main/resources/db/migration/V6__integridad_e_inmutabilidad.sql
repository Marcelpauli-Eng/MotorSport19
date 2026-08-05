-- =====================================================================
-- V6 - Funciones y triggers que sostienen las garantias del sistema
-- =====================================================================
-- Todo lo que se define aqui son invariantes que NO dependen de que la
-- aplicacion se comporte bien: se cumplen aunque alguien entre por psql.
--
--   A. updated_at siempre veraz.
--   B. El stock solo cambia mediante movimientos.
--   C. Los movimientos de stock son inmutables.
--   D. Una OT ENTREGADA es inmutable.
--   E. Las facturas son inmutables y su numeracion no tiene huecos.
--   F. Las entidades con baja logica no se borran fisicamente.
-- =====================================================================


-- =====================================================================
-- A. Mantenimiento de updated_at
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_actualizar_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION fn_actualizar_updated_at() IS 'Refresca updated_at en cada UPDATE, incluso si el cambio no viene de la aplicacion.';

CREATE TRIGGER tg_usuario_updated_at        BEFORE UPDATE ON usuario              FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();
CREATE TRIGGER tg_tipo_iva_updated_at       BEFORE UPDATE ON tipo_iva             FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();
CREATE TRIGGER tg_configuracion_updated_at  BEFORE UPDATE ON configuracion_taller FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();
CREATE TRIGGER tg_cliente_updated_at        BEFORE UPDATE ON cliente              FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();
CREATE TRIGGER tg_moto_updated_at           BEFORE UPDATE ON moto                 FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();
CREATE TRIGGER tg_proveedor_updated_at      BEFORE UPDATE ON proveedor            FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();
CREATE TRIGGER tg_pieza_updated_at          BEFORE UPDATE ON pieza                FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();
CREATE TRIGGER tg_orden_updated_at          BEFORE UPDATE ON orden_trabajo        FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();
CREATE TRIGGER tg_linea_ot_updated_at       BEFORE UPDATE ON linea_ot             FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();
CREATE TRIGGER tg_serie_factura_updated_at  BEFORE UPDATE ON serie_factura        FOR EACH ROW EXECUTE FUNCTION fn_actualizar_updated_at();


-- =====================================================================
-- B. El stock se deriva exclusivamente de los movimientos
-- =====================================================================

-- Aplica el movimiento sobre la pieza y deja constancia del stock antes y despues.
CREATE OR REPLACE FUNCTION fn_movimiento_stock_aplicar() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_stock_anterior NUMERIC(12,3);
    v_stock_nuevo    NUMERIC(12,3);
    v_sku            VARCHAR(50);
BEGIN
    -- Bloquea la pieza: dos consumos simultaneos de la misma pieza se serializan
    -- y no pueden "colarse" ambos contra el mismo stock disponible.
    SELECT stock_actual, sku INTO v_stock_anterior, v_sku
      FROM pieza WHERE id = NEW.pieza_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No existe la pieza con id %', NEW.pieza_id;
    END IF;

    v_stock_nuevo := v_stock_anterior + NEW.cantidad;

    -- Nunca se permite stock negativo. Cuando no hay piezas suficientes, la capa
    -- de dominio debe pasar la OT a ESPERANDO_PIEZAS en vez de forzar la salida.
    IF v_stock_nuevo < 0 THEN
        RAISE EXCEPTION 'Stock insuficiente para la pieza % (%): disponible %, solicitado %',
            v_sku, NEW.pieza_id, v_stock_anterior, abs(NEW.cantidad)
            USING ERRCODE = 'check_violation';
    END IF;

    NEW.stock_anterior   := v_stock_anterior;
    NEW.stock_resultante := v_stock_nuevo;

    -- Marca de sesion que autoriza al trigger de guarda a dejar pasar este UPDATE.
    PERFORM set_config('app.movimiento_stock_en_curso', 'on', true);
    UPDATE pieza SET stock_actual = v_stock_nuevo WHERE id = NEW.pieza_id;
    PERFORM set_config('app.movimiento_stock_en_curso', 'off', true);

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION fn_movimiento_stock_aplicar() IS 'Unica via por la que puede cambiar pieza.stock_actual.';

CREATE TRIGGER tg_movimiento_stock_aplicar
    BEFORE INSERT ON movimiento_stock
    FOR EACH ROW EXECUTE FUNCTION fn_movimiento_stock_aplicar();


-- Guarda: rechaza cualquier intento de tocar el stock por fuera de un movimiento.
CREATE OR REPLACE FUNCTION fn_pieza_proteger_stock() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.stock_actual IS DISTINCT FROM OLD.stock_actual
       AND COALESCE(current_setting('app.movimiento_stock_en_curso', true), 'off') <> 'on' THEN
        RAISE EXCEPTION 'El stock de una pieza no se puede modificar directamente (pieza %, % -> %). Registre un movimiento de stock.',
            OLD.sku, OLD.stock_actual, NEW.stock_actual
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_pieza_proteger_stock
    BEFORE UPDATE ON pieza
    FOR EACH ROW EXECUTE FUNCTION fn_pieza_proteger_stock();


-- Una pieza nace siempre con stock cero. La existencia inicial se carga con un
-- movimiento de ENTRADA, de modo que el libro explica hasta la primera unidad.
CREATE OR REPLACE FUNCTION fn_pieza_stock_inicial_cero() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.stock_actual := 0;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_pieza_stock_inicial_cero
    BEFORE INSERT ON pieza
    FOR EACH ROW EXECUTE FUNCTION fn_pieza_stock_inicial_cero();


-- =====================================================================
-- C, D, E, F. Bloqueos de modificacion y borrado
-- =====================================================================

-- Rechaza UPDATE y DELETE sobre tablas de registro append-only.
CREATE OR REPLACE FUNCTION fn_bloquear_modificacion() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de "%" son inmutables: no se permite % (id %)',
        TG_TABLE_NAME, TG_OP, OLD.id
        USING ERRCODE = 'restrict_violation';
END;
$$;

COMMENT ON FUNCTION fn_bloquear_modificacion() IS 'Impide UPDATE/DELETE en tablas de registro inmutables.';

-- Rechaza el borrado fisico en entidades con baja logica.
CREATE OR REPLACE FUNCTION fn_bloquear_borrado_logico() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de "%" no se borran fisicamente: use la baja logica (activo = false)',
        TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;


-- ----- C. Movimientos de stock: libro inmutable -----
-- Un movimiento erroneo se corrige con otro movimiento de AJUSTE, nunca borrando.
CREATE TRIGGER tg_movimiento_stock_inmutable
    BEFORE UPDATE OR DELETE ON movimiento_stock
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();

-- ----- Historial de estados de OT: append-only -----
CREATE TRIGGER tg_cambio_estado_inmutable
    BEFORE UPDATE OR DELETE ON cambio_estado_ot
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();

-- ----- E. Facturacion: inmutable de arriba a abajo -----
CREATE TRIGGER tg_factura_inmutable
    BEFORE UPDATE OR DELETE ON factura
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();

CREATE TRIGGER tg_linea_factura_inmutable
    BEFORE UPDATE OR DELETE ON linea_factura
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();

CREATE TRIGGER tg_desglose_iva_inmutable
    BEFORE UPDATE OR DELETE ON desglose_iva_factura
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();

CREATE TRIGGER tg_evento_factura_inmutable
    BEFORE UPDATE OR DELETE ON evento_factura
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();

-- ----- F. Baja logica obligatoria -----
CREATE TRIGGER tg_cliente_no_borrar
    BEFORE DELETE ON cliente
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado_logico();

CREATE TRIGGER tg_moto_no_borrar
    BEFORE DELETE ON moto
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado_logico();

CREATE TRIGGER tg_pieza_no_borrar
    BEFORE DELETE ON pieza
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado_logico();

CREATE TRIGGER tg_proveedor_no_borrar
    BEFORE DELETE ON proveedor
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado_logico();


-- =====================================================================
-- D. Una OT en ENTREGADA es inmutable
-- =====================================================================
CREATE OR REPLACE FUNCTION fn_orden_entregada_inmutable() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.estado = 'ENTREGADA' THEN
        RAISE EXCEPTION 'La orden de trabajo % esta ENTREGADA y no admite cambios (% rechazado)',
            OLD.codigo, TG_OP
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Las ordenes de trabajo no se borran: la % conserva el historial de la moto', OLD.codigo
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_orden_entregada_inmutable
    BEFORE UPDATE OR DELETE ON orden_trabajo
    FOR EACH ROW EXECUTE FUNCTION fn_orden_entregada_inmutable();


-- Las lineas tampoco se tocan una vez entregada la OT.
CREATE OR REPLACE FUNCTION fn_linea_ot_orden_cerrada() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_orden_id BIGINT;
    v_estado   VARCHAR(20);
    v_codigo   VARCHAR(20);
BEGIN
    v_orden_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.orden_trabajo_id ELSE NEW.orden_trabajo_id END;

    SELECT estado, codigo INTO v_estado, v_codigo FROM orden_trabajo WHERE id = v_orden_id;

    IF v_estado = 'ENTREGADA' THEN
        RAISE EXCEPTION 'La orden de trabajo % esta ENTREGADA: sus lineas no admiten % ',
            v_codigo, TG_OP
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER tg_linea_ot_orden_cerrada
    BEFORE INSERT OR UPDATE OR DELETE ON linea_ot
    FOR EACH ROW EXECUTE FUNCTION fn_linea_ot_orden_cerrada();


-- =====================================================================
-- E. Numeracion sin huecos y cadena de huellas de las facturas
-- =====================================================================
-- Este trigger es el que convierte "la aplicacion lo hace bien" en una
-- garantia real: valida en el momento del INSERT que el numero es el
-- siguiente de la serie, que la posicion en el registro global es la
-- siguiente, y que la huella anterior declarada es efectivamente la huella
-- de la factura precedente.
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

    -- 1) Correlatividad SIN HUECOS dentro de la serie.
    SELECT COALESCE(MAX(numero), 0) INTO v_ultimo_numero
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

CREATE TRIGGER tg_factura_validar_insercion
    BEFORE INSERT ON factura
    FOR EACH ROW EXECUTE FUNCTION fn_factura_validar_insercion();


-- Los totales de la cabecera deben cuadrar con las lineas y con el desglose.
-- Es un CONSTRAINT TRIGGER diferido: se comprueba al hacer COMMIT, cuando las
-- lineas ya se han insertado.
CREATE OR REPLACE FUNCTION fn_factura_validar_totales() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_lineas         INTEGER;
    v_base_lineas    NUMERIC(12,2);
    v_iva_lineas     NUMERIC(12,2);
    v_total_lineas   NUMERIC(12,2);
    v_base_desglose  NUMERIC(12,2);
    v_iva_desglose   NUMERIC(12,2);
BEGIN
    SELECT COUNT(*),
           COALESCE(SUM(base_imponible), 0),
           COALESCE(SUM(cuota_iva), 0),
           COALESCE(SUM(total), 0)
      INTO v_lineas, v_base_lineas, v_iva_lineas, v_total_lineas
      FROM linea_factura WHERE factura_id = NEW.id;

    IF v_lineas = 0 THEN
        RAISE EXCEPTION 'La factura % no tiene ninguna linea', NEW.numero_completo
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    IF v_base_lineas <> NEW.base_imponible OR v_iva_lineas <> NEW.total_iva OR v_total_lineas <> NEW.total THEN
        RAISE EXCEPTION 'Los totales de la factura % no cuadran con sus lineas. Cabecera: base %, IVA %, total %. Lineas: base %, IVA %, total %',
            NEW.numero_completo, NEW.base_imponible, NEW.total_iva, NEW.total,
            v_base_lineas, v_iva_lineas, v_total_lineas
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    SELECT COALESCE(SUM(base_imponible), 0), COALESCE(SUM(cuota_iva), 0)
      INTO v_base_desglose, v_iva_desglose
      FROM desglose_iva_factura WHERE factura_id = NEW.id;

    IF v_base_desglose <> NEW.base_imponible OR v_iva_desglose <> NEW.total_iva THEN
        RAISE EXCEPTION 'El desglose de IVA de la factura % no cuadra con la cabecera. Cabecera: base %, IVA %. Desglose: base %, IVA %',
            NEW.numero_completo, NEW.base_imponible, NEW.total_iva, v_base_desglose, v_iva_desglose
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER tg_factura_validar_totales
    AFTER INSERT ON factura
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_factura_validar_totales();


-- =====================================================================
-- Funciones de auditoria (se usan en la fase 4 y se documentan en la 7)
-- =====================================================================

-- Recorre el registro de facturacion y devuelve las anomalias encontradas.
-- Una base de datos sana devuelve cero filas.
CREATE OR REPLACE FUNCTION fn_verificar_cadena_facturas()
RETURNS TABLE (
    numero_registro BIGINT,
    numero_completo VARCHAR(40),
    anomalia        TEXT
)
LANGUAGE plpgsql AS $$
DECLARE
    r                 RECORD;
    v_esperado_reg    BIGINT := 1;
    v_huella_previa   VARCHAR(64) := repeat('0', 64);
BEGIN
    FOR r IN SELECT f.numero_registro, f.numero_completo, f.huella, f.huella_anterior,
                    f.base_imponible, f.total_iva, f.total
               FROM factura f ORDER BY f.numero_registro
    LOOP
        IF r.numero_registro <> v_esperado_reg THEN
            numero_registro := r.numero_registro;
            numero_completo := r.numero_completo;
            anomalia := format('Hueco en el registro: se esperaba la posicion %s', v_esperado_reg);
            RETURN NEXT;
        END IF;

        IF r.huella_anterior <> v_huella_previa THEN
            numero_registro := r.numero_registro;
            numero_completo := r.numero_completo;
            anomalia := format('Cadena rota: huella_anterior %s, se esperaba %s', r.huella_anterior, v_huella_previa);
            RETURN NEXT;
        END IF;

        IF r.total <> r.base_imponible + r.total_iva THEN
            numero_registro := r.numero_registro;
            numero_completo := r.numero_completo;
            anomalia := 'El total no es la suma de base imponible mas IVA';
            RETURN NEXT;
        END IF;

        v_huella_previa := r.huella;
        v_esperado_reg  := r.numero_registro + 1;
    END LOOP;

    RETURN;
END;
$$;

COMMENT ON FUNCTION fn_verificar_cadena_facturas() IS 'Audita el registro de facturacion. Devuelve una fila por anomalia; vacio significa cadena integra.';


-- Compara el stock cacheado en pieza con el acumulado real de movimientos.
-- Una base de datos sana devuelve cero filas.
CREATE OR REPLACE FUNCTION fn_verificar_integridad_stock()
RETURNS TABLE (
    pieza_id        BIGINT,
    sku             VARCHAR(50),
    stock_declarado NUMERIC(12,3),
    stock_calculado NUMERIC(12,3)
)
LANGUAGE sql STABLE AS $$
    SELECT p.id, p.sku, p.stock_actual, COALESCE(m.suma, 0)
      FROM pieza p
      LEFT JOIN (SELECT ms.pieza_id AS pid, SUM(ms.cantidad) AS suma
                   FROM movimiento_stock ms GROUP BY ms.pieza_id) m ON m.pid = p.id
     WHERE p.stock_actual <> COALESCE(m.suma, 0);
$$;

COMMENT ON FUNCTION fn_verificar_integridad_stock() IS 'Contrasta pieza.stock_actual con la suma de movimientos. Vacio significa inventario coherente.';
