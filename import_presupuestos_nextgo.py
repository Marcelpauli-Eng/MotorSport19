#!/usr/bin/env python3
"""
Carga en MotorSport19 los presupuestos de NEXTGO como ordenes de trabajo.

Cada presupuesto pasa a ser una orden con sus lineas, de su cliente y su moto:

  * el que ya se facturo en NEXTGO (su total y su cliente coinciden con una
    factura del registro) entra ENTREGADA y enlazada a esa factura, que tiene
    que estar cargada antes con import_facturas_nextgo.py. Asi no sale como
    trabajo sin facturar ni deja emitir otra factura (V26);
  * el que en sus observaciones dice que el cliente lo rechazo, RECHAZADA;
  * el resto, PRESUPUESTADA: pendiente de que el cliente lo apruebe.

Las piezas se buscan en el almacen por su codigo, y si no estan se dan de alta
sin existencias. Las de codigo GENERICO van todas a una pieza «GENERICO», como
en NEXTGO; la linea conserva su propia descripcion.

Uso, en el Mac donde estan los ficheros (hace falta pdftotext, de poppler):

    python3 import_presupuestos_nextgo.py \\
        --pdf ~/Downloads/"IMPRESION DE MULTIPLES PRESUPUESTOS-2.pdf" \\
        --registro ~/Downloads/"Registro facturación.csv" \\
        > ~/Downloads/presupuestos_nextgo.sql

El SQL lleva datos personales: se escribe fuera del repositorio y no se sube.
Se aplica en el servidor igual que el de las facturas, en una sola
transaccion. Volver a aplicarlo no duplica nada: se salta los presupuestos que
ya tienen orden.
"""

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import date, datetime
from decimal import ROUND_HALF_UP, Decimal

from import_facturas_nextgo import (DOCUMENTO, FUNCIONES, Linea, clave, importe, leer_lineas, leer_registro,
                                    normalizar_nif, texto)

TOTAL_PRESUPUESTO = re.compile(r"TOTAL PRESUPUESTO:\s*([\d.,]+)€")
# «CLIENTE: Andrea VERDU PEREA CIF: 21761560h TOTAL A PAGAR: ...»
CLIENTE = re.compile(r"CLIENTE:\s*(?P<nombre>.*?)\s+CIF:\s*(?P<nif>\S*)")
# El descuento general sale bajo el rotulo DESCUENTOS, entre parentesis: «(17,000%)»
DESCUENTO_GENERAL = re.compile(r"^\s*\((\d+,\d{3})%\)\s*$", re.M)
LARGO_DESCRIPCION = 300


@dataclass
class Presupuesto:
    documento: str
    fecha: date
    nif: str
    razon: str
    matricula: str
    bastidor: str | None
    modelo: str
    km: int
    observaciones: str
    paginas: list[int] = field(default_factory=list)
    lineas: list[Linea] = field(default_factory=list)
    subtotales: list[Decimal] = field(default_factory=list)
    descuento_general: Decimal = Decimal(0)
    total_pdf: Decimal | None = None
    factura: object = None       # la Factura del registro, si ya se facturo

    @property
    def corto(self) -> int:
        return int(self.documento.rsplit("|", 1)[-1][-6:])

    @property
    def rechazado(self) -> bool:
        return "RECHAZ" in clave(self.observaciones)


# ----------------------------------------------------------------- lectura

def leer_pdf(ruta: str, avisos: list[str]) -> list[Presupuesto]:
    texto_pdf = subprocess.run(["pdftotext", "-layout", ruta, "-"], capture_output=True, text=True,
                               check=True).stdout
    presupuestos: dict[str, Presupuesto] = {}
    for numero_pagina, pagina in enumerate(texto_pdf.split("\f")):
        documento = DOCUMENTO.search(pagina)
        if not documento:
            continue
        p = presupuestos.get(documento.group(0))
        if p is None:
            p = presupuestos[documento.group(0)] = cabecera(documento.group(0), pagina)
        p.paginas.append(numero_pagina)
        leer_lineas(pagina, p, avisos)
        total = TOTAL_PRESUPUESTO.search(pagina)
        if total:
            p.total_pdf = importe(total.group(1))
        general = DESCUENTO_GENERAL.search(pagina)
        if general:
            p.descuento_general = importe(general.group(1))
    return sorted(presupuestos.values(), key=lambda p: p.corto)


def cabecera(documento: str, pagina: str) -> Presupuesto:
    fecha = re.search(re.escape(documento) + r"\s+\S+\s+(\d\d/\d\d/\d{4})", pagina)
    cliente = CLIENTE.search(" ".join(pagina.split()))
    renglones = pagina.splitlines()
    fila = next(i for i, r in enumerate(renglones) if "MATRÍCULA" in r and "BASTIDOR" in r)
    matricula, bastidor, modelo, km = vehiculo(next(r for r in renglones[fila + 1:] if r.strip()))
    return Presupuesto(
        documento=documento, fecha=datetime.strptime(fecha[1], "%d/%m/%Y").date(),
        nif=cliente["nif"], razon=cliente["nombre"],
        matricula=matricula, bastidor=bastidor, modelo=modelo, km=km,
        observaciones=observaciones(pagina))


def vehiculo(renglon: str) -> tuple[str, str | None, str, int]:
    """
    «5977NFT   ZDM3000ESSB001008   DUCATI STREETFIGHTER V2 S RED   0». El bastidor
    puede faltar, y a veces va pegado al modelo con un solo espacio: se reconoce
    por ser una palabra larga con cifras.
    """
    trozos = re.split(r"\s{2,}", renglon.strip())
    matricula, km = trozos[0].replace(" ", "").upper(), int(trozos[-1])
    resto = " ".join(trozos[1:-1]).split()
    bastidor = None
    if resto and len(resto[0]) >= 11 and re.search(r"\d", resto[0]):
        bastidor, resto = resto[0], resto[1:]
    return matricula, bastidor, " ".join(resto), km


def observaciones(pagina: str) -> str:
    """Lo escrito entre OBSERVACIONES DEL DOCUMENTO y el pie del cliente."""
    bloque = re.search(r"OBSERVACIONES DEL DOCUMENTO[^\n]*\n(.*?)\nCLIENTE:", pagina, re.S)
    return "\n".join(r.strip() for r in bloque.group(1).strip().splitlines()).strip() if bloque else ""


# ------------------------------------------------------------- comprobacion

def redondeo(valor: Decimal) -> Decimal:
    return valor.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def descuento_total(linea: Linea, general: Decimal) -> Decimal:
    """El de la linea y el general encadenados: 50 % y luego 17 % son un 58,5 %."""
    queda = (1 - linea.descuento / 100) * (1 - general / 100)
    return redondeo((1 - queda) * 100)


def total_en_la_base(p: Presupuesto) -> Decimal:
    """Lo que sumaran sus lineas en la base, que redondea linea a linea (V4)."""
    total = Decimal(0)
    for l in p.lineas:
        base = redondeo(l.cantidad * l.precio * (1 - descuento_total(l, p.descuento_general) / 100))
        total += base + redondeo(base * Decimal("0.21"))
    return total


def comprobar(presupuestos: list[Presupuesto]) -> list[str]:
    errores, diferencias = [], []
    for p in presupuestos:
        suma = sum((l.importe for l in p.lineas), Decimal(0))
        if not p.lineas or abs(suma - sum(p.subtotales, Decimal(0))) >= Decimal("0.005"):
            errores.append(f"{p.corto}: sus lineas suman {suma} € y sus bloques {sum(p.subtotales)} €")
        if p.total_pdf is None:
            errores.append(f"{p.corto}: no encuentro su total")
        elif abs(total_en_la_base(p) - p.total_pdf) >= Decimal("0.05"):
            errores.append(f"{p.corto}: sus lineas dan {total_en_la_base(p)} € y el PDF dice {p.total_pdf} €")
        elif total_en_la_base(p) != p.total_pdf:
            diferencias.append(f"{p.corto}: {total_en_la_base(p)} € aqui y {p.total_pdf} € en NEXTGO "
                               f"(redondeo por linea)")
    if errores:
        raise SystemExit("No cuadra, no se genera nada:\n  " + "\n  ".join(errores))
    return diferencias


def emparejar(presupuestos: list[Presupuesto], registro: dict) -> None:
    """La factura de cada presupuesto: mismo cliente y mismo total."""
    por_total = {}
    for f in registro.values():
        por_total.setdefault((normalizar_nif(f.nif), f.total), []).append(f)
    for p in presupuestos:
        candidatas = por_total.get((normalizar_nif(p.nif), p.total_pdf), [])
        if len(candidatas) == 1:
            p.factura = candidatas.pop()   # una factura no cierra dos presupuestos


# --------------------------------------------------------------------- SQL

CARGAR = r"""
CREATE FUNCTION pg_temp.pieza_de(p_codigo TEXT, p_descripcion TEXT, p_precio NUMERIC) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_sku TEXT := CASE WHEN p_codigo IS NULL OR upper(p_codigo) = 'GENERICO' THEN 'GENERICO' ELSE p_codigo END;
    v_id  BIGINT;
BEGIN
    SELECT id INTO v_id FROM pieza WHERE upper(sku) = upper(v_sku);
    IF v_id IS NULL THEN
        INSERT INTO pieza (sku, descripcion, precio_venta, observaciones)
        VALUES (v_sku,
                CASE WHEN v_sku = 'GENERICO' THEN 'Artículo genérico' ELSE left(p_descripcion, 200) END,
                CASE WHEN v_sku = 'GENERICO' THEN 0 ELSE p_precio END,
                'Dada de alta al cargar los presupuestos de NEXTGO.')
        RETURNING id INTO v_id;
    END IF;
    RETURN v_id;
END
$$;

CREATE FUNCTION pg_temp.moto_del_presupuesto(p_cliente BIGINT, p JSONB) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE
    v_id     BIGINT;
    v_modelo TEXT := coalesce(nullif(p->>'modelo', ''), 'Sin modelo');
BEGIN
    SELECT id INTO v_id FROM moto WHERE upper(replace(matricula, ' ', '')) = p->>'matricula';
    IF v_id IS NULL AND p->>'bastidor' IS NOT NULL THEN
        SELECT id INTO v_id FROM moto WHERE upper(numero_bastidor) = upper(p->>'bastidor');
    END IF;
    IF v_id IS NULL THEN
        INSERT INTO moto (cliente_id, matricula, marca, modelo, numero_bastidor, km_actual)
        VALUES (p_cliente, p->>'matricula', split_part(v_modelo, ' ', 1),
                coalesce(nullif(substr(v_modelo, length(split_part(v_modelo, ' ', 1)) + 2), ''), v_modelo),
                p->>'bastidor', (p->>'km')::int)
        RETURNING id INTO v_id;
        RAISE NOTICE 'Moto % dada de alta.', p->>'matricula';
    END IF;
    RETURN v_id;
END
$$;

CREATE FUNCTION pg_temp.cargar(p JSONB) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    v_cliente BIGINT;
    v_moto    BIGINT;
    v_orden   BIGINT;
    v_numero  INT;
    v_n       INT := 0;
    v_linea   JSONB;
    v_entrada TIMESTAMPTZ := ((p->>'fecha')::date + time '09:00') AT TIME ZONE 'Europe/Madrid';
    v_salida  TIMESTAMPTZ;
BEGIN
    IF EXISTS (SELECT 1 FROM orden_trabajo WHERE position(p->>'documento' IN coalesce(observaciones, '')) > 0) THEN
        RAISE NOTICE 'El presupuesto % ya tiene orden: no se toca.', p->>'numero';
        RETURN;
    END IF;

    v_cliente := pg_temp.cliente_de(p->>'nif', p->>'cliente');
    v_moto := pg_temp.moto_del_presupuesto(v_cliente, p);

    INSERT INTO contador_ot (ejercicio) VALUES (extract(year FROM v_entrada)::int) ON CONFLICT DO NOTHING;
    UPDATE contador_ot SET ultimo_numero = ultimo_numero + 1
     WHERE ejercicio = extract(year FROM v_entrada)::int
    RETURNING ultimo_numero INTO v_numero;

    INSERT INTO orden_trabajo (ejercicio, numero, moto_id, cliente_id, fecha_entrada, km_entrada,
                               problema_reportado, diagnostico, estado, tarifa_hora, fecha_presupuesto,
                               observaciones, created_at)
    VALUES (extract(year FROM v_entrada)::int, v_numero, v_moto, v_cliente, v_entrada, (p->>'km')::int,
            p->>'problema', p->>'diagnostico', 'PRESUPUESTADA',
            coalesce((SELECT tarifa_hora_defecto FROM configuracion_taller), 0), v_entrada,
            p->>'observaciones', v_entrada)
    RETURNING id INTO v_orden;

    INSERT INTO cambio_estado_ot (orden_trabajo_id, estado_anterior, estado_nuevo, fecha, motivo)
    VALUES (v_orden, NULL, 'RECIBIDA', v_entrada, NULL),
           (v_orden, 'RECIBIDA', 'PRESUPUESTADA', v_entrada, 'Presupuesto ' || (p->>'numero') || ' de NEXTGO');

    FOR v_linea IN SELECT * FROM jsonb_array_elements(p->'lineas') LOOP
        v_n := v_n + 1;
        INSERT INTO linea_ot (orden_trabajo_id, numero_linea, tipo, descripcion, pieza_id, cantidad,
                              precio_unitario, descuento_pct, tipo_iva, porcentaje_iva, created_at)
        VALUES (v_orden, v_n, v_linea->>'tipo', v_linea->>'descripcion',
                CASE WHEN v_linea->>'tipo' = 'PIEZA'
                     THEN pg_temp.pieza_de(v_linea->>'codigo', v_linea->>'descripcion', (v_linea->>'precio')::numeric)
                END,
                (v_linea->>'cantidad')::numeric, (v_linea->>'precio')::numeric,
                (v_linea->>'descuento')::numeric, 'GENERAL', 21, v_entrada);
    END LOOP;

    -- Los kilometros del presupuesto, si los tiene y son los mas altos conocidos.
    UPDATE moto SET km_actual = (p->>'km')::int, version = version + 1
     WHERE id = v_moto AND (p->>'km')::int > km_actual;

    IF p->>'factura' IS NOT NULL THEN
        -- Las lineas ya estan: una orden ENTREGADA no admite ninguna mas (V6).
        v_salida := greatest(v_entrada, ((p->>'fecha_factura')::date + time '18:00') AT TIME ZONE 'Europe/Madrid');
        UPDATE orden_trabajo
           SET estado = 'ENTREGADA', fecha_aprobacion = v_entrada, aprobado_por = p->>'cliente',
               fecha_real_salida = v_salida
         WHERE id = v_orden;
        INSERT INTO cambio_estado_ot (orden_trabajo_id, estado_anterior, estado_nuevo, fecha, motivo)
        VALUES (v_orden, 'PRESUPUESTADA', 'ENTREGADA', v_salida,
                'Facturada en NEXTGO con la factura ' || (p->>'numero_factura'));
        UPDATE factura_anterior SET orden_trabajo_id = v_orden, moto_id = v_moto
         WHERE origen = 'NEXTGO' AND numero = p->>'factura';
        IF NOT FOUND THEN
            RAISE EXCEPTION 'La factura % de NEXTGO no esta cargada. Cargue antes las facturas.', p->>'factura';
        END IF;
    ELSIF p->>'rechazo' IS NOT NULL THEN
        UPDATE orden_trabajo SET estado = 'RECHAZADA', motivo_rechazo = p->>'rechazo', fecha_real_salida = v_entrada
         WHERE id = v_orden;
        INSERT INTO cambio_estado_ot (orden_trabajo_id, estado_anterior, estado_nuevo, fecha, motivo)
        VALUES (v_orden, 'PRESUPUESTADA', 'RECHAZADA', v_entrada, left(p->>'rechazo', 300));
    END IF;
END
$$;
"""


def como_json(p: Presupuesto) -> dict:
    numero = f"nº {p.corto}"
    notas = f"Presupuesto {numero} de NEXTGO ({p.documento}), del {p.fecha:%d/%m/%Y}."
    if p.factura:
        notas += f" Facturado en NEXTGO con la factura nº {p.factura.corto} del {p.factura.fecha:%d/%m/%Y}."
    return {
        "documento": p.documento, "numero": numero, "fecha": p.fecha.isoformat(),
        "nif": normalizar_nif(p.nif), "cliente": " ".join(p.razon.split()),
        "matricula": p.matricula, "bastidor": p.bastidor, "modelo": p.modelo, "km": p.km,
        "problema": p.lineas[0].descripcion[:LARGO_DESCRIPCION],
        "diagnostico": p.observaciones or notas,
        "observaciones": notas,
        "factura": p.factura.documento if p.factura else None,
        "numero_factura": f"nº {p.factura.corto}" if p.factura else None,
        "fecha_factura": p.factura.fecha.isoformat() if p.factura else None,
        "rechazo": p.observaciones if p.rechazado and not p.factura else None,
        "lineas": [{
            "tipo": l.tipo, "codigo": l.codigo, "descripcion": l.descripcion[:LARGO_DESCRIPCION],
            "cantidad": str(l.cantidad), "precio": str(l.precio),
            "descuento": str(descuento_total(l, p.descuento_general)),
        } for l in p.lineas],
    }


def sql(presupuestos: list[Presupuesto]) -> str:
    salida = ["-- Presupuestos de NEXTGO para MotorSport19, generado por import_presupuestos_nextgo.py.",
              "-- Lleva datos personales: no se sube al repositorio.",
              "BEGIN;", FUNCIONES, CARGAR]
    for p in presupuestos:
        salida.append(f"-- {p.corto} · {p.fecha:%d/%m/%Y} · {p.razon} · {p.matricula}\n"
                      f"SELECT pg_temp.cargar({texto(json.dumps(como_json(p), ensure_ascii=False))});")
    salida.append("\nCOMMIT;\n")
    return "\n".join(salida)


# ------------------------------------------------------------------- main

def main() -> None:
    argumentos = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    argumentos.add_argument("--pdf", required=True)
    argumentos.add_argument("--registro", required=True)
    a = argumentos.parse_args()

    avisos: list[str] = []
    presupuestos = leer_pdf(a.pdf, avisos)
    diferencias = comprobar(presupuestos)
    emparejar(presupuestos, leer_registro(a.registro))
    sys.stdout.write(sql(presupuestos))

    entregados = [p for p in presupuestos if p.factura]
    rechazados = [p for p in presupuestos if p.rechazado and not p.factura]
    pendientes = [p for p in presupuestos if not p.factura and not p.rechazado]
    print(f"{len(presupuestos)} presupuestos y {sum(len(p.lineas) for p in presupuestos)} lineas.", file=sys.stderr)
    print(f"  Entregados (ya facturados): {', '.join(f'{p.corto}→F{p.factura.corto}' for p in entregados)}",
          file=sys.stderr)
    print(f"  Rechazados: {', '.join(str(p.corto) for p in rechazados) or 'ninguno'}", file=sys.stderr)
    print(f"  Pendientes de aprobar: {', '.join(str(p.corto) for p in pendientes)}", file=sys.stderr)
    for d in diferencias:
        print(f"AVISO {d}", file=sys.stderr)
    for p in presupuestos:
        for l in p.lineas:
            if len(l.descripcion) > LARGO_DESCRIPCION:
                print(f"AVISO {p.corto}: descripcion recortada a {LARGO_DESCRIPCION} letras: «{l.descripcion[:40]}…»",
                      file=sys.stderr)
    for aviso in avisos:
        print(f"AVISO {aviso}", file=sys.stderr)


if __name__ == "__main__":
    main()
