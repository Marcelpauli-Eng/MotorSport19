import csv
import sys
import re

csv_file = '/Users/marcelpaulilara/Downloads/Mantenimiento de vehículos.csv'
sql_file = '/Users/marcelpaulilara/Documents/GitHub/MotorSport19/import_motos.sql'

def escape(val):
    if not val or str(val).strip() == '':
        return 'NULL'
    val = str(val).strip().replace("'", "''")
    return f"'{val}'"

with open(csv_file, 'r', encoding='utf-8-sig') as f, open(sql_file, 'w', encoding='utf-8') as out:
    reader = csv.reader(f, delimiter=';')
    
    headers = next(reader)
    out.write("BEGIN;\n")
    
    for row in reader:
        if len(row) < 4:
            continue
            
        moto_id = row[0]
        if not moto_id.isdigit():
            continue
            
        matricula = escape(row[1].upper().replace(" ", ""))
        denominacion = row[2].strip()
        
        # Heuristic for Marca vs Modelo
        parts = denominacion.split(' ', 1)
        marca = escape(parts[0].upper()) if len(parts) > 0 else 'NULL'
        modelo = escape(parts[1]) if len(parts) > 1 else escape(denominacion)
        
        cliente_str = escape(row[3].upper())
        bastidor = escape(row[4].upper()) if len(row) > 4 else 'NULL'
        color = escape(row[5]) if len(row) > 5 else 'NULL'
        
        # We need a subquery to find the cliente_id based on name + surname matching the CSV Cliente column
        cliente_subquery = f"(SELECT id FROM cliente WHERE trim(upper(concat_ws(' ', nombre, apellidos))) = {cliente_str} LIMIT 1)"
        
        sql = f"""
        DO $$
        DECLARE v_cliente_id BIGINT;
        BEGIN
            v_cliente_id := {cliente_subquery};
            IF v_cliente_id IS NOT NULL THEN
                INSERT INTO moto (
                    id, matricula, marca, modelo, color, numero_bastidor, cliente_id, activo, observaciones
                ) VALUES (
                    {moto_id}, {matricula}, {marca}, {modelo}, {color}, {bastidor}, v_cliente_id, true, 'Importada'
                ) ON CONFLICT (id) DO NOTHING;
            ELSE
                RAISE NOTICE 'Cliente no encontrado: %', {cliente_str};
            END IF;
        END $$;
        """
        out.write(sql)
        
    out.write("\n-- Update the sequence so new motos don't collide with imported IDs\n")
    out.write("SELECT setval('moto_id_seq', COALESCE((SELECT MAX(id) FROM moto), 1));\n")
    out.write("COMMIT;\n")

print(f"Generated {sql_file}")
