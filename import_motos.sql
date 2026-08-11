BEGIN;

        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'GENERICO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    1, '0000000', 'GENERICO', 'GENERICO', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'GENERICO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ALEX FERNANDEZ FERNANDEZ' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    16, '0000MMM', 'APRILIA', 'RS660', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ALEX FERNANDEZ FERNANDEZ';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ALEX FERNANDEZ FERNANDEZ' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    14, '0419LTH', 'RS660', 'RS660', NULL, 'ZD4KVH001MS001617', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ALEX FERNANDEZ FERNANDEZ';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'FRANCESC SULE ARIMANY' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    27, '0768LKD', 'SUZUKI', 'GSX-R (751cc - ) GSX-R 1000 R (WDM0)', NULL, 'JS1DM11GZL7100134', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'FRANCESC SULE ARIMANY';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'BRUNO  GONZALEZ CANO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    11, '1087MLN', 'YAMAHA', 'Yzf R7', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'BRUNO  GONZALEZ CANO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'EDGAR CARBONELL GARCIA' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    10, '1111XXX', 'YAMAHA', 'YZF R6R 2006', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'EDGAR CARBONELL GARCIA';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ALBERT  COVES CASTRO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    45, '1280ACC', 'YZF', 'R6', NULL, 'JYARJ151000005690', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ALBERT  COVES CASTRO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'BRYAN ALCAZAR ARCAL' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    32, '1301FPF', 'SUZUKI', 'GSX-R (124cc - 750cc) GSX-R 750 /K7 (WVCF)', NULL, 'JS1CF111100108950', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'BRYAN ALCAZAR ARCAL';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'GUILLEM CODINA DE HARO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    46, '1627MKT', 'KAWASAKI', 'Z900', NULL, 'JKAZR900PPA007024', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'GUILLEM CODINA DE HARO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'BELLON SAMUEL' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    15, '1919RMT', 'YAMAHA', 'YZF R6', NULL, 'RACING19MOTORSPORT', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'BELLON SAMUEL';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'CARLES BOIX GUARDIA' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    19, '1947GDT', 'SUZUKI', 'GSX-R 1000 2008', NULL, 'JS1CL111100114503', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'CARLES BOIX GUARDIA';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ASSOCIATION TEAM MXGP 81' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    37, '2020MLN', 'YAMAHA', 'R6 RACE', NULL, 'JYACJ33C000000322', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ASSOCIATION TEAM MXGP 81';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'MONICA SANCHEZ PRECIADO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    28, '2592GMV', 'SUZUKI', 'GSX-R (124cc - 750cc) GSX-R 600 /K8/K9/L0 (WVCV)', NULL, 'JS1CV111100108420', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'MONICA SANCHEZ PRECIADO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'DAVID  PALOMINO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    6, '2618KPR', 'TRIUMPH', 'STREET TRIPLE 660 S', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'DAVID  PALOMINO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'IBAI PALOMINO ANGEL' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    44, '2727PRT', 'YAMAHA', 'YZF R6', NULL, 'JYARJ111000019741', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'IBAI PALOMINO ANGEL';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'MARC  MEYER MESEGUER' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    7, '2787KSV', 'DUCATI', 'PANIGALE V4 S', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'MARC  MEYER MESEGUER';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'MT RACING' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    33, '2929RYT', 'HONDA', 'CBR 600 RR', NULL, 'JH2PC4000CK500014', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'MT RACING';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'IBAI PALOMINO ANGEL' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    8, '3602LPK', 'HONDA', 'CB125R', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'IBAI PALOMINO ANGEL';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'EMMANUELLE MIGUEL ROUSSELOT' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    29, '3897JHX', 'HONDA', 'NSS NSS 300 Forza (NF04)', NULL, 'MLHNF04B6D5008397', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'EMMANUELLE MIGUEL ROUSSELOT';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'JESUS MANUEL CANSINO CARO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    3, '4086KJK', 'KAWASAKI', 'ZX10R', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'JESUS MANUEL CANSINO CARO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ROGER SUAREZ VACAS' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    5, '4116DHJ', 'HPNDA', 'CBR 600 RR', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ROGER SUAREZ VACAS';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'DP42' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    36, '4242JHR', '450', 'FE', NULL, 'VBKUSR437MM332227', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'DP42';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'IVAN PORRAS TORRES' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    41, '4401FPM', 'HONDA', 'CBR ( - 600cc) CBR 600 RR (PC40)', NULL, 'JH2PC40A37M001787', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'IVAN PORRAS TORRES';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'FRANSISCO  SURIS JIMENEZ' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    22, '4862LRW', 'APRILIA', 'RS660', NULL, 'ZD4KVH000MS001382', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'FRANSISCO  SURIS JIMENEZ';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'DANIEL POLO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    30, '5157HXL', 'KTM', 'KTM 1290 SUPERDUKE R', NULL, 'VBKV39401EM911142', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'DANIEL POLO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'DENIS MONTERO TORRES' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    4, '5287JDG', 'YAMAHA', 'MT07', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'DENIS MONTERO TORRES';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'MIGUEL ANGEL CALLE HURTADO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    18, '5900HNN', 'YAMAHA', 'YZF R1', NULL, 'JYARN225000002459', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'MIGUEL ANGEL CALLE HURTADO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'SERGIO MARCIAL ORTIZ' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    47, '5977NFT', 'DUCATI', 'STREETFIGHTER V2 S RED', NULL, 'ZDM3000ESSB001008', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'SERGIO MARCIAL ORTIZ';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'JOAN  GORRIZ MUÑOZ' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    24, '6158FSG', 'SUZUKI', 'GSXR 750', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'JOAN  GORRIZ MUÑOZ';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ZAS RACING COMPOSITES S.L' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    39, '6730ZAS', 'APRILIA', 'RSV4 2026', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ZAS RACING COMPOSITES S.L';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'JORDI MAS' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    9, '7044JRS', 'DUCATI', 'HYPERMOTARD 959 SP', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'JORDI MAS';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ORIOL ARJONA' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    43, '7389LGY', 'TRIUMP', 'STREET TRIPLE 660 2019', NULL, 'SMTHDA47AZL966379', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ORIOL ARJONA';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'FRANCISCO  RODRIGUEZ MARTI' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    35, '7548GWV', 'SUZUKI', 'GSX-R (124cc - 750cc) GSX-R 600 /K8/K9/L0 (WVCV)', NULL, 'JS1CV111100109406', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'FRANCISCO  RODRIGUEZ MARTI';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'GEORGINA PORTILLO BALLESTEROS' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    38, '7674MKB', 'YAMAHA', 'YZF-R YZF- R7 (RM40, RM61)', NULL, 'JYARM391000008457', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'GEORGINA PORTILLO BALLESTEROS';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'JORDI PORTA PUJALDE' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    31, '7774JPV', 'YAMAHA', 'MT MT-09 Tracer ABS (RN43)', NULL, 'JYARN29F000020088', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'JORDI PORTA PUJALDE';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ELOI DOMINGUEZ CABRERA' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    42, '8194MFR', 'BMW', 'S1000R', NULL, 'WB10E5102P6688734', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ELOI DOMINGUEZ CABRERA';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'IVAN GRIMEY' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    23, '8215KJC', 'BMW', 'S1000RR', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'IVAN GRIMEY';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'TONI ROMERO CORONADO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    40, '8486LXF', 'ZONTES', '125 U', NULL, 'LD3PDJ6A1M1612857', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'TONI ROMERO CORONADO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ERIC TORRES' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    20, '9152FXW', 'YAMAHA', 'R1', NULL, 'JYARN191000016449', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ERIC TORRES';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'XAVIER CUADRADO SERRANO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    17, '9205KRV', 'APRILIA', 'RSV4', NULL, 'ZD4KE0003JS002927', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'XAVIER CUADRADO SERRANO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ALEX COBO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    21, '9393LSV', 'STREET', 'TRIPLE 765 RS', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ALEX COBO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'REGENERATION SOLUTION SL' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    12, '9505LLM', 'BEVERLY', '350', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'REGENERATION SOLUTION SL';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ALEJANDRO JUGO' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    2, '9594JZV', 'YAMAHA', 'XSR 700', NULL, 'VG5RM111000009670', v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ALEJANDRO JUGO';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'AACE' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    34, 'AACE', 'AUTOBUS', 'AUTOBUS', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'AACE';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'BELLON SAMUEL' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    13, 'MOTO3', 'BEON', 'MOTO3 CUP', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'BELLON SAMUEL';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'GERARD CARRERA' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    25, 'XXXXJTN', 'TRIUMPH', 'STREET TRIPLE 765 RS', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'GERARD CARRERA';
            END IF;
        END $$;
        
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := (SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = 'ELLIOT KASSIGIAN' LIMIT 1);
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    26, 'XXXXMTS', 'HONDA', 'CRF 250 2005', NULL, NULL, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', 'ELLIOT KASSIGIAN';
            END IF;
        END $$;
        
-- Update the sequence so new motos don't collide with imported IDs
SELECT setval('moto_id_seq', COALESCE((SELECT MAX(id) FROM moto), 1));
COMMIT;
