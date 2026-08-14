-- =====================================================================
-- Servicios tipo y facturación exenta, para la demo
--
-- Dos huecos que la V900 no cubre y que dejaban pantallas enteras sin nada
-- que enseñar:
--
-- 1. SERVICIOS TIPO. La funcionalidad es nueva y no había ni una plantilla,
--    así que la pantalla salía vacía y el desplegable «volcar un servicio»
--    del presupuesto ni siquiera aparecía.
--
-- 2. FACTURAS AL 0 %. Las 69 líneas de factura de la demo eran GENERAL, así
--    que la columna «Sin IVA» del libro salía a cero SIEMPRE. Parecía una
--    pantalla rota cuando era la demo la que no tenía ese caso.
--
-- Va como migración REPETIBLE, igual que la agenda: las repetibles corren
-- después de todas las versionadas, así que alcanzan a los clientes, motos y
-- piezas que inserta la V900. Y como corre en cada arranque, todo lo de aquí
-- está guardado con NOT EXISTS.
--
-- OJO CON LA CADENA DE HUELLAS. Las facturas van encadenadas por SHA-256:
-- cada una firma la huella de la anterior. Las de aquí se AÑADEN AL FINAL de
-- la cadena existente, con el mismo formato de cadena que usa la V900. Si se
-- insertaran por el medio, o con la huella mal, el informe de integridad del
-- libro de IVA las marcaría como manipuladas, que es peor que no tenerlas.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Servicios tipo
-- ---------------------------------------------------------------------
INSERT INTO servicio_tipo (nombre, descripcion, activo)
SELECT * FROM (VALUES
    ('Revisión 10.000 km',
     'Cambio de aceite y filtros, revisión de frenos, tensado y engrase de cadena, y comprobación de niveles.',
     TRUE),
    ('Cambio de aceite y filtro',
     'Aceite 10W-40 y filtro de aceite. El servicio más pedido del taller.',
     TRUE),
    ('Kit de transmisión',
     'Sustitución de cadena, corona y piñón, con limpieza y engrase.',
     TRUE),
    ('Juego de pastillas delanteras',
     'Pastillas delanteras y purga del circuito.',
     TRUE),
    ('Pre-ITV',
     'Revisión completa de luces, frenos, neumáticos y emisiones antes de pasar la ITV.',
     TRUE)
) AS nuevos (nombre, descripcion, activo)
WHERE NOT EXISTS (
    SELECT 1 FROM servicio_tipo s WHERE LOWER(TRIM(s.nombre)) = LOWER(TRIM(nuevos.nombre))
);


-- Las líneas se declaran con el SKU y no con el id de la pieza: los ids de la
-- V900 podrían cambiar, los SKU son lo estable. Una línea cuya pieza no exista
-- se queda fuera en vez de reventar la carga entera.
INSERT INTO linea_servicio_tipo (servicio_tipo_id, numero_linea, tipo, descripcion, pieza_id, cantidad)
SELECT s.id, d.numero_linea, d.tipo, d.descripcion, p.id, d.cantidad
  FROM (VALUES
    -- Revisión 10.000 km: 2,5 h y cuatro referencias
    ('Revisión 10.000 km', 1, 'MANO_DE_OBRA', 'Revisión programada 10.000 km', NULL,               2.500),
    ('Revisión 10.000 km', 2, 'PIEZA',        NULL,                            'ACE-10W40-1L',     3.000),
    ('Revisión 10.000 km', 3, 'PIEZA',        NULL,                            'FIL-ACE-HF204',    1.000),
    ('Revisión 10.000 km', 4, 'PIEZA',        NULL,                            'FIL-AIR-HFA1618',  1.000),
    ('Revisión 10.000 km', 5, 'PIEZA',        NULL,                            'BUJ-CR8E',         2.000),

    ('Cambio de aceite y filtro', 1, 'MANO_DE_OBRA', 'Cambio de aceite y filtro', NULL,            0.750),
    ('Cambio de aceite y filtro', 2, 'PIEZA',        NULL,                        'ACE-10W40-1L',  3.000),
    ('Cambio de aceite y filtro', 3, 'PIEZA',        NULL,                        'FIL-ACE-HF204', 1.000),

    ('Kit de transmisión', 1, 'MANO_DE_OBRA', 'Sustitución del kit de transmisión', NULL,          1.500),

    ('Juego de pastillas delanteras', 1, 'MANO_DE_OBRA', 'Cambio de pastillas y purga de frenos', NULL, 1.000),
    ('Juego de pastillas delanteras', 2, 'PIEZA',        NULL,                     'PAS-FRE-DEL-SBS', 1.000),

    ('Pre-ITV', 1, 'MANO_DE_OBRA', 'Revisión pre-ITV y ajuste de luces', NULL, 1.250)
  ) AS d (servicio, numero_linea, tipo, descripcion, sku, cantidad)
  JOIN servicio_tipo s ON LOWER(TRIM(s.nombre)) = LOWER(TRIM(d.servicio))
  LEFT JOIN pieza p    ON p.sku = d.sku
 WHERE (d.tipo = 'MANO_DE_OBRA' OR p.id IS NOT NULL)
   AND NOT EXISTS (
        SELECT 1 FROM linea_servicio_tipo l
         WHERE l.servicio_tipo_id = s.id AND l.numero_linea = d.numero_linea);


-- ---------------------------------------------------------------------
-- 2. Dos operaciones exentas de IVA, con su OT y su factura
--
-- El caso real: una venta a un cliente con NIF intracomunitario. El taller
-- factura sin repercutir IVA, y esas facturas tienen que poder verse y
-- declararse aparte. Son las que llenan la columna «Sin IVA» del libro.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    c_nif_emisor CONSTANT VARCHAR(20) := 'B87654323';

    v_registro      BIGINT;
    v_huella_previa VARCHAR(64);
    v_huella        VARCHAR(64);
    v_cadena        TEXT;

    v_numero        INTEGER;
    v_factura_id    BIGINT;
    v_orden_id      BIGINT;
    v_ot_numero     INTEGER;
    v_linea_id      BIGINT;

    v_base          NUMERIC(12,2);
    v_fecha         DATE;
    v_ts            TIMESTAMPTZ;

    r               RECORD;
BEGIN
    -- Guarda de idempotencia: si ya se cargaron, no se vuelve a tocar la
    -- cadena. Repetir estas facturas la partiria en dos.
    IF EXISTS (SELECT 1 FROM factura WHERE codigo_ot LIKE 'OT-2026-009%') THEN
        RAISE NOTICE 'Las facturas exentas de la demo ya estaban cargadas.';
        RETURN;
    END IF;

    -- Se engancha al final de la cadena que haya, sea la de V900 o ninguna.
    SELECT COALESCE(MAX(numero_registro), 0) INTO v_registro FROM factura;
    SELECT COALESCE((SELECT huella FROM factura ORDER BY numero_registro DESC LIMIT 1),
                    repeat('0', 64))
      INTO v_huella_previa;

    SELECT COALESCE(MAX(numero), 0) INTO v_numero  FROM factura WHERE serie_codigo = 'A' AND ejercicio = 2026;
    SELECT COALESCE(MAX(id), 0)     INTO v_factura_id FROM factura;
    SELECT COALESCE(MAX(id), 0)     INTO v_orden_id   FROM orden_trabajo;
    SELECT COALESCE(MAX(id), 0)     INTO v_linea_id   FROM linea_ot;
    SELECT COALESCE(MAX(numero), 0) INTO v_ot_numero  FROM orden_trabajo WHERE ejercicio = 2026;

    FOR r IN
        SELECT * FROM (VALUES
            (1, 'Preparación de moto para exportación a Portugal', 'Revisión completa previa a la entrega.',  6.000, 320.00, 12),
            (2, 'Puesta a punto para cliente intracomunitario',    'Puesta a punto y revisión de seguridad.', 4.500, 240.00, 26)
        ) AS x (orden, trabajo, diagnostico, horas, base, dias_atras)
    LOOP
        v_registro   := v_registro + 1;
        v_numero     := v_numero + 1;
        v_factura_id := v_factura_id + 1;
        v_orden_id   := v_orden_id + 1;
        v_ot_numero  := v_ot_numero + 1;
        v_linea_id   := v_linea_id + 1;

        v_fecha := CURRENT_DATE - r.dias_atras;
        v_ts    := (v_fecha + TIME '18:20:00') AT TIME ZONE 'Europe/Madrid';
        v_base  := r.base;

        -- ----- La orden de trabajo -----
        -- Nace en LISTA y se entrega al final, DESPUES de meterle las lineas.
        -- Una OT en ENTREGADA es inmutable por trigger (V6, regla D): si se
        -- crea ya entregada, la linea siguiente la rechaza la base de datos.
        INSERT INTO orden_trabajo (id, ejercicio, numero, moto_id, cliente_id, fecha_entrada,
                                   fecha_estimada_salida, fecha_real_salida, km_entrada,
                                   problema_reportado, diagnostico, tecnico_id, estado, tarifa_hora,
                                   fecha_presupuesto, fecha_aprobacion, aprobado_por, created_by)
        SELECT v_orden_id, 2026, 900 + r.orden, m.id, m.cliente_id,
               v_ts - INTERVAL '4 days', v_fecha, NULL, m.km_actual,
               r.trabajo, r.diagnostico, 3, 'LISTA', 45.00,
               v_ts - INTERVAL '3 days', v_ts - INTERVAL '3 days', 'Cliente', 2
          FROM moto m
         WHERE m.activo
         ORDER BY m.id DESC
         LIMIT 1;

        -- ----- Una única línea de mano de obra, EXENTA -----
        -- Exenta de verdad: tipo_iva EXENTO y 0 % de porcentaje. Es lo que hace
        -- que la factura caiga en la columna del 0 % del libro.
        INSERT INTO linea_ot (id, orden_trabajo_id, numero_linea, tipo, descripcion, pieza_id,
                              cantidad, precio_unitario, descuento_pct, tipo_iva, porcentaje_iva, created_by)
        VALUES (v_linea_id, v_orden_id, 1, 'MANO_DE_OBRA', r.trabajo, NULL,
                r.horas, ROUND(v_base / r.horas, 4), 0, 'EXENTO', 0.00, 3);

        INSERT INTO cambio_estado_ot (orden_trabajo_id, estado_anterior, estado_nuevo, fecha, usuario_id, motivo) VALUES
            (v_orden_id, NULL,            'RECIBIDA',      v_ts - INTERVAL '4 days', 2, 'Entrada de la moto en el taller'),
            (v_orden_id, 'RECIBIDA',      'PRESUPUESTADA', v_ts - INTERVAL '3 days', 3, NULL),
            (v_orden_id, 'PRESUPUESTADA', 'APROBADA',      v_ts - INTERVAL '3 days', 2, 'Aprobado por el cliente'),
            (v_orden_id, 'APROBADA',      'EN_REPARACION', v_ts - INTERVAL '2 days', 3, NULL),
            (v_orden_id, 'EN_REPARACION', 'LISTA',         v_ts - INTERVAL '1 day',  3, NULL),
            (v_orden_id, 'LISTA',         'ENTREGADA',     v_ts,                     2, 'Entregada al cliente');

        -- Y ahora si se entrega: a partir de esta linea la OT queda congelada.
        UPDATE orden_trabajo
           SET estado = 'ENTREGADA', fecha_real_salida = v_ts
         WHERE id = v_orden_id;

        -- ----- La factura, enganchada a la cadena -----
        -- Mismo formato de cadena que la V900: si se cambia una coma aqui, el
        -- informe de integridad empieza a decir que la demo esta manipulada.
        v_cadena := format(
            'NIFEmisor=%s&NumSerieFactura=%s&FechaExpedicion=%s&TipoFactura=%s&CuotaTotal=%s&ImporteTotal=%s&Huella=%s&FechaHoraHusoGenRegistro=%s',
            c_nif_emisor,
            'A/2026/' || LPAD(v_numero::text, 6, '0'),
            to_char(v_fecha, 'DD-MM-YYYY'),
            'ORDINARIA',
            to_char(0::numeric, 'FM9999999990.00'),
            to_char(v_base, 'FM9999999990.00'),
            v_huella_previa,
            to_char(v_ts AT TIME ZONE 'Europe/Madrid', 'YYYY-MM-DD"T"HH24:MI:SS') || to_char(v_ts, 'OF')
        );
        v_huella := encode(sha256(convert_to(v_cadena, 'UTF8')), 'hex');

        INSERT INTO factura (
            id, serie_id, serie_codigo, ejercicio, numero, tipo,
            orden_trabajo_id, fecha_emision, fecha_operacion, timestamp_emision,
            emisor_razon_social, emisor_nif, emisor_direccion, emisor_cp, emisor_ciudad,
            emisor_provincia, emisor_pais,
            receptor_id, receptor_nombre, receptor_nif, receptor_direccion, receptor_cp,
            receptor_ciudad, receptor_provincia, receptor_pais,
            matricula, descripcion_vehiculo, codigo_ot,
            base_imponible, total_iva, total,
            numero_registro, huella_anterior, huella, cadena_huella, algoritmo_huella,
            qr_contenido, software_nombre, software_version, software_nif, created_at, created_by)
        SELECT v_factura_id, 1, 'A', 2026, v_numero, 'ORDINARIA',
               v_orden_id, v_fecha, v_fecha, v_ts,
               cfg.razon_social, cfg.nif, cfg.direccion, cfg.codigo_postal, cfg.ciudad,
               cfg.provincia, cfg.pais,
               c.id, TRIM(c.nombre || ' ' || COALESCE(c.apellidos, '')),
               c.documento, c.direccion, c.codigo_postal, c.ciudad, c.provincia, c.pais,
               m.matricula, m.marca || ' ' || m.modelo, o.codigo,
               v_base, 0, v_base,
               v_registro, v_huella_previa, v_huella, v_cadena, 'SHA-256',
               format('https://verifactu.motorsport19.example/verifica?nif=%s&numserie=%s&fecha=%s&importe=%s',
                      c_nif_emisor, 'A/2026/' || LPAD(v_numero::text, 6, '0'),
                      to_char(v_fecha, 'DD-MM-YYYY'), to_char(v_base, 'FM9999999990.00')),
               cfg.software_nombre, cfg.software_version, cfg.software_nif, v_ts, 2
          FROM configuracion_taller cfg
          JOIN orden_trabajo o ON o.id = v_orden_id
          JOIN moto m          ON m.id = o.moto_id
          JOIN cliente c       ON c.id = o.cliente_id
         WHERE cfg.id = 1;

        INSERT INTO linea_factura (factura_id, numero_linea, tipo, descripcion, pieza_sku,
                                   cantidad, precio_unitario, descuento_pct, tipo_iva, porcentaje_iva)
        SELECT v_factura_id, l.numero_linea, l.tipo, l.descripcion, NULL,
               l.cantidad, l.precio_unitario, l.descuento_pct, l.tipo_iva, l.porcentaje_iva
          FROM linea_ot l
         WHERE l.orden_trabajo_id = v_orden_id;

        INSERT INTO desglose_iva_factura (factura_id, tipo_iva, porcentaje_iva, base_imponible, cuota_iva)
        SELECT v_factura_id, l.tipo_iva, l.porcentaje_iva, SUM(l.base_imponible), SUM(l.cuota_iva)
          FROM linea_factura l
         WHERE l.factura_id = v_factura_id
         GROUP BY l.tipo_iva, l.porcentaje_iva;

        INSERT INTO evento_factura (factura_id, tipo_evento, fecha, usuario_id, descripcion, detalle) VALUES
            (v_factura_id, 'EMISION', v_ts, 2,
             'Emision de la factura A/2026/' || LPAD(v_numero::text, 6, '0') || ' (operacion exenta)',
             format('{"origen":"exenta","importe":%s}', to_char(v_base, 'FM9999999990.00'))::jsonb);

        UPDATE serie_factura SET ultimo_numero = v_numero WHERE id = 1;
        UPDATE contador_ot   SET ultimo_numero = GREATEST(ultimo_numero, 900 + r.orden) WHERE ejercicio = 2026;

        v_huella_previa := v_huella;
    END LOOP;

    RAISE NOTICE 'Cargadas 2 operaciones exentas de IVA para la demo.';
END;
$$;


-- Las secuencias tienen que quedar por encima de los ids puestos a mano, o el
-- primer alta desde la aplicacion choca con una clave duplicada.
SELECT setval(pg_get_serial_sequence('orden_trabajo', 'id'), GREATEST((SELECT MAX(id) FROM orden_trabajo), 1));
SELECT setval(pg_get_serial_sequence('linea_ot', 'id'),       GREATEST((SELECT MAX(id) FROM linea_ot), 1));
SELECT setval(pg_get_serial_sequence('factura', 'id'),        GREATEST((SELECT MAX(id) FROM factura), 1));
