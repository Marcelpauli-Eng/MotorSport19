#!/usr/bin/env python3
"""
Carga en MotorSport19 las facturas emitidas con NEXTGO, el programa anterior.

No las emite de nuevo: van al archivo de solo consulta (tabla factura_anterior,
migracion V23), fuera de la numeracion, la cadena de huellas y los libros de
este programa. Cada una se guarda con su PDF original y sus lineas, y se ve en
la ficha del cliente, en la de la moto y en el historial de la moto.

Lee tres ficheros exportados de NEXTGO y escribe un SQL por la salida estandar:

  --pdf        Administracion -> Ventas -> Facturas, todas impresas en un PDF
  --registro   Administracion -> Tributos -> Registro facturacion (CSV)
  --vehiculos  Mantenimiento de vehiculos (CSV), para saber de que moto es cada
               factura: la factura de NEXTGO no lo dice, asi que se deduce del
               cliente cuando solo tiene una moto. Las demas, con --moto.

Uso, en el Mac donde estan los ficheros (hace falta pdftotext, de poppler, y
la libreria pypdf: brew install poppler && pip3 install pypdf):

    python3 import_facturas_nextgo.py \\
        --pdf ~/Downloads/"Facturas MotorSport.pdf" \\
        --registro ~/Downloads/"Registro facturación.csv" \\
        --vehiculos ~/Downloads/"Mantenimiento de vehículos-2.csv" \\
        --moto 17=1234BCD --moto 26=5678FGH \\
        --completar-direcciones \\
        > ~/Downloads/facturas_nextgo.sql

El SQL lleva datos personales: se escribe fuera del repositorio y no se sube.
Antes de aplicarlo tienen que estar ya importados los clientes y las motos.

Se aplica en la base del servidor (el SQL es una sola transaccion: o entra
entero o no entra nada):

    docker compose -f docker-compose.taller.yml exec -T db \\
        psql -v ON_ERROR_STOP=1 -U taller -d motorsport19 < facturas_nextgo.sql

Se puede volver a aplicar: no duplica facturas ni lineas, y actualiza la moto
de cada una. Asi se corrige una asignacion: se repite con --moto y se aplica.

Antes de escribir nada comprueba que el PDF y el registro cuentan lo mismo:
las mismas facturas, con el mismo total, y las lineas de cada una sumando su
base imponible. Si algo no cuadra, se para y dice que.
"""

import argparse
import csv
import io
import logging
import re
import subprocess
import sys
import unicodedata
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime
from decimal import Decimal

from pypdf import PdfReader, PdfWriter

ORIGEN = "NEXTGO"

# Copia de backend/.../common/util/Provincias.java: los dos primeros digitos de
# un codigo postal espanol son la provincia.
PROVINCIAS = {
    "01": "Araba/Álava", "02": "Albacete", "03": "Alicante/Alacant", "04": "Almería", "05": "Ávila",
    "06": "Badajoz", "07": "Illes Balears", "08": "Barcelona", "09": "Burgos", "10": "Cáceres",
    "11": "Cádiz", "12": "Castellón/Castelló", "13": "Ciudad Real", "14": "Córdoba", "15": "A Coruña",
    "16": "Cuenca", "17": "Girona", "18": "Granada", "19": "Guadalajara", "20": "Gipuzkoa",
    "21": "Huelva", "22": "Huesca", "23": "Jaén", "24": "León", "25": "Lleida", "26": "La Rioja",
    "27": "Lugo", "28": "Madrid", "29": "Málaga", "30": "Murcia", "31": "Navarra", "32": "Ourense",
    "33": "Asturias", "34": "Palencia", "35": "Las Palmas", "36": "Pontevedra", "37": "Salamanca",
    "38": "Santa Cruz de Tenerife", "39": "Cantabria", "40": "Segovia", "41": "Sevilla", "42": "Soria",
    "43": "Tarragona", "44": "Teruel", "45": "Toledo", "46": "Valencia/València", "47": "Valladolid",
    "48": "Bizkaia", "49": "Zamora", "50": "Zaragoza", "51": "Ceuta", "52": "Melilla",
}

DOCUMENTO = re.compile(r"\b[A-Z]{2,5}\|[A-Z]{2,5}\|\d{6,}\b")
CABECERA_LINEAS = re.compile(r"Código\s+Descripción\s+Cantidad")
FIN_LINEAS = re.compile(r"^\s*IMPORTE\s+DTO\. LINEA")
# «SERVICIOS EXTERNOS      1,000    394,37€    0,00%    394,370», con € al final en los presupuestos
NUMEROS = re.compile(
    r"^(?:(?P<descripcion>.*?)\s{2,})?"
    r"(?P<cantidad>-?[\d.]*\d,\d{3})\s+"
    r"(?P<precio>-?[\d.]*\d,\d{2})€"
    r"(?:\s+(?P<descuento>[\d.]*\d,\d{2})%)?"
    r"\s+(?P<importe>-?[\d.]*\d,\d{3})€?\s*$")
# «MANO DE OBRA        394,370»: el rotulo de un bloque con su subtotal.
SECCION = re.compile(r"^(?P<nombre>[^\d\s][^\d]*?)\s{2,}(?P<subtotal>-?[\d.]*\d,\d{3})€?\s*$")
TOTAL_FACTURA = re.compile(r"TOTAL FACTURA:\s*([\d.,]+)€")


@dataclass
class Linea:
    tipo: str
    codigo: str | None
    descripcion: str
    cantidad: Decimal
    precio: Decimal
    descuento: Decimal
    importe: Decimal


@dataclass
class Factura:
    documento: str
    fecha: date
    nif: str
    razon: str
    base: Decimal
    iva: Decimal
    total: Decimal
    paginas: list[int] = field(default_factory=list)
    lineas: list[Linea] = field(default_factory=list)
    subtotales: list[Decimal] = field(default_factory=list)
    total_pdf: Decimal | None = None
    matricula: str | None = None
    direccion: tuple[str, str, str, str] | None = None

    @property
    def corto(self) -> int:
        """El numero que se ve en el listado: 17 para ORD|FAC|202600000000017."""
        return int(self.documento.rsplit("|", 1)[-1][-6:])


def importe(texto: str) -> Decimal:
    """«2.525,00€», «2525,000» y «0,00%» a la manera espanola."""
    return Decimal(texto.replace("€", "").replace("%", "").replace(".", "").replace(",", ".").strip())


def clave(texto: str) -> str:
    """Para comparar nombres: mayusculas, sin tildes y sin espacios de mas."""
    sin_tildes = unicodedata.normalize("NFD", texto or "").encode("ascii", "ignore").decode()
    return " ".join(sin_tildes.upper().split())


def normalizar_nif(nif: str) -> str:
    return re.sub(r"[\s.\-]", "", nif or "").upper()


def documento_espanol_valido(nif: str) -> bool:
    """Copia de ValidadorDocumento: NIF, NIE o CIF con su digito de control."""
    d = normalizar_nif(nif)
    letras = "TRWAGMYFPDXBNJZSQVHLCKE"
    if re.fullmatch(r"\d{8}[A-Z]", d):
        return d[8] == letras[int(d[:8]) % 23]
    if re.fullmatch(r"[XYZ]\d{7}[A-Z]", d):
        return d[8] == letras[int(str("XYZ".index(d[0])) + d[1:8]) % 23]
    if re.fullmatch(r"[ABCDEFGHJNPQRSUVW]\d{7}[\dA-J]", d):
        suma = 0
        for i, c in enumerate(d[1:8]):
            n = int(c)
            suma += (n * 2) // 10 + (n * 2) % 10 if i % 2 == 0 else n
        control = (10 - suma % 10) % 10
        if d[0] in "KPQRSNW":
            return d[8] == "JABCDEFGHI"[control]
        if d[0] in "ABEH":
            return d[8] == str(control)
        return d[8] in (str(control), "JABCDEFGHI"[control])
    return False


# ----------------------------------------------------------------- lectura

def leer_registro(ruta: str) -> dict[str, Factura]:
    with open(ruta, encoding="utf-8-sig", newline="") as f:
        return {
            r["Documento"].strip(): Factura(
                documento=r["Documento"].strip(),
                fecha=datetime.strptime(r["Fecha"].split()[0], "%d/%m/%Y").date(),
                nif=r["CIF"].strip(),
                razon=" ".join(r["Razón"].split()),
                base=importe(r["Base imponible"]),
                iva=importe(r["Impuestos"]),
                total=importe(r["Total"]),
            )
            for r in csv.DictReader(f, delimiter=";")
        }


def leer_pdf(ruta: str, facturas: dict[str, Factura], avisos: list[str]) -> None:
    """Reparte las paginas entre las facturas y saca de cada una lineas, total y direccion."""
    # pdftotext conserva las columnas y no parte los numeros («11,15€»), cosa
    # que pypdf a veces si hace; pypdf queda solo para separar el PDF.
    texto = subprocess.run(["pdftotext", "-layout", ruta, "-"], capture_output=True, text=True,
                           check=True).stdout
    for numero_pagina, pagina in enumerate(texto.split("\f")):
        documento = DOCUMENTO.search(pagina)
        if not documento:
            continue                     # las paginas en blanco que mete la impresion
        factura = facturas.get(documento.group(0))
        if factura is None:
            raise SystemExit(f"La factura {documento.group(0)} esta en el PDF pero no en el registro.")
        if not factura.paginas:
            factura.direccion = leer_direccion(pagina, factura)
        factura.paginas.append(numero_pagina)
        leer_lineas(pagina, factura, avisos)
        total = TOTAL_FACTURA.search(pagina)
        if total:
            factura.total_pdf = importe(total.group(1))


def leer_lineas(pagina: str, factura: Factura, avisos: list[str]) -> None:
    """
    Las lineas de una pagina. Una descripcion larga ocupa varios renglones, y
    NEXTGO pone los numeros a veces en el primero («MUELLES OHLINS» / «10» /
    «9.5») y a veces en medio, con el renglon de los numeros sin texto («KIT
    SERVICE SHOWA...» / numeros / «FMAN04701WO»). Por eso los renglones sueltos
    esperan: si el siguiente con numeros no trae texto, son el principio de su
    descripcion; si no, eran la continuacion de la linea de antes.
    """
    dentro = False
    tipo = factura.lineas[-1].tipo if factura.lineas else "PIEZA"
    sueltos: list[str] = []

    def a_la_anterior():
        if sueltos and factura.lineas:
            factura.lineas[-1].descripcion += " " + " ".join(sueltos)
        elif sueltos:
            avisos.append(f"{factura.corto}: texto sin linea a la que pegarlo: «{' '.join(sueltos)}»")
        sueltos.clear()

    for bruta in pagina.splitlines():
        if CABECERA_LINEAS.search(bruta):
            dentro = True
            continue
        if not dentro or not bruta.strip():
            continue
        if FIN_LINEAS.search(bruta):
            break
        # El codigo va pegado al margen; la descripcion y los rotulos, sangrados.
        codigo, resto = (bruta.split(None, 1) + [""])[:2] if not bruta[0].isspace() else (None, bruta.strip())
        numeros = NUMEROS.match(resto)
        seccion = SECCION.match(resto) if codigo is None else None
        if numeros:
            descripcion = " ".join((numeros["descripcion"] or "").split())
            if descripcion:
                a_la_anterior()
            else:
                descripcion, sueltos[:] = " ".join(sueltos), []
            factura.lineas.append(Linea(
                tipo=tipo, codigo=codigo, descripcion=descripcion or (codigo or "(sin descripción)"),
                cantidad=importe(numeros["cantidad"]), precio=importe(numeros["precio"]),
                descuento=importe(numeros["descuento"] or "0,00"), importe=importe(numeros["importe"])))
        elif seccion:
            a_la_anterior()
            tipo = "MANO_DE_OBRA" if clave(seccion["nombre"]) == "MANO DE OBRA" else "PIEZA"
            factura.subtotales.append(importe(seccion["subtotal"]))
        elif codigo is None:
            sueltos.append(" ".join(resto.split()))
        else:
            avisos.append(f"{factura.corto}: no entiendo el renglon «{bruta.strip()}»")
    a_la_anterior()


def leer_direccion(pagina: str, factura: Factura) -> tuple[str, str, str, str] | None:
    """
    Calle, codigo postal, ciudad y provincia del bloque del cliente, arriba a la
    derecha. Solo si es una direccion espanola completa: sin calle o sin codigo
    postal no sirve para facturar, y un codigo postal extranjero no dice la
    provincia (el 06530 es de Badajoz y de un pueblo frances).
    """
    renglones = pagina.splitlines()
    nombre = re.compile(r"\s+".join(map(re.escape, factura.razon.upper().split())))
    for i, renglon in enumerate(renglones):
        encontrado = nombre.search(renglon.upper())
        if encontrado:
            columna = encontrado.start()
            break
    else:
        return None

    partes = []
    for renglon in renglones[i + 1:i + 6]:
        parte = " ".join(renglon[columna:].split()) if len(renglon) > columna else ""
        if not parte:
            continue
        cp = re.match(r"^(\d{5})\s+(.+)$", parte)
        if cp:
            provincia = PROVINCIAS.get(cp[1][:2])
            if not partes or provincia is None:
                return None
            return ", ".join(partes), cp[1], separar_ciudad(cp[2], provincia), provincia
        partes.append(parte)
    return None


def separar_ciudad(resto: str, provincia: str) -> str:
    """«MATARÓ BARCELONA» -> «MATARÓ»: la provincia va al final, si va."""
    palabras = resto.split()
    for variante in provincia.split("/") + [provincia]:
        n = len(variante.split())
        if len(palabras) > n and clave(" ".join(palabras[-n:])) == clave(variante):
            return " ".join(palabras[:-n])
    return resto


def leer_vehiculos(ruta: str) -> dict[str, list[str]]:
    """Matriculas de cada cliente, por su nombre tal y como sale en el fichero."""
    with open(ruta, encoding="utf-8-sig", newline="") as f:
        filas = list(csv.reader(f, delimiter=";"))
    columnas = [clave(c) for c in filas[0]]
    col_matricula, col_cliente = columnas.index("MATRICULA"), columnas.index("CLIENTE")
    motos = defaultdict(list)
    for fila in filas[1:]:
        if len(fila) > max(col_matricula, col_cliente) and fila[col_matricula].strip():
            motos[clave(fila[col_cliente])].append(fila[col_matricula].replace(" ", "").upper())
    return motos


# ------------------------------------------------------------- comprobacion

def comprobar(facturas: dict[str, Factura]) -> None:
    errores = []
    for f in facturas.values():
        if not f.paginas:
            errores.append(f"{f.corto} ({f.documento}): esta en el registro pero no en el PDF")
            continue
        if f.total_pdf != f.total:
            errores.append(f"{f.corto}: el PDF dice {f.total_pdf} € y el registro {f.total} €")
        # Cada bloque (mano de obra, articulos) lleva su subtotal impreso: si las
        # lineas leidas no lo suman, se ha perdido o partido alguna. Se compara
        # con eso y no con la base, que ademas descuenta el descuento general.
        # Se tolera menos de medio centimo: cada linea se imprime redondeada a
        # tres decimales y el subtotal se suma sin redondear (117,8255 -> 117,825).
        suma = sum((l.importe for l in f.lineas), Decimal(0))
        subtotales = sum(f.subtotales, Decimal(0))
        if not f.lineas or abs(suma - subtotales) >= Decimal("0.005"):
            errores.append(f"{f.corto}: sus lineas suman {suma} € y sus bloques dicen {subtotales} €")
    if errores:
        raise SystemExit("No cuadra, no se genera nada:\n  " + "\n  ".join(errores))


# --------------------------------------------------------------------- SQL

def texto(valor) -> str:
    return "NULL" if valor is None else "'" + str(valor).replace("'", "''") + "'"


def pdf_de(lector: PdfReader, paginas: list[int]) -> bytes:
    escritor = PdfWriter()
    for p in paginas:
        escritor.add_page(lector.pages[p])
    salida = io.BytesIO()
    escritor.write(salida)
    return salida.getvalue()


FUNCIONES = r"""
-- Se buscan en la base, no por identificador: los del programa anterior no
-- coinciden con los de aqui. Funciones temporales: desaparecen al terminar.
CREATE FUNCTION pg_temp.cliente_de(p_nif TEXT, p_nombre TEXT) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE
    v_id      BIGINT;
    v_cuantos INT;
BEGIN
    -- Por el NIF primero, que no se escribe de dos maneras.
    SELECT id INTO v_id FROM cliente
     WHERE upper(regexp_replace(documento, '[\s.\-]', '', 'g')) = p_nif
     LIMIT 1;
    IF v_id IS NOT NULL THEN
        RETURN v_id;
    END IF;
    -- Si no, por el nombre de la ficha: el NIF que no valia se quedo en observaciones.
    SELECT count(*), min(id) INTO v_cuantos, v_id FROM cliente
     WHERE upper(regexp_replace(trim(concat(nombre, ' ', coalesce(apellidos, ''))), '\s+', ' ', 'g'))
           = upper(p_nombre);
    IF v_cuantos = 1 THEN
        RETURN v_id;
    END IF;
    RAISE EXCEPTION 'No encuentro al cliente % (NIF %): hay % con ese nombre. Importe antes los clientes.',
        p_nombre, p_nif, v_cuantos;
END
$$;

CREATE FUNCTION pg_temp.moto_de(p_matricula TEXT) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE
    v_id BIGINT;
BEGIN
    IF p_matricula IS NULL THEN
        RETURN NULL;
    END IF;
    SELECT id INTO v_id FROM moto WHERE upper(replace(matricula, ' ', '')) = p_matricula;
    IF v_id IS NULL THEN
        RAISE NOTICE 'La moto % no esta dada de alta: su factura queda solo en la ficha del cliente.', p_matricula;
    END IF;
    RETURN v_id;
END
$$;
"""


def sql(facturas: list[Factura], lector: PdfReader, completar_direcciones: bool) -> str:
    salida = [f"-- Facturas de {ORIGEN} para MotorSport19, generado por import_facturas_nextgo.py.",
              "-- Lleva datos personales: no se sube al repositorio.",
              "BEGIN;", FUNCIONES]
    for f in facturas:
        cliente = f"pg_temp.cliente_de({texto(normalizar_nif(f.nif))}, {texto(f.razon)})"
        lineas = ",\n       ".join(
            f"({n}, {texto(l.tipo)}, {texto(l.codigo)}, {texto(l.descripcion)}, {l.cantidad}, {l.precio}, "
            f"{l.descuento}, {l.importe})"
            for n, l in enumerate(f.lineas, start=1))
        insertar_lineas = f"""
INSERT INTO linea_factura_anterior (factura_anterior_id, numero_linea, tipo, codigo, descripcion, cantidad,
                                    precio_unitario, descuento_pct, importe)
SELECT factura.id, l.* FROM factura, (VALUES
       {lineas}) AS l
ON CONFLICT (factura_anterior_id, numero_linea) DO NOTHING""" if f.lineas else "\nSELECT id FROM factura"
        salida.append(f"""
-- {f.corto} · {f.fecha:%d/%m/%Y} · {f.razon}
WITH factura AS (
    INSERT INTO factura_anterior (origen, numero, fecha, cliente_id, moto_id, receptor_nombre, receptor_nif,
                                  base_imponible, total_iva, total, pdf)
    VALUES ({texto(ORIGEN)}, {texto(f.documento)}, DATE '{f.fecha.isoformat()}', {cliente},
            pg_temp.moto_de({texto(f.matricula)}), {texto(f.razon)}, {texto(f.nif)},
            {f.base}, {f.iva}, {f.total}, decode('{pdf_de(lector, f.paginas).hex()}', 'hex'))
    ON CONFLICT (origen, numero)
        DO UPDATE SET moto_id = COALESCE(EXCLUDED.moto_id, factura_anterior.moto_id)
    RETURNING id
){insertar_lineas};""")

    if completar_direcciones:
        salida.append("\n-- Direcciones de las facturas, solo para quien no tiene ninguna en su ficha.")
        for f in direcciones_a_completar(facturas):
            calle, cp, ciudad, provincia = f.direccion
            salida.append(
                f"UPDATE cliente SET direccion = {texto(calle)}, codigo_postal = {texto(cp)}, "
                f"ciudad = {texto(ciudad)}, provincia = {texto(provincia)}, version = version + 1\n"
                f" WHERE id = pg_temp.cliente_de({texto(normalizar_nif(f.nif))}, {texto(f.razon)})\n"
                f"   AND documento IS NOT NULL AND direccion IS NULL AND codigo_postal IS NULL;")

    salida.append("\nCOMMIT;\n")
    return "\n".join(salida)


def direcciones_a_completar(facturas: list[Factura]) -> list[Factura]:
    """
    La direccion de la factura mas reciente de cada cliente con NIF espanol
    valido: es el unico caso en que con ella queda listo para facturar. Los
    extranjeros no, porque su pais no viene en ningun fichero de NEXTGO.
    """
    ultima = {}
    for f in sorted(facturas, key=lambda f: f.fecha):
        if f.direccion and documento_espanol_valido(f.nif):
            ultima[normalizar_nif(f.nif)] = f
    return list(ultima.values())


# ------------------------------------------------------------------- main

def main() -> None:
    argumentos = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    argumentos.add_argument("--pdf", required=True)
    argumentos.add_argument("--registro", required=True)
    argumentos.add_argument("--vehiculos", required=True)
    argumentos.add_argument("--moto", action="append", default=[], metavar="FACTURA=MATRICULA",
                            help="moto de una factura cuyo cliente tiene varias: --moto 17=1234BCD")
    argumentos.add_argument("--completar-direcciones", action="store_true",
                            help="rellena la direccion de los clientes que no la tienen, con la de su factura")
    a = argumentos.parse_args()
    logging.getLogger("pypdf").setLevel(logging.ERROR)

    facturas = leer_registro(a.registro)
    avisos: list[str] = []
    leer_pdf(a.pdf, facturas, avisos)
    comprobar(facturas)

    indicadas = {int(k): v.replace(" ", "").upper() for k, v in (m.split("=", 1) for m in a.moto)}
    motos = leer_vehiculos(a.vehiculos)
    sin_moto = []
    for f in facturas.values():
        suyas = motos.get(clave(f.razon), [])
        f.matricula = indicadas.get(f.corto) or (suyas[0] if len(suyas) == 1 else None)
        if f.matricula is None:
            sin_moto.append(f"{f.corto} · {f.razon}: tiene {len(suyas)} motos ({', '.join(suyas) or 'ninguna'})."
                            f" Se indica con --moto {f.corto}=MATRICULA")

    ordenadas = sorted(facturas.values(), key=lambda f: f.corto)
    sys.stdout.write(sql(ordenadas, PdfReader(a.pdf), a.completar_direcciones))

    total = sum((f.total for f in ordenadas), Decimal(0))
    print(f"{len(ordenadas)} facturas y {sum(len(f.lineas) for f in ordenadas)} lineas, que cuadran con el "
          f"registro ({total} € en total).", file=sys.stderr)
    print(f"Moto: {len(ordenadas) - len(sin_moto) - len(indicadas)} deducidas, {len(indicadas)} indicadas, "
          f"{len(sin_moto)} sin moto.", file=sys.stderr)
    for s in sin_moto:
        print(f"   {s}", file=sys.stderr)
    if a.completar_direcciones:
        print(f"Direcciones para completar: {len(direcciones_a_completar(ordenadas))} clientes.", file=sys.stderr)
    for aviso in avisos:
        print(f"AVISO {aviso}", file=sys.stderr)


if __name__ == "__main__":
    main()
