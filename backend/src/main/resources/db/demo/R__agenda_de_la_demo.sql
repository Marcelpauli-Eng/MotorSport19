-- =====================================================================
-- Agenda de demostracion
--
-- Va como migracion REPETIBLE porque las repetibles corren DESPUES de todas
-- las versionadas, asi que alcanzan a los clientes que inserta V900. Una V11
-- correria antes y no encontraria a nadie a quien citar.
--
-- POR QUE DA DE ALTA MOTOS: la V900 deja ocho de sus diez motos con una orden
-- de trabajo abierta, porque lo que quiere enseñar es el tablero del taller
-- lleno. Pero una cita es de una moto que va a ENTRAR, no de una que ya esta
-- dentro, y el sistema no deja abrir dos ordenes a la vez para la misma moto.
-- Citando esas motos, el boton «Ha llegado» fallaba en casi todas: parecia un
-- fallo de la agenda cuando era la demo mal montada. Se amplia el parque con
-- motos que estan fuera, que es lo realista en un taller con diez fichas.
--
-- Las fechas son relativas a hoy para que la agenda tenga siempre algo que
-- enseñar, se cargue la demo el dia que se cargue.
-- =====================================================================


-- ---------------------------------------------------------------------
-- Motos que estan fuera del taller, para poder citarlas
-- ---------------------------------------------------------------------
-- Sin id explicito: la V900 deja las secuencias ajustadas al final, asi que
-- la base asigna el siguiente libre. Cada una solo se inserta si no esta ya,
-- porque este fichero corre en cada arranque.
INSERT INTO moto (cliente_id, matricula, marca, modelo, anio, cilindrada, color, numero_bastidor, km_actual, activo, created_by)
SELECT * FROM (VALUES
    (1::BIGINT, '2468 HJK', 'Honda',   'CB650R',           2022,  649, 'Rojo',   'JH2RH0210NK112233', 18300, TRUE, 2::BIGINT),
    (2::BIGINT, '1357 BCF', 'Yamaha',  'Tracer 900',       2021,  847, 'Gris',   'JYARN571000223344', 35100, TRUE, 2::BIGINT),
    (4::BIGINT, '8642 LMN', 'Triumph', 'Bonneville T120',  2019, 1200, 'Verde',  'SMTD10HL0KT334455', 21800, TRUE, 2::BIGINT),
    (5::BIGINT, '9753 PQR', 'KTM',     '890 Adventure',    2023,  889, 'Naranja','VBKKA490X0M445566',  7400, TRUE, 2::BIGINT),
    (6::BIGINT, '3691 STV', 'Aprilia', 'RS 660',           2022,  659, 'Negro',  'ZD4KSA00X0N556677', 12900, TRUE, 2::BIGINT),
    (7::BIGINT, '7412 WXZ', 'Honda',   'Transalp 750',     2024,  755, 'Blanco', 'JH2RD1310RK667788',  3100, TRUE, 2::BIGINT)
) AS nuevas (cliente_id, matricula, marca, modelo, anio, cilindrada, color, numero_bastidor, km_actual, activo, created_by)
WHERE NOT EXISTS (SELECT 1 FROM moto m WHERE m.matricula = nuevas.matricula);


-- ---------------------------------------------------------------------
-- Limpieza de la primera version de esta demo
-- ---------------------------------------------------------------------
-- La version anterior de este fichero cito motos que ya estaban dentro del
-- taller. Se retiran para dejar la agenda coherente, pero SOLO las que siguen
-- sin tocar: si alguna ya genero su orden de trabajo o alguien la cancelo,
-- tiene historia y se respeta.
DELETE FROM cita
 WHERE orden_trabajo_id IS NULL
   AND estado IN ('PENDIENTE', 'CONFIRMADA')
   AND motivo IN (
       'Revision de los 20.000 km',
       'Cambio de aceite y filtro',
       'Ruido en el tren delantero al frenar',
       'Sustitucion del kit de transmision',
       'Puesta a punto antes de viaje largo',
       'Motor: consumo alto de aceite',
       'Cambio de neumatico trasero',
       'Revision pre-ITV')
   AND moto_id IN (SELECT id FROM moto WHERE matricula IN (
       '1234 JKL', '5678 MNB', '9012 RTS', '7890 WXY', '2345 BCD', '6789 FGH', '0123 JKM'));


-- ---------------------------------------------------------------------
-- Citas
-- ---------------------------------------------------------------------
-- Se buscan las motos por matricula en vez de por id, que es lo unico estable
-- entre instalaciones. Cada cita se inserta solo si esa moto no tiene ya una
-- viva, que es justo la regla que aplica el sistema al darlas de alta.
INSERT INTO cita (fecha_hora, duracion_estimada, moto_id, motivo, tecnico_id, estado, created_by)
SELECT c.fecha_hora, c.duracion, m.id, c.motivo, c.tecnico, c.estado, 1
  FROM (VALUES
    -- Hoy: el taller ya tiene faena, pero cabe mas.
    (date_trunc('day', now()) + INTERVAL '9 hours',          2.50, '2468 HJK', 'Revision de los 20.000 km',            3::BIGINT, 'CONFIRMADA'),
    (date_trunc('day', now()) + INTERVAL '11 hours',         1.50, '1357 BCF', 'Cambio de aceite y filtro',            4::BIGINT, 'CONFIRMADA'),
    (date_trunc('day', now()) + INTERVAL '16 hours',         3.00, '3456 TVW', 'Ruido en el tren delantero al frenar', NULL,      'PENDIENTE'),

    -- Manana: dia saturado a proposito, para que se vea el aviso de lleno.
    (date_trunc('day', now()) + INTERVAL '1 day 9 hours',    4.00, '8642 LMN', 'Sustitucion del kit de transmision',   3::BIGINT, 'CONFIRMADA'),
    (date_trunc('day', now()) + INTERVAL '1 day 10 hours',   6.00, '9753 PQR', 'Puesta a punto antes de viaje largo',  4::BIGINT, 'CONFIRMADA'),
    (date_trunc('day', now()) + INTERVAL '1 day 15 hours',   8.00, '3691 STV', 'Motor: consumo alto de aceite',        NULL,      'PENDIENTE'),

    -- Pasado manana: solo una, queda hueco de sobra.
    (date_trunc('day', now()) + INTERVAL '2 days 10 hours',  1.00, '7412 WXZ', 'Cambio de neumatico trasero',          3::BIGINT, 'PENDIENTE')
  ) AS c (fecha_hora, duracion, matricula, motivo, tecnico, estado)
  JOIN moto m ON m.matricula = c.matricula
 WHERE NOT EXISTS (
       SELECT 1 FROM cita x
        WHERE x.moto_id = m.id
          AND x.estado IN ('PENDIENTE', 'CONFIRMADA'));


-- Una cita de alguien que aun no esta dado de alta: llamo por telefono y se le
-- apunto a mano. Es la mitad de la agenda de un taller de verdad, y enseña que
-- para atenderla hay que darle ficha primero.
INSERT INTO cita (fecha_hora, duracion_estimada, contacto_nombre, contacto_telefono,
                  descripcion_moto, motivo, estado, created_by)
SELECT date_trunc('day', now()) + INTERVAL '3 days 12 hours', 2.00,
       'Alberto Ruiz Cano', '655443322', 'Triumph Street Triple 765',
       'Llama por primera vez: quiere presupuesto de revision completa',
       'PENDIENTE', 1
WHERE NOT EXISTS (SELECT 1 FROM cita WHERE contacto_nombre = 'Alberto Ruiz Cano');
