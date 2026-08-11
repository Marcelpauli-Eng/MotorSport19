import csv
import sys

csv_file = '/Users/marcelpaulilara/Downloads/Registro-2026-08-09.csv'
sql_file = '/Users/marcelpaulilara/Documents/GitHub/MotorSport19/import_clients.sql'

def escape(val):
    if not val or val.strip() == '':
        return 'NULL'
    val = val.strip().replace("'", "''")
    return f"'{val}'"

with open(csv_file, 'r', encoding='utf-8-sig') as f, open(sql_file, 'w', encoding='utf-8') as out:
    reader = csv.DictReader(f, delimiter=';')
    out.write("BEGIN;\n")
    
    for row in reader:
        try:
            client_id = int(row.get('CODIGO', 0))
        except ValueError:
            continue
            
        nombre = escape(row.get('NOMBRE', ''))
        if nombre == 'NULL':
            razon = row.get('RAZON_FISCAL', '').strip()
            nombre = escape(razon) if razon else "'Desconocido'"
            
        apellidos = escape(row.get('APELLIDO', ''))
        cif = escape(row.get('CIF', ''))
        tipo_doc = 'NULL'
        if cif != 'NULL':
            raw_cif = cif.strip("'").replace(" ", "").upper()
            if len(raw_cif) >= 8:
                tipo_doc = "'NIF'"
            else:
                tipo_doc = "'OTRO'"
                
        direccion = escape(row.get('DIRECCION', ''))
        cp = escape(row.get('CODIGO_POSTAL', ''))
        poblacion = escape(row.get('POBLACION', ''))
        provincia = escape(row.get('PROVINCIA', ''))
        
        pais = escape(row.get('PAIS', 'España'))
        if pais == 'NULL': pais = "'España'"
            
        telefono = escape(row.get('TELEFONO', ''))
        email = escape(row.get('EMAIL', ''))
        
        baja = row.get('DADO_BAJA', '').strip().upper()
        activo = 'true' if baja != 'SI' else 'false'
        
        sql = f"""
        INSERT INTO cliente (
            id, nombre, apellidos, tipo_documento, documento, 
            direccion, codigo_postal, ciudad, provincia, pais, 
            email, telefono, activo, observaciones
        ) VALUES (
            {client_id}, {nombre}, {apellidos}, {tipo_doc}, {cif}, 
            {direccion}, {cp}, {poblacion}, {provincia}, {pais}, 
            {email}, {telefono}, {activo}, 'Importado desde CSV antiguo'
        ) ON CONFLICT (id) DO NOTHING;
        """
        out.write(sql)
        
    out.write("\n-- Update the sequence so new clients don't collide with imported IDs\n")
    out.write("SELECT setval('cliente_id_seq', COALESCE((SELECT MAX(id) FROM cliente), 1));\n")
    out.write("COMMIT;\n")

print(f"Generated {sql_file}")
