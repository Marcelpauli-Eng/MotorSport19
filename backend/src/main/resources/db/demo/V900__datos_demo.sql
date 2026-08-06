-- =====================================================================
-- V900 - Datos de demostracion
-- =====================================================================
-- Esta migracion SOLO se aplica con el perfil `demo` activo (que anade
-- classpath:db/demo a las localizaciones de Flyway).
--
-- NO USAR EN PRODUCCION: crea usuarios con contrasenas conocidas y datos
-- fiscales ficticios.
--
-- Credenciales de demostracion (BCrypt, se usaran a partir de la fase 5):
--   admin      / admin1234       ADMIN
--   mostrador  / mostrador1234   MOSTRADOR
--   jortega    / tecnico1234     TECNICO
--   nsanz      / tecnico1234     TECNICO
--
-- Los datos cubren a proposito todos los casos interesantes del dominio:
--   * Las nueve situaciones de la maquina de estados de las OT.
--   * Un cliente sin datos fiscales (no se puede facturar) y otro de baja.
--   * Tres piezas en alerta de stock, una de ellas sin existencias.
--   * Una cadena de facturas con huellas SHA-256 reales y encadenadas,
--     incluida una rectificativa.
-- =====================================================================


-- ---------------------------------------------------------------------
-- Usuarios
-- ---------------------------------------------------------------------
INSERT INTO usuario (id, username, password_hash, nombre_completo, email, telefono, rol, activo) VALUES
 (1, 'admin',     '$2a$10$iLxAMJPHQbpQkykVuxLKoO037g7b/CIDVP4WgmYKrZtQm3e3mBY4G', 'Dirección del taller', 'admin@motorsport19.example',     '910000001', 'ADMIN',     TRUE),
 (2, 'mostrador', '$2a$10$fTIS2QNdy.lMlKK5iM.59.x69E9ek.7Rddxdf8Aa5GohC4bNNSJVe', 'Laura Vidal Requena',  'mostrador@motorsport19.example', '910000002', 'MOSTRADOR', TRUE),
 (3, 'jortega',   '$2a$10$mrVPaQYZvzCbseG6LJTtIeJVhUUK.eoGChZU0kLB8HN7.naFJmqqS', 'Javier Ortega Marín',  'jortega@motorsport19.example',   '910000003', 'TECNICO',   TRUE),
 (4, 'nsanz',     '$2a$10$UtP8C..J4SAtqM/agJ7fe.AqtyTOo3BuKPdu34EMCHmnvGbaiRt3G', 'Nuria Sanz Belmonte',  'nsanz@motorsport19.example',     '910000004', 'TECNICO',   TRUE);


-- ---------------------------------------------------------------------
-- Configuracion fiscal del taller
-- ---------------------------------------------------------------------
INSERT INTO configuracion_taller (
    id, razon_social, nif, direccion, codigo_postal, ciudad, provincia, pais,
    telefono, email, tarifa_hora_defecto, tipo_iva_defecto,
    software_nombre, software_version, software_nif, url_verificacion_qr, created_by, updated_by
) VALUES (
    1, 'MotorSport19 Taller S.L.', 'B87654323', 'Calle del Motor 19', '28019', 'Madrid', 'Madrid', 'Espana',
    '910000000', 'taller@motorsport19.example', 45.00, 'GENERAL',
    'MotorSport19 Taller', '0.1.0', 'B87654323',
    'https://verifactu.motorsport19.example/verifica', 1, 1
);


-- ---------------------------------------------------------------------
-- Proveedores
-- ---------------------------------------------------------------------
INSERT INTO proveedor (id, nombre, nif, direccion, codigo_postal, ciudad, provincia, telefono, email, activo, created_by) VALUES
 (1, 'Recambios del Sur S.A.',      'A28184562', 'Poligono Industrial Sur, nave 14', '28914', 'Leganes',   'Madrid',   '911111111', 'pedidos@recambiosdelsur.example',  TRUE, 1),
 (2, 'Motorecambios Levante S.L.',  'B61234563', 'Avenida del Puerto 220',           '46023', 'Valencia',  'Valencia', '961111111', 'ventas@motolevante.example',       TRUE, 1),
 (3, 'Neumaticos Ibericos S.L.',    'B45678901', 'Carretera de Burgos km 18',        '28703', 'San Sebastian de los Reyes', 'Madrid', '912222222', 'pedidos@neumaticosibericos.example', TRUE, 1);


-- ---------------------------------------------------------------------
-- Catalogo de piezas
-- ---------------------------------------------------------------------
-- El stock arranca SIEMPRE en cero (lo fuerza un trigger). Las existencias
-- iniciales se cargan mas abajo con movimientos de ENTRADA, de forma que el
-- libro de movimientos explica cada unidad del almacen.
INSERT INTO pieza (id, sku, descripcion, marca, ubicacion, stock_minimo, precio_coste, precio_venta, tipo_iva, proveedor_id, unidad_medida, activo, created_by) VALUES
 ( 1, 'ACE-10W40-1L',      'Aceite motor 10W-40 semisintético 1 L',        'Motul',       'A1-01',  12,   6.2000,  12.9000, 'GENERAL', 1, 'L',  TRUE, 1),
 ( 2, 'FIL-ACE-HF204',     'Filtro de aceite HF204',                       'Hiflofiltro', 'A1-02',   6,   4.1000,   9.5000, 'GENERAL', 1, 'UD', TRUE, 1),
 ( 3, 'FIL-AIR-HFA1618',   'Filtro de aire HFA1618',                       'Hiflofiltro', 'A1-03',   4,  11.3000,  24.0000, 'GENERAL', 1, 'UD', TRUE, 1),
 ( 4, 'BUJ-CR8E',          'Bujia NGK CR8E',                               'NGK',         'A2-01',   8,   3.9000,   8.7500, 'GENERAL', 1, 'UD', TRUE, 1),
 ( 5, 'PAS-FRE-DEL-SBS',   'Pastillas de freno delanteras sinterizadas',   'SBS',         'B1-01',   4,  18.4000,  39.9000, 'GENERAL', 2, 'JGO',TRUE, 1),
 ( 6, 'PAS-FRE-TRA-SBS',   'Pastillas de freno traseras sinterizadas',     'SBS',         'B1-02',   4,  15.2000,  33.5000, 'GENERAL', 2, 'JGO',TRUE, 1),
 ( 7, 'DIS-FRE-DEL-320',   'Disco de freno delantero 320 mm',              'Brembo',      'B2-01',   2,  78.0000, 149.0000, 'GENERAL', 2, 'UD', TRUE, 1),
 ( 8, 'KIT-TRA-525',       'Kit de transmisión 525 (cadena y pinones)',    'DID',         'C1-01',   2,  92.0000, 168.0000, 'GENERAL', 2, 'KIT',TRUE, 1),
 ( 9, 'NEU-DEL-120-70-17', 'Neumatico delantero 120/70 ZR17',              'Michelin',    'D1-01',   2,  96.5000, 159.0000, 'GENERAL', 3, 'UD', TRUE, 1),
 (10, 'NEU-TRA-180-55-17', 'Neumatico trasero 180/55 ZR17',                'Michelin',    'D1-02',   2, 132.0000, 209.0000, 'GENERAL', 3, 'UD', TRUE, 1),
 (11, 'BAT-YTX12',         'Batería YTX12-BS 12V 10Ah',                    'Yuasa',       'A3-01',   3,  48.0000,  89.9000, 'GENERAL', 1, 'UD', TRUE, 1),
 (12, 'LIQ-REF-1L',        'Liquido refrigerante 1 L',                     'Motul',       'A1-04',   6,   5.1000,  11.5000, 'GENERAL', 1, 'L',  TRUE, 1),
 (13, 'LIQ-FRE-DOT4',      'Liquido de frenos DOT 4 500 ml',               'Motul',       'A1-05',   5,   6.8000,  14.9000, 'GENERAL', 1, 'UD', TRUE, 1),
 (14, 'JUN-CUL',           'Junta de culata',                              'Athena',      'C2-01',   2,  24.0000,  52.0000, 'GENERAL', 2, 'UD', TRUE, 1),
 (15, 'CAB-EMB',           'Cable de embrague',                            'Domino',      'C2-02',   3,   9.4000,  21.0000, 'GENERAL', 2, 'UD', TRUE, 1),
 (16, 'ESP-RET-DER',       'Espejo retrovisor derecho universal',          'Puig',        'E1-01',   2,  12.5000,  27.5000, 'GENERAL', 2, 'UD', TRUE, 1),
 (17, 'LAM-H4',            'Lampara faro H4 12V 60/55W',                   'Philips',     'A2-02',   6,   4.2000,  10.9000, 'GENERAL', 1, 'UD', TRUE, 1),
 (18, 'KIT-REV-10000',     'Kit revisión 10.000 km (aceite, filtros, bujias)', 'Varios',  'A1-06',   2,  42.0000,  89.0000, 'GENERAL', 1, 'KIT',TRUE, 1);


-- ---------------------------------------------------------------------
-- Carga inicial de almacen (ENTRADA por compra a proveedor)
-- ---------------------------------------------------------------------
INSERT INTO movimiento_stock (pieza_id, tipo, cantidad, fecha, usuario_id, motivo, documento_proveedor, precio_coste_unitario) VALUES
 ( 1, 'ENTRADA', 40, TIMESTAMPTZ '2026-04-08 09:15:00+02', 1, 'Compra inicial de temporada', 'ALB-2026-0412',   6.2000),
 ( 2, 'ENTRADA', 18, TIMESTAMPTZ '2026-04-08 09:15:00+02', 1, 'Compra inicial de temporada', 'ALB-2026-0412',   4.1000),
 ( 3, 'ENTRADA',  7, TIMESTAMPTZ '2026-04-08 09:15:00+02', 1, 'Compra inicial de temporada', 'ALB-2026-0412',  11.3000),
 ( 4, 'ENTRADA', 24, TIMESTAMPTZ '2026-04-08 09:15:00+02', 1, 'Compra inicial de temporada', 'ALB-2026-0412',   3.9000),
 (11, 'ENTRADA',  6, TIMESTAMPTZ '2026-04-08 09:15:00+02', 1, 'Compra inicial de temporada', 'ALB-2026-0412',  48.0000),
 (12, 'ENTRADA', 14, TIMESTAMPTZ '2026-04-08 09:15:00+02', 1, 'Compra inicial de temporada', 'ALB-2026-0412',   5.1000),
 (13, 'ENTRADA',  9, TIMESTAMPTZ '2026-04-08 09:15:00+02', 1, 'Compra inicial de temporada', 'ALB-2026-0412',   6.8000),
 (17, 'ENTRADA', 20, TIMESTAMPTZ '2026-04-08 09:15:00+02', 1, 'Compra inicial de temporada', 'ALB-2026-0412',   4.2000),
 (18, 'ENTRADA',  5, TIMESTAMPTZ '2026-04-08 09:15:00+02', 1, 'Compra inicial de temporada', 'ALB-2026-0412',  42.0000),
 ( 5, 'ENTRADA', 10, TIMESTAMPTZ '2026-04-10 11:40:00+02', 1, 'Compra inicial de temporada', 'ML-2026-1188',   18.4000),
 ( 6, 'ENTRADA',  8, TIMESTAMPTZ '2026-04-10 11:40:00+02', 1, 'Compra inicial de temporada', 'ML-2026-1188',   15.2000),
 ( 7, 'ENTRADA',  3, TIMESTAMPTZ '2026-04-10 11:40:00+02', 1, 'Compra inicial de temporada', 'ML-2026-1188',   78.0000),
 ( 8, 'ENTRADA',  4, TIMESTAMPTZ '2026-04-10 11:40:00+02', 1, 'Compra inicial de temporada', 'ML-2026-1188',   92.0000),
 (14, 'ENTRADA',  2, TIMESTAMPTZ '2026-04-10 11:40:00+02', 1, 'Compra inicial de temporada', 'ML-2026-1188',   24.0000),
 (15, 'ENTRADA',  1, TIMESTAMPTZ '2026-04-10 11:40:00+02', 1, 'Compra inicial de temporada', 'ML-2026-1188',    9.4000),
 (16, 'ENTRADA',  2, TIMESTAMPTZ '2026-04-10 11:40:00+02', 1, 'Compra inicial de temporada', 'ML-2026-1188',   12.5000),
 ( 9, 'ENTRADA',  5, TIMESTAMPTZ '2026-04-15 08:30:00+02', 1, 'Compra inicial de temporada', 'NI-2026-0330',   96.5000),
 (10, 'ENTRADA',  4, TIMESTAMPTZ '2026-04-15 08:30:00+02', 1, 'Compra inicial de temporada', 'NI-2026-0330',  132.0000);

-- Ajuste tras inventario fisico: dos espejos aparecieron danados en el almacen.
-- Es la forma correcta de corregir existencias: un movimiento mas, nunca editar.
INSERT INTO movimiento_stock (pieza_id, tipo, cantidad, fecha, usuario_id, motivo) VALUES
 (16, 'AJUSTE', -2, TIMESTAMPTZ '2026-05-04 18:00:00+02', 1, 'Inventario fisico: 2 unidades danadas en almacen, se dan de baja');


-- ---------------------------------------------------------------------
-- Clientes
-- ---------------------------------------------------------------------
INSERT INTO cliente (id, nombre, apellidos, tipo_documento, documento, direccion, codigo_postal, ciudad, provincia, email, telefono, observaciones, activo, fecha_baja, created_by) VALUES
 (1, 'Carlos',  'Núñez Prieto',   'NIF', '12345678Z', 'Calle de Alcala 145',                    '28009', 'Madrid', 'Madrid', 'carlos.nunez@correo.example',  '600100101', NULL, TRUE, NULL, 2),
 (2, 'Marta',   'Iglesias Rubio', 'NIF', '45678912S', 'Avenida de America 22, 4B',              '28002', 'Madrid', 'Madrid', 'marta.iglesias@correo.example','600100102', NULL, TRUE, NULL, 2),
 (3, 'Talleres y Flotas Delta S.L.', NULL, 'CIF', 'B86543212', 'Poligono Las Mercedes, nave 7', '28022', 'Madrid', 'Madrid', 'flotas@delta.example',        '913000303', 'Cliente de flota: facturacion mensual agrupada.', TRUE, NULL, 2),
 (4, 'Andrés',  'Colomer Ruiz',   'NIF', '23456789D', 'Calle Bravo Murillo 88',                 '28003', 'Madrid', 'Madrid', 'andres.colomer@correo.example','600100104', NULL, TRUE, NULL, 2),
 (5, 'Silvia',  'Barea Lozano',   'NIF', '34567890V', 'Calle Serrano 210',                      '28016', 'Madrid', 'Madrid', 'silvia.barea@correo.example',  '600100105', NULL, TRUE, NULL, 2),
 (6, 'Iván',    'Peláez Mora',    'NIF', '11223344B', 'Calle Embajadores 45',                   '28012', 'Madrid', 'Madrid', 'ivan.pelaez@correo.example',   '600100106', NULL, TRUE, NULL, 2),
 -- Ficha incompleta a proposito: entro con una averia y dejo solo el telefono.
 -- Se le puede abrir una OT, pero NO se le puede facturar hasta completar los
 -- datos fiscales.
 (7, 'Rocío',   'Almansa Gil',    NULL,  NULL,        NULL,                                     NULL,    NULL,     NULL,     'rocio.almansa@correo.example', '600100107', 'Pendiente de completar datos fiscales antes de facturar.', TRUE, NULL, 2),
 -- Cliente dado de baja: sigue existiendo porque conserva historial y facturas.
 (8, 'Ernesto', 'Vidal Cano',     'NIF', '87654321X', 'Calle de Toledo 3',                      '28005', 'Madrid', 'Madrid', 'ernesto.vidal@correo.example', '600100108', 'Baja: traslado fuera de la comunidad.', FALSE, TIMESTAMPTZ '2026-03-18 12:00:00+01', 2);


-- ---------------------------------------------------------------------
-- Motos
-- ---------------------------------------------------------------------
INSERT INTO moto (id, cliente_id, matricula, marca, modelo, anio, cilindrada, color, numero_bastidor, km_actual, observaciones, activo, fecha_baja, created_by) VALUES
 ( 1, 1, '1234 JKL', 'Yamaha',  'MT-07',      2021,  689, 'Azul',    'JYARM33E0MA012345', 24500, NULL, TRUE, NULL, 2),
 ( 2, 1, '5678 MNB', 'Honda',   'CB500F',     2019,  471, 'Negro',   'JH2PC4510KK023456', 41200, NULL, TRUE, NULL, 2),
 ( 3, 2, '9012 RTS', 'Kawasaki','Z900',       2022,  948, 'Verde',   'JKAZR2C15NA034567', 15800, NULL, TRUE, NULL, 2),
 ( 4, 3, '3456 TVW', 'Honda',   'PCX 125',    2023,  125, 'Blanco',  'MLHJK5510PC045678',  9800, 'Vehiculo de flota (reparto).', TRUE, NULL, 2),
 ( 5, 3, '7890 WXY', 'Honda',   'PCX 125',    2023,  125, 'Blanco',  'MLHJK5510PC056789', 11400, 'Vehiculo de flota (reparto).', TRUE, NULL, 2),
 ( 6, 4, '2345 BCD', 'BMW',     'R 1250 GS',  2020, 1254, 'Gris',    'WB10J1109LZ067890', 62300, NULL, TRUE, NULL, 2),
 ( 7, 5, '6789 FGH', 'Ducati',  'Monster 821',2018,  821, 'Rojo',    'ZDMM606AAJB078901', 33900, NULL, TRUE, NULL, 2),
 ( 8, 6, '0123 JKM', 'Suzuki',  'GSX-R 750',  2017,  750, 'Azul',    'JS1CF3111H2089012', 48700, NULL, TRUE, NULL, 2),
 ( 9, 7, '4567 NPR', 'KTM',     'Duke 390',   2022,  373, 'Naranja', 'VBKJKA405NM090123', 12100, NULL, TRUE, NULL, 2),
 (10, 8, '8901 SVZ', 'Vespa',   'GTS 300',    2016,  278, 'Burdeos', 'ZAPMA36001V101234', 27600, 'Moto de cliente dado de baja.', FALSE, TIMESTAMPTZ '2026-03-18 12:00:00+01', 2);


-- ---------------------------------------------------------------------
-- Ordenes de trabajo
-- ---------------------------------------------------------------------
INSERT INTO contador_ot (ejercicio, ultimo_numero) VALUES (2026, 11);

-- Las tres primeras se insertan como LISTA y se pasan a ENTREGADA al final:
-- una OT ya entregada es inmutable y no admitiria que se le anadan lineas.
INSERT INTO orden_trabajo (id, ejercicio, numero, moto_id, cliente_id, fecha_entrada, fecha_estimada_salida, fecha_real_salida,
                           km_entrada, problema_reportado, diagnostico, tecnico_id, estado, tarifa_hora,
                           fecha_presupuesto, fecha_aprobacion, aprobado_por, motivo_rechazo, observaciones, created_by) VALUES

 (1, 2026,  1,  1, 1, TIMESTAMPTZ '2026-05-12 09:10:00+02', DATE '2026-05-15', NULL, 20120,
  'Toca la revisión de los 20.000 km. Nota la moto algo perezosa al arrancar en frio.',
  'Revisión programada completa. Bujias muy desgastadas y filtro de aire saturado, lo que explica el arranque en frio.',
  3, 'LISTA', 45.00, TIMESTAMPTZ '2026-05-12 12:30:00+02', TIMESTAMPTZ '2026-05-12 17:45:00+02', 'Carlos Núñez Prieto', NULL, NULL, 2),

 (2, 2026,  2,  6, 4, TIMESTAMPTZ '2026-06-02 08:40:00+02', DATE '2026-06-05', NULL, 62150,
  'Neumaticos al limite y freno delantero con poco tacto. Quiere dejarla lista antes de un viaje largo.',
  'Ambos neumaticos por debajo del limite legal. Pastillas delanteras al minimo y liquido de frenos degradado.',
  4, 'LISTA', 45.00, TIMESTAMPTZ '2026-06-02 11:15:00+02', TIMESTAMPTZ '2026-06-02 13:00:00+02', 'Andrés Colomer Ruiz', NULL, 'Cliente avisa de viaje: prioridad alta.', 2),

 (3, 2026,  3,  4, 3, TIMESTAMPTZ '2026-06-20 08:00:00+02', DATE '2026-06-23', NULL,  9650,
  'Mantenimiento periodico de flota. Ademas, la luz de cruce no funciona.',
  'Mantenimiento realizado sin incidencias. Lampara de cruce fundida, sustituida.',
  3, 'LISTA', 45.00, TIMESTAMPTZ '2026-06-20 10:00:00+02', TIMESTAMPTZ '2026-06-20 10:20:00+02', 'Talleres y Flotas Delta S.L.', NULL, NULL, 2),

 -- Presupuesto rechazado: la moto vuelve al cliente sin reparar.
 (4, 2026,  4,  3, 2, TIMESTAMPTZ '2026-07-01 10:30:00+02', DATE '2026-07-08', TIMESTAMPTZ '2026-07-03 17:00:00+02', 15650,
  'Pierde refrigerante y humea al ralenti despues de un uso intensivo en circuito.',
  'Junta de culata danada. Requiere desmontar la culata para sustituirla y verificar planitud.',
  4, 'RECHAZADA', 45.00, TIMESTAMPTZ '2026-07-02 13:00:00+02', NULL, NULL,
  'El cliente considera que el importe supera lo que quiere invertir y prefiere pedir una segunda opinion.', NULL, 2),

 -- Reparada y pendiente de que el cliente la recoja.
 (5, 2026,  5,  7, 5, TIMESTAMPTZ '2026-07-22 09:00:00+02', DATE '2026-07-25', NULL, 33840,
  'Ruido metalico en la transmisión y cadena muy estirada.',
  'Kit de transmisión al final de su vida util. Sustituido el conjunto completo.',
  3, 'LISTA', 45.00, TIMESTAMPTZ '2026-07-22 11:30:00+02', TIMESTAMPTZ '2026-07-22 12:10:00+02', 'Silvia Barea Lozano', NULL, 'Avisado por telefono el 24/07, pendiente de recogida.', 2),

 -- En el puente, con las piezas ya consumidas.
 (6, 2026,  6,  8, 6, TIMESTAMPTZ '2026-07-28 09:30:00+02', DATE '2026-08-06', NULL, 48650,
  'Freno trasero que no responde bien y la moto no arranca si esta dos dias parada.',
  'Pastillas traseras agotadas y batería sin capacidad de retencion. Se sustituyen ambas.',
  4, 'EN_REPARACION', 45.00, TIMESTAMPTZ '2026-07-28 12:00:00+02', TIMESTAMPTZ '2026-07-28 15:20:00+02', 'Iván Peláez Mora', NULL, NULL, 2),

 -- Bloqueada por falta de stock: el espejo no esta disponible en almacen.
 (7, 2026,  7,  5, 3, TIMESTAMPTZ '2026-07-30 08:15:00+02', DATE '2026-08-07', NULL, 11380,
  'Caída sin consecuencias: espejo derecho roto y embrague duro.',
  'Espejo derecho a sustituir. Cable de embrague en buen estado, solo necesitaba ajuste.',
  3, 'ESPERANDO_PIEZAS', 45.00, TIMESTAMPTZ '2026-07-30 10:45:00+02', TIMESTAMPTZ '2026-07-30 11:30:00+02', 'Talleres y Flotas Delta S.L.', NULL,
  'Sin existencias de ESP-RET-DER. Pedido al proveedor el 30/07.', 2),

 -- Presupuesto enviado, esperando respuesta del cliente.
 (8, 2026,  8,  2, 1, TIMESTAMPTZ '2026-08-01 09:45:00+02', DATE '2026-08-08', NULL, 41180,
  'Revisión general antes de las vacaciones.',
  'Revisión de mantenimiento: cambio de aceite y filtro. Resto de elementos en buen estado.',
  3, 'PRESUPUESTADA', 45.00, TIMESTAMPTZ '2026-08-01 12:20:00+02', NULL, NULL, NULL, NULL, 2),

 -- El tecnico esta valorando la averia.
 (9, 2026,  9,  9, 7, TIMESTAMPTZ '2026-08-03 10:00:00+02', DATE '2026-08-07', NULL, 12080,
  'Se apaga en marcha de forma intermitente, sobre todo en caliente.',
  NULL,
  4, 'EN_DIAGNOSTICO', 45.00, NULL, NULL, NULL, NULL, 'Cliente pendiente de aportar datos fiscales para poder facturar.', 2),

 -- Presupuesto aprobado, todavia sin entrar en taller.
 (10, 2026, 10,  3, 2, TIMESTAMPTZ '2026-08-04 09:20:00+02', DATE '2026-08-11', NULL, 15790,
  'Revisión de los 15.000 km.',
  'Revisión programada: aceite, filtro y bujias.',
  4, 'APROBADA', 45.00, TIMESTAMPTZ '2026-08-04 11:00:00+02', TIMESTAMPTZ '2026-08-04 16:30:00+02', 'Marta Iglesias Rubio', NULL, NULL, 2),

 -- Recien entrada hoy: aun sin diagnostico.
 (11, 2026, 11,  1, 1, TIMESTAMPTZ '2026-08-05 09:05:00+02', DATE '2026-08-12', NULL, 24500,
  'Ruido en la transmisión a partir de 4000 rpm.',
  NULL,
  NULL, 'RECIBIDA', 45.00, NULL, NULL, NULL, NULL, NULL, 2);


-- ---------------------------------------------------------------------
-- Lineas de las ordenes de trabajo
-- ---------------------------------------------------------------------
-- Los precios estan CONGELADOS: coinciden con el catalogo de hoy, pero si
-- manana sube el precio de una pieza, estas lineas no se moveran.
INSERT INTO linea_ot (id, orden_trabajo_id, numero_linea, tipo, descripcion, pieza_id, cantidad, precio_unitario, descuento_pct, tipo_iva, porcentaje_iva, created_by) VALUES
 -- OT-2026-00001
 ( 1,  1, 1, 'MANO_DE_OBRA', 'Revisión programada 20.000 km',                 NULL,  2.500,  45.0000, 0, 'GENERAL', 21.00, 3),
 ( 2,  1, 2, 'PIEZA',        'Aceite motor 10W-40 semisintético 1 L',            1,  3.000,  12.9000, 0, 'GENERAL', 21.00, 3),
 ( 3,  1, 3, 'PIEZA',        'Filtro de aceite HF204',                           2,  1.000,   9.5000, 0, 'GENERAL', 21.00, 3),
 ( 4,  1, 4, 'PIEZA',        'Bujia NGK CR8E',                                   4,  2.000,   8.7500, 0, 'GENERAL', 21.00, 3),
 ( 5,  1, 5, 'PIEZA',        'Filtro de aire HFA1618',                           3,  1.000,  24.0000, 0, 'GENERAL', 21.00, 3),
 -- OT-2026-00002
 ( 6,  2, 1, 'MANO_DE_OBRA', 'Sustitución de neumaticos y pastillas de freno', NULL,  3.000,  45.0000, 0, 'GENERAL', 21.00, 4),
 ( 7,  2, 2, 'PIEZA',        'Neumatico delantero 120/70 ZR17',                  9,  1.000, 159.0000, 0, 'GENERAL', 21.00, 4),
 ( 8,  2, 3, 'PIEZA',        'Neumatico trasero 180/55 ZR17',                   10,  1.000, 209.0000, 0, 'GENERAL', 21.00, 4),
 ( 9,  2, 4, 'PIEZA',        'Pastillas de freno delanteras sinterizadas',       5,  1.000,  39.9000, 0, 'GENERAL', 21.00, 4),
 (10,  2, 5, 'PIEZA',        'Liquido de frenos DOT 4 500 ml',                  13,  1.000,  14.9000, 0, 'GENERAL', 21.00, 4),
 -- OT-2026-00003
 (11,  3, 1, 'MANO_DE_OBRA', 'Mantenimiento periodico de flota',               NULL,  1.500,  45.0000, 0, 'GENERAL', 21.00, 3),
 (12,  3, 2, 'PIEZA',        'Aceite motor 10W-40 semisintético 1 L',            1,  1.000,  12.9000, 0, 'GENERAL', 21.00, 3),
 (13,  3, 3, 'PIEZA',        'Filtro de aceite HF204',                           2,  1.000,   9.5000, 0, 'GENERAL', 21.00, 3),
 (14,  3, 4, 'PIEZA',        'Lampara faro H4 12V 60/55W',                      17,  1.000,  10.9000, 0, 'GENERAL', 21.00, 3),
 -- OT-2026-00004 (presupuestada y rechazada: nunca llego a consumir piezas)
 (15,  4, 1, 'MANO_DE_OBRA', 'Desmontaje de culata y sustitución de junta',    NULL,  8.000,  45.0000, 0, 'GENERAL', 21.00, 4),
 (16,  4, 2, 'PIEZA',        'Junta de culata',                                 14,  1.000,  52.0000, 0, 'GENERAL', 21.00, 4),
 -- OT-2026-00005
 (17,  5, 1, 'MANO_DE_OBRA', 'Sustitución de kit de transmisión',              NULL,  2.000,  45.0000, 0, 'GENERAL', 21.00, 3),
 (18,  5, 2, 'PIEZA',        'Kit de transmisión 525 (cadena y pinones)',        8,  1.000, 168.0000, 0, 'GENERAL', 21.00, 3),
 -- OT-2026-00006
 (19,  6, 1, 'MANO_DE_OBRA', 'Sustitución de pastillas traseras y batería',    NULL,  1.500,  45.0000, 0, 'GENERAL', 21.00, 4),
 (20,  6, 2, 'PIEZA',        'Pastillas de freno traseras sinterizadas',         6,  1.000,  33.5000, 0, 'GENERAL', 21.00, 4),
 (21,  6, 3, 'PIEZA',        'Batería YTX12-BS 12V 10Ah',                       11,  1.000,  89.9000, 0, 'GENERAL', 21.00, 4),
 -- OT-2026-00007 (bloqueada: la pieza no esta en stock, no hay salida de almacen)
 (22,  7, 1, 'MANO_DE_OBRA', 'Sustitución de espejo y ajuste de embrague',     NULL,  1.000,  45.0000, 0, 'GENERAL', 21.00, 3),
 (23,  7, 2, 'PIEZA',        'Espejo retrovisor derecho universal',             16,  1.000,  27.5000, 0, 'GENERAL', 21.00, 3),
 -- OT-2026-00008 (presupuestada: todavia sin consumir)
 (24,  8, 1, 'MANO_DE_OBRA', 'Revisión general de mantenimiento',              NULL,  2.000,  45.0000, 0, 'GENERAL', 21.00, 3),
 (25,  8, 2, 'PIEZA',        'Aceite motor 10W-40 semisintético 1 L',            1,  3.000,  12.9000, 0, 'GENERAL', 21.00, 3),
 (26,  8, 3, 'PIEZA',        'Filtro de aceite HF204',                           2,  1.000,   9.5000, 0, 'GENERAL', 21.00, 3),
 -- OT-2026-00010 (aprobada: se consumira al pasar a EN_REPARACION)
 (27, 10, 1, 'MANO_DE_OBRA', 'Revisión programada 15.000 km',                  NULL,  2.000,  45.0000, 0, 'GENERAL', 21.00, 4),
 (28, 10, 2, 'PIEZA',        'Aceite motor 10W-40 semisintético 1 L',            1,  3.000,  12.9000, 0, 'GENERAL', 21.00, 4),
 (29, 10, 3, 'PIEZA',        'Filtro de aceite HF204',                           2,  1.000,   9.5000, 0, 'GENERAL', 21.00, 4),
 (30, 10, 4, 'PIEZA',        'Bujia NGK CR8E',                                   4,  4.000,   8.7500, 0, 'GENERAL', 21.00, 4);


-- ---------------------------------------------------------------------
-- Consumo de almacen de las OT que han entrado en reparacion
-- ---------------------------------------------------------------------
-- Solo salen piezas de las OT que llegaron a EN_REPARACION. Las
-- presupuestadas, aprobadas o rechazadas no han tocado el almacen.
INSERT INTO movimiento_stock (pieza_id, tipo, cantidad, fecha, usuario_id, orden_trabajo_id, linea_ot_id, motivo) VALUES
 -- OT-2026-00001
 ( 1, 'SALIDA', -3, TIMESTAMPTZ '2026-05-13 10:20:00+02', 3, 1,  2, NULL),
 ( 2, 'SALIDA', -1, TIMESTAMPTZ '2026-05-13 10:20:00+02', 3, 1,  3, NULL),
 ( 4, 'SALIDA', -2, TIMESTAMPTZ '2026-05-13 10:25:00+02', 3, 1,  4, NULL),
 ( 3, 'SALIDA', -1, TIMESTAMPTZ '2026-05-13 10:25:00+02', 3, 1,  5, NULL),
 -- OT-2026-00002
 ( 9, 'SALIDA', -1, TIMESTAMPTZ '2026-06-03 09:30:00+02', 4, 2,  7, NULL),
 (10, 'SALIDA', -1, TIMESTAMPTZ '2026-06-03 09:30:00+02', 4, 2,  8, NULL),
 ( 5, 'SALIDA', -1, TIMESTAMPTZ '2026-06-03 11:00:00+02', 4, 2,  9, NULL),
 (13, 'SALIDA', -1, TIMESTAMPTZ '2026-06-03 11:00:00+02', 4, 2, 10, NULL),
 -- OT-2026-00003
 ( 1, 'SALIDA', -1, TIMESTAMPTZ '2026-06-21 09:10:00+02', 3, 3, 12, NULL),
 ( 2, 'SALIDA', -1, TIMESTAMPTZ '2026-06-21 09:10:00+02', 3, 3, 13, NULL),
 (17, 'SALIDA', -1, TIMESTAMPTZ '2026-06-21 09:40:00+02', 3, 3, 14, NULL),
 -- OT-2026-00005
 ( 8, 'SALIDA', -1, TIMESTAMPTZ '2026-07-23 10:00:00+02', 3, 5, 18, NULL),
 -- OT-2026-00006
 ( 6, 'SALIDA', -1, TIMESTAMPTZ '2026-07-29 09:50:00+02', 4, 6, 20, NULL),
 (11, 'SALIDA', -1, TIMESTAMPTZ '2026-07-29 10:15:00+02', 4, 6, 21, NULL);


-- ---------------------------------------------------------------------
-- Historial de estados
-- ---------------------------------------------------------------------
INSERT INTO cambio_estado_ot (orden_trabajo_id, estado_anterior, estado_nuevo, fecha, usuario_id, motivo) VALUES
 -- OT-2026-00001
 (1, NULL,             'RECIBIDA',        TIMESTAMPTZ '2026-05-12 09:10:00+02', 2, 'Entrada de la moto en el taller'),
 (1, 'RECIBIDA',       'EN_DIAGNOSTICO',  TIMESTAMPTZ '2026-05-12 10:00:00+02', 3, NULL),
 (1, 'EN_DIAGNOSTICO', 'PRESUPUESTADA',   TIMESTAMPTZ '2026-05-12 12:30:00+02', 3, NULL),
 (1, 'PRESUPUESTADA',  'APROBADA',        TIMESTAMPTZ '2026-05-12 17:45:00+02', 2, 'Aprobado por telefono'),
 (1, 'APROBADA',       'EN_REPARACION',   TIMESTAMPTZ '2026-05-13 09:00:00+02', 3, NULL),
 (1, 'EN_REPARACION',  'LISTA',           TIMESTAMPTZ '2026-05-14 16:30:00+02', 3, NULL),
 -- OT-2026-00002
 (2, NULL,             'RECIBIDA',        TIMESTAMPTZ '2026-06-02 08:40:00+02', 2, 'Entrada de la moto en el taller'),
 (2, 'RECIBIDA',       'EN_DIAGNOSTICO',  TIMESTAMPTZ '2026-06-02 09:20:00+02', 4, NULL),
 (2, 'EN_DIAGNOSTICO', 'PRESUPUESTADA',   TIMESTAMPTZ '2026-06-02 11:15:00+02', 4, NULL),
 (2, 'PRESUPUESTADA',  'APROBADA',        TIMESTAMPTZ '2026-06-02 13:00:00+02', 2, 'Aprobado en mostrador'),
 (2, 'APROBADA',       'EN_REPARACION',   TIMESTAMPTZ '2026-06-03 09:00:00+02', 4, NULL),
 (2, 'EN_REPARACION',  'LISTA',           TIMESTAMPTZ '2026-06-04 13:00:00+02', 4, NULL),
 -- OT-2026-00003
 (3, NULL,             'RECIBIDA',        TIMESTAMPTZ '2026-06-20 08:00:00+02', 2, 'Entrada de la moto en el taller'),
 (3, 'RECIBIDA',       'EN_DIAGNOSTICO',  TIMESTAMPTZ '2026-06-20 08:45:00+02', 3, NULL),
 (3, 'EN_DIAGNOSTICO', 'PRESUPUESTADA',   TIMESTAMPTZ '2026-06-20 10:00:00+02', 3, NULL),
 (3, 'PRESUPUESTADA',  'APROBADA',        TIMESTAMPTZ '2026-06-20 10:20:00+02', 2, 'Aprobacion automatica por acuerdo de flota'),
 (3, 'APROBADA',       'EN_REPARACION',   TIMESTAMPTZ '2026-06-21 09:00:00+02', 3, NULL),
 (3, 'EN_REPARACION',  'LISTA',           TIMESTAMPTZ '2026-06-22 12:00:00+02', 3, NULL),
 -- OT-2026-00004: rechazada
 (4, NULL,             'RECIBIDA',        TIMESTAMPTZ '2026-07-01 10:30:00+02', 2, 'Entrada de la moto en el taller'),
 (4, 'RECIBIDA',       'EN_DIAGNOSTICO',  TIMESTAMPTZ '2026-07-01 15:00:00+02', 4, NULL),
 (4, 'EN_DIAGNOSTICO', 'PRESUPUESTADA',   TIMESTAMPTZ '2026-07-02 13:00:00+02', 4, NULL),
 (4, 'PRESUPUESTADA',  'RECHAZADA',       TIMESTAMPTZ '2026-07-03 16:40:00+02', 2, 'El cliente rechaza el presupuesto y recoge la moto'),
 -- OT-2026-00005
 (5, NULL,             'RECIBIDA',        TIMESTAMPTZ '2026-07-22 09:00:00+02', 2, 'Entrada de la moto en el taller'),
 (5, 'RECIBIDA',       'EN_DIAGNOSTICO',  TIMESTAMPTZ '2026-07-22 09:40:00+02', 3, NULL),
 (5, 'EN_DIAGNOSTICO', 'PRESUPUESTADA',   TIMESTAMPTZ '2026-07-22 11:30:00+02', 3, NULL),
 (5, 'PRESUPUESTADA',  'APROBADA',        TIMESTAMPTZ '2026-07-22 12:10:00+02', 2, NULL),
 (5, 'APROBADA',       'EN_REPARACION',   TIMESTAMPTZ '2026-07-23 09:30:00+02', 3, NULL),
 (5, 'EN_REPARACION',  'LISTA',           TIMESTAMPTZ '2026-07-24 11:00:00+02', 3, NULL),
 -- OT-2026-00006
 (6, NULL,             'RECIBIDA',        TIMESTAMPTZ '2026-07-28 09:30:00+02', 2, 'Entrada de la moto en el taller'),
 (6, 'RECIBIDA',       'EN_DIAGNOSTICO',  TIMESTAMPTZ '2026-07-28 10:30:00+02', 4, NULL),
 (6, 'EN_DIAGNOSTICO', 'PRESUPUESTADA',   TIMESTAMPTZ '2026-07-28 12:00:00+02', 4, NULL),
 (6, 'PRESUPUESTADA',  'APROBADA',        TIMESTAMPTZ '2026-07-28 15:20:00+02', 2, NULL),
 (6, 'APROBADA',       'EN_REPARACION',   TIMESTAMPTZ '2026-07-29 09:30:00+02', 4, NULL),
 -- OT-2026-00007: bloqueada por falta de stock
 (7, NULL,             'RECIBIDA',        TIMESTAMPTZ '2026-07-30 08:15:00+02', 2, 'Entrada de la moto en el taller'),
 (7, 'RECIBIDA',       'EN_DIAGNOSTICO',  TIMESTAMPTZ '2026-07-30 09:30:00+02', 3, NULL),
 (7, 'EN_DIAGNOSTICO', 'PRESUPUESTADA',   TIMESTAMPTZ '2026-07-30 10:45:00+02', 3, NULL),
 (7, 'PRESUPUESTADA',  'APROBADA',        TIMESTAMPTZ '2026-07-30 11:30:00+02', 2, NULL),
 (7, 'APROBADA',       'EN_REPARACION',   TIMESTAMPTZ '2026-07-31 09:00:00+02', 3, NULL),
 (7, 'EN_REPARACION',  'ESPERANDO_PIEZAS',TIMESTAMPTZ '2026-07-31 09:05:00+02', 3, 'Sin existencias de ESP-RET-DER: pedido al proveedor'),
 -- OT-2026-00008
 (8, NULL,             'RECIBIDA',        TIMESTAMPTZ '2026-08-01 09:45:00+02', 2, 'Entrada de la moto en el taller'),
 (8, 'RECIBIDA',       'EN_DIAGNOSTICO',  TIMESTAMPTZ '2026-08-01 10:30:00+02', 3, NULL),
 (8, 'EN_DIAGNOSTICO', 'PRESUPUESTADA',   TIMESTAMPTZ '2026-08-01 12:20:00+02', 3, 'Presupuesto enviado por email'),
 -- OT-2026-00009
 (9, NULL,             'RECIBIDA',        TIMESTAMPTZ '2026-08-03 10:00:00+02', 2, 'Entrada de la moto en el taller'),
 (9, 'RECIBIDA',       'EN_DIAGNOSTICO',  TIMESTAMPTZ '2026-08-03 11:15:00+02', 4, NULL),
 -- OT-2026-00010
 (10, NULL,            'RECIBIDA',        TIMESTAMPTZ '2026-08-04 09:20:00+02', 2, 'Entrada de la moto en el taller'),
 (10, 'RECIBIDA',      'EN_DIAGNOSTICO',  TIMESTAMPTZ '2026-08-04 10:00:00+02', 4, NULL),
 (10, 'EN_DIAGNOSTICO','PRESUPUESTADA',   TIMESTAMPTZ '2026-08-04 11:00:00+02', 4, NULL),
 (10, 'PRESUPUESTADA', 'APROBADA',        TIMESTAMPTZ '2026-08-04 16:30:00+02', 2, 'Aprobado por telefono'),
 -- OT-2026-00011
 (11, NULL,            'RECIBIDA',        TIMESTAMPTZ '2026-08-05 09:05:00+02', 2, 'Entrada de la moto en el taller');


-- ---------------------------------------------------------------------
-- Entrega de las tres primeras OT
-- ---------------------------------------------------------------------
-- A partir de aqui esas OT quedan CONGELADAS: cualquier UPDATE o DELETE
-- posterior sobre ellas o sobre sus lineas sera rechazado por la BD.
UPDATE orden_trabajo SET estado = 'ENTREGADA', fecha_real_salida = TIMESTAMPTZ '2026-05-15 18:20:00+02' WHERE id = 1;
UPDATE orden_trabajo SET estado = 'ENTREGADA', fecha_real_salida = TIMESTAMPTZ '2026-06-05 10:10:00+02' WHERE id = 2;
UPDATE orden_trabajo SET estado = 'ENTREGADA', fecha_real_salida = TIMESTAMPTZ '2026-06-23 17:45:00+02' WHERE id = 3;

INSERT INTO cambio_estado_ot (orden_trabajo_id, estado_anterior, estado_nuevo, fecha, usuario_id, motivo) VALUES
 (1, 'LISTA', 'ENTREGADA', TIMESTAMPTZ '2026-05-15 18:20:00+02', 2, 'Entregada al cliente y facturada'),
 (2, 'LISTA', 'ENTREGADA', TIMESTAMPTZ '2026-06-05 10:10:00+02', 2, 'Entregada al cliente y facturada'),
 (3, 'LISTA', 'ENTREGADA', TIMESTAMPTZ '2026-06-23 17:45:00+02', 2, 'Entregada al cliente y facturada');


-- ---------------------------------------------------------------------
-- Series de facturacion
-- ---------------------------------------------------------------------
INSERT INTO serie_factura (id, codigo, ejercicio, descripcion, tipo, ultimo_numero, activa) VALUES
 (1, 'A', 2026, 'Serie general de facturacion 2026',  'ORDINARIA',     0, TRUE),
 (2, 'R', 2026, 'Serie de facturas rectificativas 2026', 'RECTIFICATIVA', 0, TRUE);


-- ---------------------------------------------------------------------
-- Historico del ejercicio anterior: un año completo de actividad
--
-- Sin esto la demostracion solo tiene cuatro facturas de dos meses, y las
-- graficas del informe salen vacias. Aqui se genera un 2025 entero: doce meses
-- de ordenes cerradas y facturadas con sus compras de material.
--
-- Se genera de verdad, no se falsea. Cada factura pasa por los mismos triggers
-- que una emitida desde la aplicacion —numeracion correlativa, posicion de
-- registro correlativa y cadena SHA-256 encadenada—, asi que si un importe no
-- cuadrase con sus lineas esta migracion fallaria al aplicarse.
--
-- Va ANTES del bloque de facturas de 2026 para que la posicion en el registro
-- siga el orden en que se emitieron: las de 2025 ocupan las primeras y las de
-- 2026 continuan detras. Un registro de facturacion en el que el numero 1 es
-- posterior al numero 5 no se sostiene.
-- ---------------------------------------------------------------------
-- ---------------------------------------------------------------------
-- Serie y contador del ejercicio anterior
-- ---------------------------------------------------------------------
INSERT INTO serie_factura (id, codigo, ejercicio, descripcion, tipo, ultimo_numero, activa) VALUES
 (10, 'A', 2025, 'Facturas ordinarias 2025', 'ORDINARIA', 0, TRUE);

INSERT INTO contador_ot (ejercicio, ultimo_numero) VALUES (2025, 0);


DO $$
DECLARE
    c_nif_emisor CONSTANT VARCHAR(20) := 'B87654323';
    c_tarifa     CONSTANT NUMERIC     := 45.00;

    -- Trabajos tipo del taller: descripcion, horas y las piezas que llevan.
    -- Se van rotando mes a mes para que la facturacion no salga plana.
    c_trabajos CONSTANT TEXT[] := ARRAY[
        'Revisión programada de mantenimiento',
        'Sustitución del kit de transmisión',
        'Cambio de pastillas y purga de frenos',
        'Sustitución de neumático y equilibrado',
        'Puesta a punto de carburación',
        'Cambio de aceite y filtros',
        'Revisión pre-ITV',
        'Sustitución de batería y revisión de carga'
    ];

    v_huella_previa VARCHAR(64);
    v_registro      BIGINT;
    v_numero        INTEGER := 0;
    v_factura_id    BIGINT  := 1000;
    v_orden_id      BIGINT  := 1000;
    v_linea_id      BIGINT  := 5000;

    v_mes           INTEGER;
    v_dia           INTEGER;
    v_factura_mes   INTEGER;
    v_por_mes       INTEGER;
    v_fecha         DATE;
    v_ts            TIMESTAMPTZ;
    v_entrada       TIMESTAMPTZ;

    v_cliente       BIGINT;
    v_moto          BIGINT;
    v_tecnico       BIGINT;
    v_horas         NUMERIC;
    v_trabajo       TEXT;

    v_base          NUMERIC(12,2);
    v_cuota         NUMERIC(12,2);
    v_total         NUMERIC(12,2);
    v_linea_base    NUMERIC(12,2);
    v_linea_cuota   NUMERIC(12,2);
    v_num_linea     INTEGER;

    v_cadena        TEXT;
    v_huella        VARCHAR(64);
    v_semilla       INTEGER;

    p               RECORD;
    v_piezas        BIGINT[];
    v_cantidades    NUMERIC[];
    i               INTEGER;
BEGIN
    -- Se arranca donde este el registro: si V900 ya metio facturas, esto
    -- continua su cadena en vez de abrir otra.
    SELECT COALESCE(MAX(numero_registro), 0) INTO v_registro FROM factura;
    SELECT COALESCE((SELECT huella FROM factura ORDER BY numero_registro DESC LIMIT 1),
                    repeat('0', 64))
      INTO v_huella_previa;

    FOR v_mes IN 1..12 LOOP
        -- Entre 3 y 6 facturas al mes, con mas trabajo en primavera y antes de
        -- vacaciones, que es como funciona un taller de motos.
        v_por_mes := 3 + ((v_mes * 7) % 4);

        FOR v_factura_mes IN 1..v_por_mes LOOP
            v_semilla := v_mes * 13 + v_factura_mes * 7;
            -- El dia crece con el numero de factura del mes: el registro de
            -- facturacion tiene que seguir el orden de emision, y con un dia al
            -- azar salian facturas posteriores con fecha anterior.
            v_dia     := 2 + (v_factura_mes - 1) * 4 + (v_semilla % 3);
            v_fecha   := make_date(2025, v_mes, v_dia);
            v_ts      := v_fecha + TIME '17:30:00';
            v_entrada := (v_fecha - ((v_semilla % 5) + 2)) + TIME '09:15:00';

            -- Solo clientes facturables: sin datos fiscales no se puede emitir
            -- una factura, y es justo lo que impide el NOT NULL de receptor_nif.
            -- (El cliente sin documento de la demo esta ahi a proposito, para
            -- que se vea la advertencia de «faltan datos» en su ficha.)
            SELECT c.id, m.id INTO v_cliente, v_moto
              FROM cliente c
              JOIN moto m ON m.cliente_id = c.id AND m.activo
             WHERE c.documento IS NOT NULL AND c.activo
             ORDER BY c.id, m.id
             OFFSET (v_semilla % (SELECT COUNT(*)
                                    FROM cliente c2
                                    JOIN moto m2 ON m2.cliente_id = c2.id AND m2.activo
                                   WHERE c2.documento IS NOT NULL AND c2.activo))
             LIMIT 1;

            v_tecnico := CASE WHEN v_semilla % 2 = 0 THEN 3 ELSE 4 END;
            v_horas   := 1.0 + ((v_semilla % 7) * 0.5);
            v_trabajo := c_trabajos[1 + (v_semilla % array_length(c_trabajos, 1))];

            -- ----- Orden de trabajo, ya entregada -----
            v_orden_id := v_orden_id + 1;
            UPDATE contador_ot SET ultimo_numero = ultimo_numero + 1 WHERE ejercicio = 2025;

            INSERT INTO orden_trabajo (
                id, ejercicio, numero, moto_id, cliente_id,
                fecha_entrada, fecha_estimada_salida, fecha_real_salida,
                km_entrada, problema_reportado, diagnostico, estado,
                tecnico_id, tarifa_hora, fecha_presupuesto, fecha_aprobacion, aprobado_por,
                created_by, updated_by)
            SELECT v_orden_id, 2025,
                   (SELECT ultimo_numero FROM contador_ot WHERE ejercicio = 2025),
                   v_moto, v_cliente,
                   v_entrada, v_fecha, NULL,
                   m.km_actual - (400 * (12 - v_mes)),
                   v_trabajo || '.',
                   'Trabajo realizado y comprobado en banco.',
                   -- Nace en reparacion: una orden ENTREGADA ya no admite
                   -- lineas nuevas, y todavia hay que ponerselas. Se entrega
                   -- mas abajo, igual que haria la aplicacion.
                   'EN_REPARACION',
                   v_tecnico, c_tarifa,
                   v_entrada + INTERVAL '3 hours', v_entrada + INTERVAL '6 hours',
                   TRIM(c.nombre || ' ' || COALESCE(c.apellidos, '')),
                   2, 2
              FROM moto m JOIN cliente c ON c.id = v_cliente
             WHERE m.id = v_moto;

            -- ----- Piezas del trabajo: entre una y tres -----
            v_piezas     := ARRAY[]::BIGINT[];
            v_cantidades := ARRAY[]::NUMERIC[];
            FOR i IN 0..(v_semilla % 3) LOOP
                v_piezas     := v_piezas || (1 + ((v_semilla + i * 5) % 18))::BIGINT;
                v_cantidades := v_cantidades || (1 + ((v_semilla + i) % 3))::NUMERIC;
            END LOOP;

            -- Se compra ANTES lo que se va a gastar: asi el almacen nunca queda
            -- en negativo y el stock final es el que dejo V900.
            FOR i IN 1..array_length(v_piezas, 1) LOOP
                INSERT INTO movimiento_stock (
                    pieza_id, tipo, cantidad, fecha, usuario_id, motivo,
                    documento_proveedor, precio_coste_unitario)
                SELECT v_piezas[i], 'ENTRADA', v_cantidades[i],
                       v_entrada - INTERVAL '2 days', 1,
                       'Reposición de almacén',
                       'ALB-2025-' || LPAD((v_mes * 100 + v_factura_mes)::text, 5, '0'),
                       pz.precio_coste
                  FROM pieza pz WHERE pz.id = v_piezas[i];
            END LOOP;

            -- ----- Lineas de la orden y consumo de almacen -----
            v_linea_id := v_linea_id + 1;
            INSERT INTO linea_ot (id, orden_trabajo_id, numero_linea, tipo, descripcion,
                                  pieza_id, cantidad, precio_unitario, descuento_pct,
                                  tipo_iva, porcentaje_iva, created_by)
            VALUES (v_linea_id, v_orden_id, 1, 'MANO_DE_OBRA', v_trabajo,
                    NULL, v_horas, c_tarifa, 0, 'GENERAL', 21.00, v_tecnico);

            v_num_linea := 1;
            FOR i IN 1..array_length(v_piezas, 1) LOOP
                v_num_linea := v_num_linea + 1;
                v_linea_id  := v_linea_id + 1;

                INSERT INTO linea_ot (id, orden_trabajo_id, numero_linea, tipo, descripcion,
                                      pieza_id, cantidad, precio_unitario, descuento_pct,
                                      tipo_iva, porcentaje_iva, created_by)
                SELECT v_linea_id, v_orden_id, v_num_linea, 'PIEZA', pz.descripcion,
                       pz.id, v_cantidades[i], pz.precio_venta, 0, pz.tipo_iva, t.porcentaje, v_tecnico
                  FROM pieza pz JOIN tipo_iva t ON t.codigo = pz.tipo_iva
                 WHERE pz.id = v_piezas[i];

                INSERT INTO movimiento_stock (
                    pieza_id, tipo, cantidad, fecha, usuario_id,
                    orden_trabajo_id, linea_ot_id, motivo, precio_coste_unitario)
                SELECT v_piezas[i], 'SALIDA', -v_cantidades[i],
                       v_entrada + INTERVAL '1 day', v_tecnico,
                       v_orden_id, v_linea_id, 'Consumo en orden de trabajo', pz.precio_coste
                  FROM pieza pz WHERE pz.id = v_piezas[i];
            END LOOP;

            -- Ya tiene todas sus lineas: ahora se puede entregar.
            UPDATE orden_trabajo
               SET estado = 'ENTREGADA', fecha_real_salida = v_ts, updated_by = 2
             WHERE id = v_orden_id;

            -- ----- Totales de la factura -----
            -- Con la MISMA formula que las columnas generadas de linea_factura:
            -- primero se redondea la base de cada linea, y sobre esa base
            -- redondeada se calcula la cuota. Hacerlo de otra forma daria un
            -- centimo de diferencia y el trigger de totales rechazaria la
            -- factura al hacer commit.
            v_base  := 0;
            v_cuota := 0;

            v_linea_base  := ROUND(v_horas * c_tarifa, 2);
            v_linea_cuota := ROUND(v_linea_base * 21.00 / 100, 2);
            v_base  := v_base + v_linea_base;
            v_cuota := v_cuota + v_linea_cuota;

            FOR i IN 1..array_length(v_piezas, 1) LOOP
                SELECT ROUND(v_cantidades[i] * pz.precio_venta, 2)
                  INTO v_linea_base
                  FROM pieza pz WHERE pz.id = v_piezas[i];
                SELECT ROUND(v_linea_base * t.porcentaje / 100, 2)
                  INTO v_linea_cuota
                  FROM pieza pz JOIN tipo_iva t ON t.codigo = pz.tipo_iva
                 WHERE pz.id = v_piezas[i];

                v_base  := v_base + v_linea_base;
                v_cuota := v_cuota + v_linea_cuota;
            END LOOP;

            v_total := v_base + v_cuota;

            -- ----- Huella encadenada -----
            v_numero     := v_numero + 1;
            v_registro   := v_registro + 1;
            v_factura_id := v_factura_id + 1;

            v_cadena := format(
                'NIFEmisor=%s&NumSerieFactura=%s&FechaExpedicion=%s&TipoFactura=%s&CuotaTotal=%s&ImporteTotal=%s&Huella=%s&FechaHoraHusoGenRegistro=%s',
                c_nif_emisor,
                'A/2025/' || LPAD(v_numero::text, 6, '0'),
                to_char(v_fecha, 'DD-MM-YYYY'),
                'ORDINARIA',
                to_char(v_cuota, 'FM9999999990.00'),
                to_char(v_total, 'FM9999999990.00'),
                v_huella_previa,
                to_char(v_ts AT TIME ZONE 'Europe/Madrid', 'YYYY-MM-DD"T"HH24:MI:SS') ||
                    to_char(v_ts, 'OF')
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
            SELECT v_factura_id, 10, 'A', 2025, v_numero, 'ORDINARIA',
                   v_orden_id, v_fecha, v_fecha, v_ts,
                   cfg.razon_social, cfg.nif, cfg.direccion, cfg.codigo_postal, cfg.ciudad,
                   cfg.provincia, cfg.pais,
                   c.id, TRIM(c.nombre || ' ' || COALESCE(c.apellidos, '')),
                   c.documento, c.direccion, c.codigo_postal, c.ciudad, c.provincia, c.pais,
                   m.matricula, m.marca || ' ' || m.modelo, o.codigo,
                   v_base, v_cuota, v_total,
                   v_registro, v_huella_previa, v_huella, v_cadena, 'SHA-256',
                   format('https://verifactu.motorsport19.example/verifica?nif=%s&numserie=%s&fecha=%s&importe=%s',
                          c_nif_emisor, 'A/2025/' || LPAD(v_numero::text, 6, '0'),
                          to_char(v_fecha, 'DD-MM-YYYY'), to_char(v_total, 'FM9999999990.00')),
                   cfg.software_nombre, cfg.software_version, cfg.software_nif, v_ts, 2
              FROM configuracion_taller cfg
              JOIN orden_trabajo o ON o.id = v_orden_id
              JOIN moto m          ON m.id = v_moto
              JOIN cliente c       ON c.id = v_cliente
             WHERE cfg.id = 1;

            UPDATE serie_factura SET ultimo_numero = v_numero WHERE id = 10;

            -- ----- Lineas de la factura (copiadas de la orden) -----
            INSERT INTO linea_factura (factura_id, numero_linea, tipo, descripcion, pieza_sku,
                                       cantidad, precio_unitario, descuento_pct, tipo_iva, porcentaje_iva)
            SELECT v_factura_id, l.numero_linea, l.tipo, l.descripcion, pz.sku,
                   l.cantidad, l.precio_unitario, l.descuento_pct, l.tipo_iva, l.porcentaje_iva
              FROM linea_ot l
         LEFT JOIN pieza pz ON pz.id = l.pieza_id
             WHERE l.orden_trabajo_id = v_orden_id
             ORDER BY l.numero_linea;

            -- ----- Desglose de IVA -----
            INSERT INTO desglose_iva_factura (factura_id, tipo_iva, porcentaje_iva, base_imponible, cuota_iva)
            SELECT v_factura_id, l.tipo_iva, l.porcentaje_iva,
                   SUM(l.base_imponible), SUM(l.cuota_iva)
              FROM linea_factura l
             WHERE l.factura_id = v_factura_id
             GROUP BY l.tipo_iva, l.porcentaje_iva;

            -- ----- Historial de estados de la orden -----
            INSERT INTO cambio_estado_ot (orden_trabajo_id, estado_anterior, estado_nuevo, fecha, usuario_id, motivo) VALUES
             (v_orden_id, NULL,               'RECIBIDA',       v_entrada,                        2, 'Entrada de la moto en el taller'),
             (v_orden_id, 'RECIBIDA',         'EN_DIAGNOSTICO', v_entrada + INTERVAL '2 hours',   v_tecnico, NULL),
             (v_orden_id, 'EN_DIAGNOSTICO',   'PRESUPUESTADA',  v_entrada + INTERVAL '3 hours',   v_tecnico, NULL),
             (v_orden_id, 'PRESUPUESTADA',    'APROBADA',       v_entrada + INTERVAL '6 hours',   2, 'Aprobado por el cliente'),
             (v_orden_id, 'APROBADA',         'EN_REPARACION',  v_entrada + INTERVAL '1 day',     v_tecnico, NULL),
             (v_orden_id, 'EN_REPARACION',    'LISTA',          v_ts - INTERVAL '2 hours',        v_tecnico, NULL),
             (v_orden_id, 'LISTA',            'ENTREGADA',      v_ts,                             2, 'Entregada y cobrada');

            INSERT INTO evento_factura (factura_id, tipo_evento, fecha, usuario_id, descripcion) VALUES
             (v_factura_id, 'EMISION', v_ts, 2, 'Emisión de la factura A/2025/' || LPAD(v_numero::text, 6, '0'));

            v_huella_previa := v_huella;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Historico 2025 generado: % facturas, ultimo registro %', v_numero, v_registro;
END
$$;


-- ---------------------------------------------------------------------
-- Facturas, con cadena de huellas SHA-256 real
-- ---------------------------------------------------------------------
-- Las huellas se calculan aqui de verdad, con la misma funcion que usara la
-- fase 4. La cadena canonica es:
--
--   NIFEmisor=...&NumSerieFactura=...&FechaExpedicion=dd-mm-aaaa&TipoFactura=...
--   &CuotaTotal=...&ImporteTotal=...&Huella=<huella anterior>
--   &FechaHoraHusoGenRegistro=<ISO-8601 con huso>
--
-- La primera factura encadena con la huella genesis (64 ceros). Los triggers
-- de V6 verifican en el INSERT que la cadena es coherente: si estos valores
-- estuvieran mal calculados, esta migracion fallaria.
DO $$
DECLARE
    c_nif_emisor CONSTANT VARCHAR(20) := 'B87654323';
    -- Se continua la cadena que dejo el historico, en vez de arrancar otra.
    v_huella_previa VARCHAR(64) := COALESCE(
        (SELECT huella FROM factura ORDER BY numero_registro DESC LIMIT 1),
        repeat('0', 64));
    v_registro_base BIGINT := (SELECT COALESCE(MAX(numero_registro), 0) FROM factura);
    v_cadena        TEXT;
    v_huella        VARCHAR(64);
    v_qr            TEXT;

    -- Datos de cada factura: numero completo, fecha, tipo, cuota, total, timestamp.
    r RECORD;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            (1::BIGINT, 1::BIGINT, 'A'::VARCHAR, 1::INTEGER, 'ORDINARIA'::VARCHAR,
             DATE '2026-05-15', TIMESTAMPTZ '2026-05-15 18:25:00+02', 202.20::NUMERIC, 42.48::NUMERIC, 244.68::NUMERIC,
             1::BIGINT, 1::BIGINT, NULL::BIGINT, NULL::VARCHAR, NULL::TEXT),

            (2::BIGINT, 1::BIGINT, 'A'::VARCHAR, 2::INTEGER, 'ORDINARIA'::VARCHAR,
             DATE '2026-06-05', TIMESTAMPTZ '2026-06-05 10:15:00+02', 557.80::NUMERIC, 117.14::NUMERIC, 674.94::NUMERIC,
             2::BIGINT, 4::BIGINT, NULL::BIGINT, NULL::VARCHAR, NULL::TEXT),

            (3::BIGINT, 1::BIGINT, 'A'::VARCHAR, 3::INTEGER, 'ORDINARIA'::VARCHAR,
             DATE '2026-06-23', TIMESTAMPTZ '2026-06-23 17:50:00+02', 100.80::NUMERIC, 21.18::NUMERIC, 121.98::NUMERIC,
             3::BIGINT, 3::BIGINT, NULL::BIGINT, NULL::VARCHAR, NULL::TEXT),

            -- Rectificativa por sustitucion de la A/2026/000003: se facturaron
            -- 1,5 h de mano de obra cuando el parte del tecnico recogia 1 h.
            (4::BIGINT, 2::BIGINT, 'R'::VARCHAR, 1::INTEGER, 'RECTIFICATIVA'::VARCHAR,
             DATE '2026-06-30', TIMESTAMPTZ '2026-06-30 12:00:00+02', 78.30::NUMERIC, 16.45::NUMERIC, 94.75::NUMERIC,
             3::BIGINT, 3::BIGINT, 3::BIGINT, 'POR_SUSTITUCION'::VARCHAR,
             'Error en las horas de mano de obra: se facturaron 1,5 h cuando el parte de trabajo recoge 1 h.'::TEXT)
        ) AS t(id, serie_id, serie_codigo, numero, tipo, fecha_emision, ts_emision,
               base, cuota, total, orden_id, cliente_id, rectifica_id, tipo_rect, motivo_rect)
        ORDER BY t.id
    LOOP
        v_cadena := format(
            'NIFEmisor=%s&NumSerieFactura=%s&FechaExpedicion=%s&TipoFactura=%s&CuotaTotal=%s&ImporteTotal=%s&Huella=%s&FechaHoraHusoGenRegistro=%s',
            c_nif_emisor,
            r.serie_codigo || '/2026/' || LPAD(r.numero::text, 6, '0'),
            to_char(r.fecha_emision, 'DD-MM-YYYY'),
            r.tipo,
            to_char(r.cuota, 'FM9999999990.00'),
            to_char(r.total, 'FM9999999990.00'),
            v_huella_previa,
            to_char(r.ts_emision AT TIME ZONE 'Europe/Madrid', 'YYYY-MM-DD"T"HH24:MI:SS') ||
                to_char(r.ts_emision, 'OF')
        );

        v_huella := encode(sha256(convert_to(v_cadena, 'UTF8')), 'hex');

        v_qr := format(
            'https://verifactu.motorsport19.example/verifica?nif=%s&numserie=%s&fecha=%s&importe=%s',
            c_nif_emisor,
            r.serie_codigo || '/2026/' || LPAD(r.numero::text, 6, '0'),
            to_char(r.fecha_emision, 'DD-MM-YYYY'),
            to_char(r.total, 'FM9999999990.00')
        );

        INSERT INTO factura (
            id, serie_id, serie_codigo, ejercicio, numero, tipo,
            orden_trabajo_id, factura_rectificada_id, tipo_rectificativa, motivo_rectificacion,
            fecha_emision, fecha_operacion, timestamp_emision,
            emisor_razon_social, emisor_nif, emisor_direccion, emisor_cp, emisor_ciudad, emisor_provincia, emisor_pais,
            receptor_id, receptor_nombre, receptor_nif, receptor_direccion, receptor_cp, receptor_ciudad, receptor_provincia, receptor_pais,
            matricula, descripcion_vehiculo, codigo_ot,
            base_imponible, total_iva, total,
            numero_registro, huella_anterior, huella, cadena_huella, algoritmo_huella, qr_contenido,
            software_nombre, software_version, software_nif, created_at, created_by
        )
        SELECT
            r.id, r.serie_id, r.serie_codigo, 2026, r.numero, r.tipo,
            r.orden_id, r.rectifica_id, r.tipo_rect, r.motivo_rect,
            r.fecha_emision, o.fecha_real_salida::date, r.ts_emision,
            cfg.razon_social, cfg.nif, cfg.direccion, cfg.codigo_postal, cfg.ciudad, cfg.provincia, cfg.pais,
            c.id,
            TRIM(c.nombre || ' ' || COALESCE(c.apellidos, '')),
            c.documento, c.direccion, c.codigo_postal, c.ciudad, c.provincia, c.pais,
            m.matricula, m.marca || ' ' || m.modelo, o.codigo,
            r.base, r.cuota, r.total,
            v_registro_base + r.id, v_huella_previa, v_huella, v_cadena, 'SHA-256', v_qr,
            cfg.software_nombre, cfg.software_version, cfg.software_nif, r.ts_emision, 2
        FROM configuracion_taller cfg
        JOIN orden_trabajo o ON o.id = r.orden_id
        JOIN moto m          ON m.id = o.moto_id
        JOIN cliente c       ON c.id = r.cliente_id
        WHERE cfg.id = 1;

        v_huella_previa := v_huella;
    END LOOP;
END
$$;


-- ---------------------------------------------------------------------
-- Lineas de las facturas (COPIADAS de las OT, no referenciadas)
-- ---------------------------------------------------------------------
INSERT INTO linea_factura (factura_id, numero_linea, tipo, descripcion, pieza_sku, cantidad, precio_unitario, descuento_pct, tipo_iva, porcentaje_iva) VALUES
 -- A/2026/000001
 (1, 1, 'MANO_DE_OBRA', 'Revisión programada 20.000 km',                 NULL,             2.500,  45.0000, 0, 'GENERAL', 21.00),
 (1, 2, 'PIEZA',        'Aceite motor 10W-40 semisintético 1 L',         'ACE-10W40-1L',   3.000,  12.9000, 0, 'GENERAL', 21.00),
 (1, 3, 'PIEZA',        'Filtro de aceite HF204',                        'FIL-ACE-HF204',  1.000,   9.5000, 0, 'GENERAL', 21.00),
 (1, 4, 'PIEZA',        'Bujia NGK CR8E',                                'BUJ-CR8E',       2.000,   8.7500, 0, 'GENERAL', 21.00),
 (1, 5, 'PIEZA',        'Filtro de aire HFA1618',                        'FIL-AIR-HFA1618',1.000,  24.0000, 0, 'GENERAL', 21.00),
 -- A/2026/000002
 (2, 1, 'MANO_DE_OBRA', 'Sustitución de neumaticos y pastillas de freno', NULL,            3.000,  45.0000, 0, 'GENERAL', 21.00),
 (2, 2, 'PIEZA',        'Neumatico delantero 120/70 ZR17',               'NEU-DEL-120-70-17', 1.000, 159.0000, 0, 'GENERAL', 21.00),
 (2, 3, 'PIEZA',        'Neumatico trasero 180/55 ZR17',                 'NEU-TRA-180-55-17', 1.000, 209.0000, 0, 'GENERAL', 21.00),
 (2, 4, 'PIEZA',        'Pastillas de freno delanteras sinterizadas',    'PAS-FRE-DEL-SBS',1.000,  39.9000, 0, 'GENERAL', 21.00),
 (2, 5, 'PIEZA',        'Liquido de frenos DOT 4 500 ml',                'LIQ-FRE-DOT4',   1.000,  14.9000, 0, 'GENERAL', 21.00),
 -- A/2026/000003
 (3, 1, 'MANO_DE_OBRA', 'Mantenimiento periodico de flota',              NULL,             1.500,  45.0000, 0, 'GENERAL', 21.00),
 (3, 2, 'PIEZA',        'Aceite motor 10W-40 semisintético 1 L',         'ACE-10W40-1L',   1.000,  12.9000, 0, 'GENERAL', 21.00),
 (3, 3, 'PIEZA',        'Filtro de aceite HF204',                        'FIL-ACE-HF204',  1.000,   9.5000, 0, 'GENERAL', 21.00),
 (3, 4, 'PIEZA',        'Lampara faro H4 12V 60/55W',                    'LAM-H4',         1.000,  10.9000, 0, 'GENERAL', 21.00),
 -- R/2026/000001 (sustituye integramente a la A/2026/000003, con 1 h de mano de obra)
 (4, 1, 'MANO_DE_OBRA', 'Mantenimiento periodico de flota',              NULL,             1.000,  45.0000, 0, 'GENERAL', 21.00),
 (4, 2, 'PIEZA',        'Aceite motor 10W-40 semisintético 1 L',         'ACE-10W40-1L',   1.000,  12.9000, 0, 'GENERAL', 21.00),
 (4, 3, 'PIEZA',        'Filtro de aceite HF204',                        'FIL-ACE-HF204',  1.000,   9.5000, 0, 'GENERAL', 21.00),
 (4, 4, 'PIEZA',        'Lampara faro H4 12V 60/55W',                    'LAM-H4',         1.000,  10.9000, 0, 'GENERAL', 21.00);


-- ---------------------------------------------------------------------
-- Desglose de IVA
-- ---------------------------------------------------------------------
-- Se calcula sumando las lineas, nunca recalculando sobre el total: asi el
-- desglose cuadra al centimo con las lineas y con la cabecera, que es lo que
-- comprueba el trigger diferido al hacer commit.
-- Solo las facturas que aun no lo tienen: las del historico ya se desglosaron
-- dentro de su propio bucle, y volver a insertarlas romperia la unicidad de
-- (factura, porcentaje).
INSERT INTO desglose_iva_factura (factura_id, tipo_iva, porcentaje_iva, base_imponible, cuota_iva)
SELECT lf.factura_id, lf.tipo_iva, lf.porcentaje_iva, SUM(lf.base_imponible), SUM(lf.cuota_iva)
  FROM linea_factura lf
 WHERE NOT EXISTS (SELECT 1 FROM desglose_iva_factura d WHERE d.factura_id = lf.factura_id)
 GROUP BY lf.factura_id, lf.tipo_iva, lf.porcentaje_iva;



-- ---------------------------------------------------------------------
-- Julio y agosto de 2026: el ejercicio en curso hasta hoy
--
-- Va DESPUES de las facturas de mayo y junio para que la posicion en el
-- registro siga el orden de emision. Continua la numeracion de la serie A/2026
-- donde la dejaron aquellas, en lugar de fijarla a mano.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    c_nif_emisor CONSTANT VARCHAR(20) := 'B87654323';
    c_tarifa     CONSTANT NUMERIC     := 45.00;

    -- Trabajos tipo del taller: descripcion, horas y las piezas que llevan.
    -- Se van rotando mes a mes para que la facturacion no salga plana.
    c_trabajos CONSTANT TEXT[] := ARRAY[
        'Revisión programada de mantenimiento',
        'Sustitución del kit de transmisión',
        'Cambio de pastillas y purga de frenos',
        'Sustitución de neumático y equilibrado',
        'Puesta a punto de carburación',
        'Cambio de aceite y filtros',
        'Revisión pre-ITV',
        'Sustitución de batería y revisión de carga'
    ];

    v_huella_previa VARCHAR(64);
    v_registro      BIGINT;
    v_numero        INTEGER := (SELECT ultimo_numero FROM serie_factura WHERE id = 1);
    v_factura_id    BIGINT  := 2000;
    v_orden_id      BIGINT  := 2000;
    v_linea_id      BIGINT  := 9000;

    v_mes           INTEGER;
    v_dia           INTEGER;
    v_factura_mes   INTEGER;
    v_por_mes       INTEGER;
    v_fecha         DATE;
    v_ts            TIMESTAMPTZ;
    v_entrada       TIMESTAMPTZ;

    v_cliente       BIGINT;
    v_moto          BIGINT;
    v_tecnico       BIGINT;
    v_horas         NUMERIC;
    v_trabajo       TEXT;

    v_base          NUMERIC(12,2);
    v_cuota         NUMERIC(12,2);
    v_total         NUMERIC(12,2);
    v_linea_base    NUMERIC(12,2);
    v_linea_cuota   NUMERIC(12,2);
    v_num_linea     INTEGER;

    v_cadena        TEXT;
    v_huella        VARCHAR(64);
    v_semilla       INTEGER;

    p               RECORD;
    v_piezas        BIGINT[];
    v_cantidades    NUMERIC[];
    i               INTEGER;
BEGIN
    -- Se arranca donde este el registro: si V900 ya metio facturas, esto
    -- continua su cadena en vez de abrir otra.
    SELECT COALESCE(MAX(numero_registro), 0) INTO v_registro FROM factura;
    SELECT COALESCE((SELECT huella FROM factura ORDER BY numero_registro DESC LIMIT 1),
                    repeat('0', 64))
      INTO v_huella_previa;

    FOR v_mes IN 7..8 LOOP
        -- Entre 3 y 6 facturas al mes, con mas trabajo en primavera y antes de
        -- vacaciones, que es como funciona un taller de motos.
        v_por_mes := 3 + ((v_mes * 7) % 4);

        FOR v_factura_mes IN 1..v_por_mes LOOP
            v_semilla := v_mes * 13 + v_factura_mes * 7;
            -- El dia crece con el numero de factura del mes: el registro de
            -- facturacion tiene que seguir el orden de emision, y con un dia al
            -- azar salian facturas posteriores con fecha anterior.
            v_dia     := 2 + (v_factura_mes - 1) * 4 + (v_semilla % 3);
            v_fecha   := make_date(2026, v_mes, v_dia);
            v_ts      := v_fecha + TIME '17:30:00';
            v_entrada := (v_fecha - ((v_semilla % 5) + 2)) + TIME '09:15:00';

            -- Solo clientes facturables: sin datos fiscales no se puede emitir
            -- una factura, y es justo lo que impide el NOT NULL de receptor_nif.
            -- (El cliente sin documento de la demo esta ahi a proposito, para
            -- que se vea la advertencia de «faltan datos» en su ficha.)
            SELECT c.id, m.id INTO v_cliente, v_moto
              FROM cliente c
              JOIN moto m ON m.cliente_id = c.id AND m.activo
             WHERE c.documento IS NOT NULL AND c.activo
             ORDER BY c.id, m.id
             OFFSET (v_semilla % (SELECT COUNT(*)
                                    FROM cliente c2
                                    JOIN moto m2 ON m2.cliente_id = c2.id AND m2.activo
                                   WHERE c2.documento IS NOT NULL AND c2.activo))
             LIMIT 1;

            v_tecnico := CASE WHEN v_semilla % 2 = 0 THEN 3 ELSE 4 END;
            v_horas   := 1.0 + ((v_semilla % 7) * 0.5);
            v_trabajo := c_trabajos[1 + (v_semilla % array_length(c_trabajos, 1))];

            -- ----- Orden de trabajo, ya entregada -----
            v_orden_id := v_orden_id + 1;
            UPDATE contador_ot SET ultimo_numero = ultimo_numero + 1 WHERE ejercicio = 2026;

            INSERT INTO orden_trabajo (
                id, ejercicio, numero, moto_id, cliente_id,
                fecha_entrada, fecha_estimada_salida, fecha_real_salida,
                km_entrada, problema_reportado, diagnostico, estado,
                tecnico_id, tarifa_hora, fecha_presupuesto, fecha_aprobacion, aprobado_por,
                created_by, updated_by)
            SELECT v_orden_id, 2026,
                   (SELECT ultimo_numero FROM contador_ot WHERE ejercicio = 2026),
                   v_moto, v_cliente,
                   v_entrada, v_fecha, NULL,
                   m.km_actual - (150 * (9 - v_mes)),
                   v_trabajo || '.',
                   'Trabajo realizado y comprobado en banco.',
                   -- Nace en reparacion: una orden ENTREGADA ya no admite
                   -- lineas nuevas, y todavia hay que ponerselas. Se entrega
                   -- mas abajo, igual que haria la aplicacion.
                   'EN_REPARACION',
                   v_tecnico, c_tarifa,
                   v_entrada + INTERVAL '3 hours', v_entrada + INTERVAL '6 hours',
                   TRIM(c.nombre || ' ' || COALESCE(c.apellidos, '')),
                   2, 2
              FROM moto m JOIN cliente c ON c.id = v_cliente
             WHERE m.id = v_moto;

            -- ----- Piezas del trabajo: entre una y tres -----
            v_piezas     := ARRAY[]::BIGINT[];
            v_cantidades := ARRAY[]::NUMERIC[];
            FOR i IN 0..(v_semilla % 3) LOOP
                v_piezas     := v_piezas || (1 + ((v_semilla + i * 5) % 18))::BIGINT;
                v_cantidades := v_cantidades || (1 + ((v_semilla + i) % 3))::NUMERIC;
            END LOOP;

            -- Se compra ANTES lo que se va a gastar: asi el almacen nunca queda
            -- en negativo y el stock final es el que dejo V900.
            FOR i IN 1..array_length(v_piezas, 1) LOOP
                INSERT INTO movimiento_stock (
                    pieza_id, tipo, cantidad, fecha, usuario_id, motivo,
                    documento_proveedor, precio_coste_unitario)
                SELECT v_piezas[i], 'ENTRADA', v_cantidades[i],
                       v_entrada - INTERVAL '2 days', 1,
                       'Reposición de almacén',
                       'ALB-2026-' || LPAD((v_mes * 100 + v_factura_mes)::text, 5, '0'),
                       pz.precio_coste
                  FROM pieza pz WHERE pz.id = v_piezas[i];
            END LOOP;

            -- ----- Lineas de la orden y consumo de almacen -----
            v_linea_id := v_linea_id + 1;
            INSERT INTO linea_ot (id, orden_trabajo_id, numero_linea, tipo, descripcion,
                                  pieza_id, cantidad, precio_unitario, descuento_pct,
                                  tipo_iva, porcentaje_iva, created_by)
            VALUES (v_linea_id, v_orden_id, 1, 'MANO_DE_OBRA', v_trabajo,
                    NULL, v_horas, c_tarifa, 0, 'GENERAL', 21.00, v_tecnico);

            v_num_linea := 1;
            FOR i IN 1..array_length(v_piezas, 1) LOOP
                v_num_linea := v_num_linea + 1;
                v_linea_id  := v_linea_id + 1;

                INSERT INTO linea_ot (id, orden_trabajo_id, numero_linea, tipo, descripcion,
                                      pieza_id, cantidad, precio_unitario, descuento_pct,
                                      tipo_iva, porcentaje_iva, created_by)
                SELECT v_linea_id, v_orden_id, v_num_linea, 'PIEZA', pz.descripcion,
                       pz.id, v_cantidades[i], pz.precio_venta, 0, pz.tipo_iva, t.porcentaje, v_tecnico
                  FROM pieza pz JOIN tipo_iva t ON t.codigo = pz.tipo_iva
                 WHERE pz.id = v_piezas[i];

                INSERT INTO movimiento_stock (
                    pieza_id, tipo, cantidad, fecha, usuario_id,
                    orden_trabajo_id, linea_ot_id, motivo, precio_coste_unitario)
                SELECT v_piezas[i], 'SALIDA', -v_cantidades[i],
                       v_entrada + INTERVAL '1 day', v_tecnico,
                       v_orden_id, v_linea_id, 'Consumo en orden de trabajo', pz.precio_coste
                  FROM pieza pz WHERE pz.id = v_piezas[i];
            END LOOP;

            -- Ya tiene todas sus lineas: ahora se puede entregar.
            UPDATE orden_trabajo
               SET estado = 'ENTREGADA', fecha_real_salida = v_ts, updated_by = 2
             WHERE id = v_orden_id;

            -- ----- Totales de la factura -----
            -- Con la MISMA formula que las columnas generadas de linea_factura:
            -- primero se redondea la base de cada linea, y sobre esa base
            -- redondeada se calcula la cuota. Hacerlo de otra forma daria un
            -- centimo de diferencia y el trigger de totales rechazaria la
            -- factura al hacer commit.
            v_base  := 0;
            v_cuota := 0;

            v_linea_base  := ROUND(v_horas * c_tarifa, 2);
            v_linea_cuota := ROUND(v_linea_base * 21.00 / 100, 2);
            v_base  := v_base + v_linea_base;
            v_cuota := v_cuota + v_linea_cuota;

            FOR i IN 1..array_length(v_piezas, 1) LOOP
                SELECT ROUND(v_cantidades[i] * pz.precio_venta, 2)
                  INTO v_linea_base
                  FROM pieza pz WHERE pz.id = v_piezas[i];
                SELECT ROUND(v_linea_base * t.porcentaje / 100, 2)
                  INTO v_linea_cuota
                  FROM pieza pz JOIN tipo_iva t ON t.codigo = pz.tipo_iva
                 WHERE pz.id = v_piezas[i];

                v_base  := v_base + v_linea_base;
                v_cuota := v_cuota + v_linea_cuota;
            END LOOP;

            v_total := v_base + v_cuota;

            -- ----- Huella encadenada -----
            v_numero     := v_numero + 1;
            v_registro   := v_registro + 1;
            v_factura_id := v_factura_id + 1;

            v_cadena := format(
                'NIFEmisor=%s&NumSerieFactura=%s&FechaExpedicion=%s&TipoFactura=%s&CuotaTotal=%s&ImporteTotal=%s&Huella=%s&FechaHoraHusoGenRegistro=%s',
                c_nif_emisor,
                'A/2026/' || LPAD(v_numero::text, 6, '0'),
                to_char(v_fecha, 'DD-MM-YYYY'),
                'ORDINARIA',
                to_char(v_cuota, 'FM9999999990.00'),
                to_char(v_total, 'FM9999999990.00'),
                v_huella_previa,
                to_char(v_ts AT TIME ZONE 'Europe/Madrid', 'YYYY-MM-DD"T"HH24:MI:SS') ||
                    to_char(v_ts, 'OF')
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
                   v_base, v_cuota, v_total,
                   v_registro, v_huella_previa, v_huella, v_cadena, 'SHA-256',
                   format('https://verifactu.motorsport19.example/verifica?nif=%s&numserie=%s&fecha=%s&importe=%s',
                          c_nif_emisor, 'A/2026/' || LPAD(v_numero::text, 6, '0'),
                          to_char(v_fecha, 'DD-MM-YYYY'), to_char(v_total, 'FM9999999990.00')),
                   cfg.software_nombre, cfg.software_version, cfg.software_nif, v_ts, 2
              FROM configuracion_taller cfg
              JOIN orden_trabajo o ON o.id = v_orden_id
              JOIN moto m          ON m.id = v_moto
              JOIN cliente c       ON c.id = v_cliente
             WHERE cfg.id = 1;

            UPDATE serie_factura SET ultimo_numero = v_numero WHERE id = 1;

            -- ----- Lineas de la factura (copiadas de la orden) -----
            INSERT INTO linea_factura (factura_id, numero_linea, tipo, descripcion, pieza_sku,
                                       cantidad, precio_unitario, descuento_pct, tipo_iva, porcentaje_iva)
            SELECT v_factura_id, l.numero_linea, l.tipo, l.descripcion, pz.sku,
                   l.cantidad, l.precio_unitario, l.descuento_pct, l.tipo_iva, l.porcentaje_iva
              FROM linea_ot l
         LEFT JOIN pieza pz ON pz.id = l.pieza_id
             WHERE l.orden_trabajo_id = v_orden_id
             ORDER BY l.numero_linea;

            -- ----- Desglose de IVA -----
            INSERT INTO desglose_iva_factura (factura_id, tipo_iva, porcentaje_iva, base_imponible, cuota_iva)
            SELECT v_factura_id, l.tipo_iva, l.porcentaje_iva,
                   SUM(l.base_imponible), SUM(l.cuota_iva)
              FROM linea_factura l
             WHERE l.factura_id = v_factura_id
             GROUP BY l.tipo_iva, l.porcentaje_iva;

            -- ----- Historial de estados de la orden -----
            INSERT INTO cambio_estado_ot (orden_trabajo_id, estado_anterior, estado_nuevo, fecha, usuario_id, motivo) VALUES
             (v_orden_id, NULL,               'RECIBIDA',       v_entrada,                        2, 'Entrada de la moto en el taller'),
             (v_orden_id, 'RECIBIDA',         'EN_DIAGNOSTICO', v_entrada + INTERVAL '2 hours',   v_tecnico, NULL),
             (v_orden_id, 'EN_DIAGNOSTICO',   'PRESUPUESTADA',  v_entrada + INTERVAL '3 hours',   v_tecnico, NULL),
             (v_orden_id, 'PRESUPUESTADA',    'APROBADA',       v_entrada + INTERVAL '6 hours',   2, 'Aprobado por el cliente'),
             (v_orden_id, 'APROBADA',         'EN_REPARACION',  v_entrada + INTERVAL '1 day',     v_tecnico, NULL),
             (v_orden_id, 'EN_REPARACION',    'LISTA',          v_ts - INTERVAL '2 hours',        v_tecnico, NULL),
             (v_orden_id, 'LISTA',            'ENTREGADA',      v_ts,                             2, 'Entregada y cobrada');

            INSERT INTO evento_factura (factura_id, tipo_evento, fecha, usuario_id, descripcion) VALUES
             (v_factura_id, 'EMISION', v_ts, 2, 'Emisión de la factura A/2026/' || LPAD(v_numero::text, 6, '0'));

            v_huella_previa := v_huella;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Verano 2026 generado: hasta la factura %, registro %', v_numero, v_registro;
END
$$;

-- ---------------------------------------------------------------------
-- Registro de eventos de facturacion
-- ---------------------------------------------------------------------
INSERT INTO evento_factura (factura_id, tipo_evento, fecha, usuario_id, descripcion, detalle) VALUES
 (1, 'EMISION',       TIMESTAMPTZ '2026-05-15 18:25:00+02', 2, 'Emision de la factura A/2026/000001 desde la OT-2026-00001', '{"origen":"OT-2026-00001","importe":244.68}'),
 (1, 'GENERACION_PDF',TIMESTAMPTZ '2026-05-15 18:26:00+02', 2, 'Generacion del PDF de la factura A/2026/000001', NULL),
 (2, 'EMISION',       TIMESTAMPTZ '2026-06-05 10:15:00+02', 2, 'Emision de la factura A/2026/000002 desde la OT-2026-00002', '{"origen":"OT-2026-00002","importe":674.94}'),
 (3, 'EMISION',       TIMESTAMPTZ '2026-06-23 17:50:00+02', 2, 'Emision de la factura A/2026/000003 desde la OT-2026-00003', '{"origen":"OT-2026-00003","importe":121.98}'),
 (4, 'RECTIFICACION', TIMESTAMPTZ '2026-06-30 12:00:00+02', 1, 'Emision de la rectificativa R/2026/000001 que sustituye a la A/2026/000003', '{"rectifica":"A/2026/000003","motivo":"horas de mano de obra"}'),
 (NULL, 'VERIFICACION_CADENA', TIMESTAMPTZ '2026-07-01 08:00:00+02', 1, 'Verificacion periodica de la cadena de huellas: sin anomalias', '{"facturas_verificadas":4,"anomalias":0}');


-- ---------------------------------------------------------------------
-- Sincronizacion de las secuencias de identidad
-- ---------------------------------------------------------------------
-- Se han insertado ids explicitos; hay que dejar los contadores de IDENTITY
-- por encima del maximo o los siguientes INSERT chocarian con la clave primaria.
SELECT setval(pg_get_serial_sequence('usuario',              'id'), COALESCE((SELECT MAX(id) FROM usuario), 1));
SELECT setval(pg_get_serial_sequence('proveedor',            'id'), COALESCE((SELECT MAX(id) FROM proveedor), 1));
SELECT setval(pg_get_serial_sequence('pieza',                'id'), COALESCE((SELECT MAX(id) FROM pieza), 1));
SELECT setval(pg_get_serial_sequence('cliente',              'id'), COALESCE((SELECT MAX(id) FROM cliente), 1));
SELECT setval(pg_get_serial_sequence('moto',                 'id'), COALESCE((SELECT MAX(id) FROM moto), 1));
SELECT setval(pg_get_serial_sequence('orden_trabajo',        'id'), COALESCE((SELECT MAX(id) FROM orden_trabajo), 1));
SELECT setval(pg_get_serial_sequence('linea_ot',             'id'), COALESCE((SELECT MAX(id) FROM linea_ot), 1));
SELECT setval(pg_get_serial_sequence('cambio_estado_ot',     'id'), COALESCE((SELECT MAX(id) FROM cambio_estado_ot), 1));
SELECT setval(pg_get_serial_sequence('movimiento_stock',     'id'), COALESCE((SELECT MAX(id) FROM movimiento_stock), 1));
SELECT setval(pg_get_serial_sequence('serie_factura',        'id'), COALESCE((SELECT MAX(id) FROM serie_factura), 1));
SELECT setval(pg_get_serial_sequence('factura',              'id'), COALESCE((SELECT MAX(id) FROM factura), 1));
SELECT setval(pg_get_serial_sequence('linea_factura',        'id'), COALESCE((SELECT MAX(id) FROM linea_factura), 1));
SELECT setval(pg_get_serial_sequence('desglose_iva_factura', 'id'), COALESCE((SELECT MAX(id) FROM desglose_iva_factura), 1));
SELECT setval(pg_get_serial_sequence('evento_factura',       'id'), COALESCE((SELECT MAX(id) FROM evento_factura), 1));
