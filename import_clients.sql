BEGIN;

        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            0, 'GENERICO', NULL, 'NIF', 'XXXXXXXXX', 
            'DIRECCION DE PRUEBA', '00000', 'GENERICA', 'GENERICA', 'ESPAÑA', 
            NULL, '.', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            2, 'Alejandro', 'Jugo', 'NIF', '49992904N', 
            NULL, NULL, NULL, NULL, 'España', 
            'alejandro.jugo7@gmail.com', '662024808', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            3, 'JESUS MANUEL', 'CANSINO CARO', 'OTRO', '1', 
            NULL, NULL, NULL, NULL, 'España', 
            'jjsas16c@gmail.com', '626626287', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            4, 'Denis', 'Montero Torres', 'NIF', '38867791E', 
            NULL, NULL, NULL, NULL, 'España', 
            'denis.montor@gmail.com', '675523239', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            5, 'Roger', 'Suarez vacas', 'NIF', '48176279L', 
            NULL, NULL, NULL, NULL, 'España', 
            'rsuarez.sk8@gmail.com', NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            6, 'David', 'Palomino', 'NIF', '39412119D', 
            NULL, NULL, NULL, NULL, 'España', 
            'dpaa011206@gmail.com', '644556116', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            7, 'Marc', 'Meyer Meseguer', 'NIF', '47940061B', 
            NULL, NULL, NULL, NULL, 'España', 
            'marcmeyer14@gmail.com', '628 50 71 56', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            8, 'Ibai', 'Palomino Angel', 'NIF', '39969947H', 
            NULL, NULL, NULL, NULL, 'España', 
            'ibaijuega@gmail.com', '674727681', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            9, 'Fabrice Njoya', NULL, 'NIF', '14116620', 
            NULL, NULL, NULL, NULL, 'España', 
            'skeggirurik@gmail.com', '+33 667538567', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            10, 'Jordi', 'Mas', 'NIF', '00000000', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            11, 'Edgar', 'Carbonell Garcia', 'NIF', '77622887A', 
            NULL, NULL, NULL, NULL, 'España', 
            'edgarcg3@gmail.com', '616623428', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            12, 'Bruno', 'Gonzalez Cano', 'NIF', '54810316M', 
            NULL, NULL, NULL, NULL, 'España', 
            'brunogonzalezcano@gmail.com', '611068806', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            13, 'JAVIER', 'URREA PALACIOS', 'NIF', '39418255G', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            14, 'OSCARq', 'GARCIA ALONSO', 'NIF', '54459933V', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            15, 'ORIOL', 'TORRES CATALAN', 'NIF', '39404224A', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            16, 'IKEN', 'MUÑOZ', 'NIF', '49364698M', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            17, 'ZHUIR', 'MOHAMAD', 'NIF', '47719379Z', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            18, 'GUILLEM', 'MARTINEZ BERNAL', 'NIF', '45791598N', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            19, 'ADRIAN', 'BACH RIBERA', 'NIF', '45794507N', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            20, 'Albert', 'Coves Castro', 'NIF', '47182951S', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            21, 'MARIO', 'VITORIA FUENTES', 'NIF', '3941726G', 
            NULL, NULL, NULL, NULL, 'España', 
            'mariovitoriafuentes@gmail.com', '722 49 20 87', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            22, 'REGENERATION SOLUTION SL', NULL, 'NIF', 'B66883752', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            23, 'Samuel', 'BELLON', 'NIF', 'MFM686BF4', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '692 69 00 79', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            24, 'ALEX FERNANDEZ FERNANDEZ', NULL, 'NIF', '38880938j', 
            NULL, NULL, NULL, NULL, 'España', 
            'alex.99.ff@gmail.com', '635465735', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            25, 'XAVIER', 'CUADRADO SERRANO', 'NIF', '14267352S', 
            NULL, NULL, NULL, NULL, 'España', 
            'xavicuse@gmail.com', '608967032', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            26, 'MIGUEL ANGEL', 'CALLE HURTADO', 'NIF', '39425316G', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '607888104', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            27, 'Carles', 'BOIX GUARDIA', 'NIF', '38872563x', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '626272099', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            28, 'ERIC', 'TORRES', 'NIF', '46385950x', 
            NULL, NULL, NULL, NULL, 'España', 
            'rockeric2003@', '603 53 37 31', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            29, 'ALEX', 'COBO', 'NIF', '24416828M', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            30, 'FRANSISCO', 'SURIS JIMENEZ', 'NIF', '47942339n', 
            NULL, NULL, NULL, NULL, 'España', 
            'fsurisj846@gmail.com', '675 21 67 97', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            31, 'Ivan', 'Grimey', 'NIF', '77624263E', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '687 12 07 85', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            32, 'Joan', 'GORRIZ MUÑOZ', 'NIF', '48197082F', 
            NULL, NULL, NULL, NULL, 'España', 
            'joangorrizmunoz@hotmail.com', '633501008', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            33, 'Elliot', 'KASSIGIAN', 'NIF', 'JCV0XR4D1', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '06 28 35 48 82', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            34, 'Gerard', 'Carrera', 'NIF', '48169682T', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '638750505', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            35, 'FRANCESC', 'SULE ARIMANY', 'NIF', '47920918G', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '634436400', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            36, 'Monica', 'Sanchez Preciado', 'NIF', '46420421G', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '635909771', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            37, 'Emmanuelle', 'De Miguel Rousselot', 'NIF', '46354240V', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '670263795', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            38, 'Daniel', 'Polo', 'NIF', '32713183f', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '660347633', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            39, 'JORDI', 'PORTA PUJALDE', 'NIF', '43535812D', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '640098489', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            40, 'Bryan', 'Alcazar Arcal', 'NIF', '39404813V', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '635105425', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            41, 'Michael', 'Truchot', 'NIF', 'FR56988242061', 
            '756 Chemin de la Stele', '06530', NULL, NULL, 'España', 
            NULL, '0643171778', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            42, 'AACE', NULL, 'NIF', 'FR63830955076', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, NULL, true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            43, 'Francisco', 'Rodriguez Marti', 'NIF', '47332734E', 
            NULL, NULL, NULL, NULL, 'España', 
            'frm91e@gmail.com', '695841686', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            44, 'JULIUS', 'FRELLSEN', 'NIF', 'DK32578195', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '+4529636090', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            45, 'NICOLAS', 'COULOM', 'NIF', '16DZ02154', 
            NULL, NULL, NULL, NULL, 'España', 
            'NICOLASCOULOM20@GMAIL.COM', '+33783004476', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            46, 'Georgina', 'Portillo Ballesteros', 'NIF', '39966110e', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '693786167', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            47, 'CARLOS', NULL, 'NIF', 'B67304501', 
            NULL, NULL, NULL, NULL, 'España', 
            'ZASRACING@ZASRACING.ES', '93 669 52 93', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            48, 'Toni', 'Romero Coronado', 'NIF', '48171883Q', 
            NULL, NULL, NULL, NULL, 'España', 
            'toniromerocoronado1@gmail.com', '640583294', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            49, 'Ivan', 'PORRAS TORRES', 'NIF', '39966169N', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '644852257', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            50, 'Eloi', 'DOMINGUEZ CABRERA', 'NIF', '54037231L', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '654177355', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            51, 'Oriol', 'ARJONA', 'NIF', '49268537F', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '646621712', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            52, 'GUILLEM', 'CODINA DE HARO', 'NIF', '49297694T', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '609374096', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            53, 'SERGIO', 'MARCIAL ORTIZ', 'NIF', '44993210N', 
            NULL, NULL, NULL, NULL, 'España', 
            NULL, '615670227', true, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        
-- Update the sequence so new clients don't collide with imported IDs
SELECT setval('cliente_id_seq', COALESCE((SELECT MAX(id) FROM cliente), 1));
COMMIT;
