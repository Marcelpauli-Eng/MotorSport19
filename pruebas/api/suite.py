#!/usr/bin/env python3
"""
Bateria de pruebas de extremo a extremo de MotorSport19.

Recorre el programa como lo recorreria el taller en un dia de trabajo, pero
metiendose por todos los recovecos: datos mal tecleados, botones pulsados dos
veces, pasos hechos en el orden equivocado, un tecnico intentando ver precios,
importes en el limite de lo que cabe.

No sustituye a los tests de JUnit (esos prueban las reglas una a una, sin base
de datos). Esta bateria prueba lo otro: que las piezas encajan de verdad cuando
hablan entre ellas por HTTP y contra PostgreSQL.

Como se lanza:

    1. Levantar una base de datos vacia y el backend contra ella:

       docker exec motorsport19-db psql -U taller -d postgres \\
           -c "DROP DATABASE IF EXISTS motorsport19_pruebas;" \\
           -c "CREATE DATABASE motorsport19_pruebas OWNER taller;"

       cd backend && SERVER_PORT=8081 \\
         SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/motorsport19_pruebas \\
         ... ./mvnw spring-boot:run

    2. python3 pruebas/api/suite.py

Devuelve 0 si todo va bien y 1 si algo falla, asi que sirve tal cual para un
servidor de integracion continua.
"""

from __future__ import annotations

import concurrent.futures
import datetime as dt
import time
import io
import zipfile

from arnes import (
    Api, aviso, caso, cliente_facturable, entrar, existencias,
    matricula, moto_de, nif, pieza_con_stock, resumen, seccion, sku,
)

ADMIN = ("admin", "admin1234")
HOY = dt.date.today()
SELLO = str(int(time.time()))[-6:]  # sufijo unico por ejecucion


def iso(dias=0, hora=10):
    """Una fecha-hora UTC en formato Instant, a N dias de hoy."""
    d = HOY + dt.timedelta(days=dias)
    return f"{d.isoformat()}T{hora:02d}:00:00Z"


# =====================================================================
# 1. Arranque: lo que el programa trae puesto de fabrica
# =====================================================================

def s1_arranque(admin: Api) -> dict:
    seccion("1. Arranque y configuracion de fabrica")

    cfg = admin.get("/configuracion")
    caso("la configuracion del taller responde", cfg.ok, f"HTTP {cfg.codigo}")
    tipos = {t["codigo"]: t["porcentaje"] for t in cfg.get("tiposIva", [])}
    caso("vienen los tipos de IVA cargados", len(tipos) >= 3, ", ".join(tipos))
    caso("el IVA general es del 21 %", str(tipos.get("GENERAL", "")).startswith("21"))

    guardada = admin.put("/configuracion", {
        "razonSocial": "Taller MotorSport19 S.L.", "nif": "B12345674",
        "direccion": "Poligono Industrial 12", "codigoPostal": "08820",
        "ciudad": "El Prat", "provincia": "Barcelona", "pais": "ES",
        "telefono": "934567890", "email": "taller@motorsport19.es",
        "tarifaHoraDefecto": "45.00", "tipoIvaDefecto": "GENERAL",
        "capacidadDiariaHoras": "16",
    })
    caso("se pueden guardar los datos fiscales del taller", guardada.ok, guardada.mensaje)

    # Un taller recien instalado tiene que poder facturar el primer trabajo del
    # primer dia. Si no hay serie, la primera factura del negocio no sale.
    series = admin.get("/facturas/series")
    ordinaria = next((s for s in series.cuerpo if s["tipo"] == "ORDINARIA"
                      and s["ejercicio"] == HOY.year), None) if series.ok else None
    caso("una instalacion nueva ya trae serie de facturacion del ejercicio en curso",
         ordinaria is not None,
         "sin serie no se puede emitir la primera factura del taller")
    if ordinaria is None:
        creada = admin.post("/facturas/series", {"codigo": "A", "ejercicio": HOY.year,
                                                 "descripcion": "Serie general",
                                                 "tipo": "ORDINARIA"})
        caso("al menos se puede crear a mano", creada.ok, creada.mensaje)
        ordinaria = creada.cuerpo

    permisos = admin.get("/roles/permisos")
    caso("el catalogo de permisos esta disponible", permisos.ok)
    todos = [p["clave"] for grupo in permisos.cuerpo for p in grupo["permisos"]] if permisos.ok else []
    caso("el catalogo trae permisos de sobra para repartir", len(todos) >= 20, f"{len(todos)} permisos")
    caso("los permisos vienen agrupados por area para que se puedan repartir",
         permisos.ok and all(g.get("titulo") for g in permisos.cuerpo),
         ", ".join(g["titulo"] for g in permisos.cuerpo[:6]) if permisos.ok else "")

    yo = admin.get("/auth/yo")
    caso("el administrador se identifica correctamente", yo.ok and yo.get("username") == "admin")
    caso("el administrador tiene todos los permisos", len(yo.get("permisos", [])) >= len(todos),
         f"{len(yo.get('permisos', []))} de {len(todos)}")

    return {"serie": ordinaria, "permisos": todos}


# =====================================================================
# 2. Datos maestros: lo que se teclea mal todos los dias
# =====================================================================

def s2_datos_maestros(admin: Api) -> dict:
    seccion("2. Alta de clientes, motos y piezas (y como se teclean mal)")

    # --- clientes
    doc = nif()
    c1 = admin.post("/clientes", {"nombre": "Ana", "apellidos": "Ruiz Vidal",
                                  "tipoDocumento": "NIF", "documento": doc,
                                  "telefono": "600111222", "email": "ana@ejemplo.es",
                                  "direccion": "Calle Sol 3", "codigoPostal": "08001",
                                  "ciudad": "Barcelona", "provincia": "Barcelona", "pais": "ES"})
    caso("se da de alta un cliente completo", c1.ok, c1.mensaje)

    dup = admin.post("/clientes", {"nombre": "Otro", "tipoDocumento": "NIF", "documento": doc})
    caso("no deja repetir el mismo documento", dup.codigo == 409, f"HTTP {dup.codigo}: {dup.mensaje}")

    letra = admin.post("/clientes", {"nombre": "Mal NIF", "tipoDocumento": "NIF", "documento": "12345678A"})
    caso("rechaza un NIF con la letra equivocada", not letra.ok, f"HTTP {letra.codigo}")

    sin_nombre = admin.post("/clientes", {"nombre": "   ", "telefono": "600000000"})
    caso("un nombre en blanco no cuela", sin_nombre.codigo == 400)

    email_malo = admin.post("/clientes", {"nombre": "Email", "email": "esto-no-es-un-email"})
    caso("un email sin arroba no cuela", email_malo.codigo == 400)

    minimo = admin.post("/clientes", {"nombre": "Cliente de paso"})
    caso("un cliente solo con nombre si se puede dar de alta", minimo.ok,
         "hay quien entra sin dar mas datos")

    acentos = admin.post("/clientes", {"nombre": "Iñaki Muñoz-Peñalver",
                                       "apellidos": "O'Connell Ferrán", "ciudad": "A Coruña"})
    caso("acentos, enyes y apostrofos se guardan bien",
         acentos.ok and acentos["nombre"] == "Iñaki Muñoz-Peñalver", acentos.get("nombre"))

    largo = admin.post("/clientes", {"nombre": "N" * 300})
    caso("un nombre larguisimo se rechaza con aviso, no revienta", largo.codigo == 400,
         f"HTTP {largo.codigo}")

    # --- motos
    cid = c1["id"]
    mat = matricula()
    m1 = admin.post("/motos", {"clienteId": cid, "matricula": mat, "marca": "Honda",
                               "modelo": "CB500F", "anio": 2020, "cilindrada": 471,
                               "color": "Rojo", "kmActual": 24000,
                               "numeroBastidor": f"VIN{SELLO}0099887766"[:20]})
    caso("se da de alta una moto", m1.ok, m1.mensaje)

    mdup = admin.post("/motos", {"clienteId": cid, "matricula": mat, "marca": "X", "modelo": "Y"})
    caso("no deja repetir matricula", mdup.codigo == 409, f"HTTP {mdup.codigo}")

    minus = admin.post("/motos", {"clienteId": cid, "matricula": mat.lower(), "marca": "X", "modelo": "Y"})
    caso("la misma matricula en minusculas tampoco cuela", minus.codigo == 409,
         "si no, se duplican fichas de la misma moto")

    huerfana = admin.post("/motos", {"clienteId": 999999, "matricula": matricula(),
                                     "marca": "X", "modelo": "Y"})
    caso("una moto de un cliente inexistente se rechaza", huerfana.codigo in (400, 404),
         f"HTTP {huerfana.codigo}")

    futuro = admin.post("/motos", {"clienteId": cid, "matricula": matricula(), "marca": "X",
                                   "modelo": "Y", "anio": HOY.year + 5})
    caso("un ano de fabricacion en el futuro se rechaza", not futuro.ok, f"HTTP {futuro.codigo}")

    kmneg = admin.post("/motos", {"clienteId": cid, "matricula": matricula(), "marca": "X",
                                  "modelo": "Y", "kmActual": -100})
    caso("un kilometraje negativo se rechaza", kmneg.codigo == 400)

    atras = admin.put(f"/motos/{m1['id']}/kilometraje", {"km": 100})
    caso("no deja poner un cuentakilometros hacia atras", not atras.ok,
         f"HTTP {atras.codigo}: {atras.mensaje}")

    adelante = admin.put(f"/motos/{m1['id']}/kilometraje", {"km": 26500})
    caso("si deja actualizar el kilometraje hacia arriba", adelante.ok)

    # --- piezas
    p_sku = sku("FIL")
    p1 = admin.post("/piezas", {"sku": p_sku, "descripcion": "Filtro de aceite HF204",
                                "marca": "Hiflo", "familia": "Filtros", "ubicacion": "A-01",
                                "stockMinimo": "5", "precioCoste": "4.20", "precioVenta": "9.90",
                                "unidadMedida": "ud", "stockInicial": "40"})
    caso("se da de alta una pieza con stock inicial", p1.ok, p1.mensaje)
    caso("el stock inicial queda registrado", float(p1.get("stockActual", 0)) == 40.0)

    psame = admin.post("/piezas", {"sku": p_sku, "descripcion": "Repe", "stockMinimo": "1",
                                   "precioCoste": "1", "precioVenta": "2"})
    caso("no deja repetir SKU", psame.codigo == 409, f"HTTP {psame.codigo}")

    pneg = admin.post("/piezas", {"sku": sku(), "descripcion": "Precio negativo", "stockMinimo": "1",
                                  "precioCoste": "-5", "precioVenta": "10"})
    caso("un precio de coste negativo se rechaza", pneg.codigo == 400)

    pperdida = admin.post("/piezas", {"sku": sku(), "descripcion": "Se vende con perdida",
                                      "stockMinimo": "1", "precioCoste": "50", "precioVenta": "10"})
    if pperdida.ok:
        aviso("deja dar de alta una pieza que se vende por debajo del coste (50 -> 10) sin decir nada")

    # --- proveedores
    prov = admin.post("/proveedores", {"nombre": "Recambios del Baix S.L.", "nif": nif(),
                                       "telefono": "930000000", "email": "pedidos@recambios.es"})
    caso("se da de alta un proveedor", prov.ok, prov.mensaje)

    return {"cliente": c1.cuerpo, "moto": m1.cuerpo, "pieza": p1.cuerpo,
            "proveedor": prov.cuerpo if prov.ok else None}


# =====================================================================
# 3. Listados: buscar, filtrar y paginar sin romper nada
# =====================================================================

def s3_listados(admin: Api, datos: dict) -> None:
    seccion("3. Listados, busquedas y paginacion")

    listados = ["/clientes", "/motos", "/piezas", "/ordenes", "/facturas",
                "/usuarios", "/roles", "/proveedores", "/servicios-tipo"]
    for ruta in listados:
        r = admin.get(ruta)
        caso(f"{ruta} responde", r.ok, f"HTTP {r.codigo}")

    busca = admin.get("/clientes?texto=" + datos["cliente"]["nombre"])
    encontrados = busca.filas
    caso("la busqueda de clientes por nombre encuentra", busca.ok and len(encontrados) >= 1)

    parcial = admin.get("/clientes?texto=" + datos["cliente"]["nombre"][:3].lower())
    hall = parcial.filas
    caso("busca por trozo del nombre y sin distinguir mayusculas", parcial.ok and len(hall) >= 1,
         f"{len(hall)} resultados")

    vacia = admin.get("/clientes?texto=zzzzzzzzznoexiste")
    caso("una busqueda sin resultados devuelve lista vacia, no un error",
         vacia.ok and len(vacia.filas) == 0)

    placa = datos["moto"]["matricula"]
    mat = admin.get("/motos?texto=" + placa.replace(" ", "%20"))
    caso("se encuentra una moto por matricula", mat.ok and len(mat.filas) >= 1, placa)

    # La matricula se guarda normalizada con espacio ("1234 ABC"), pero nadie la
    # teclea asi: se copia del permiso o del llavero, de un tiron.
    pegada = admin.get("/motos?texto=" + placa.replace(" ", ""))
    caso("se encuentra tecleando la matricula sin el espacio",
         pegada.ok and len(pegada.filas) >= 1,
         f"'{placa.replace(' ', '')}' -> {len(pegada.filas) if pegada.ok else '?'} resultados")

    guion = admin.get("/motos?texto=" + placa.replace(" ", "-"))
    caso("se encuentra tecleando la matricula con guion",
         guion.ok and len(guion.filas) >= 1,
         f"'{placa.replace(' ', '-')}' -> {len(guion.filas) if guion.ok else '?'} resultados")

    xsku = admin.get(f"/piezas/sku/{datos['pieza']['sku']}")
    caso("se encuentra una pieza por SKU exacto", xsku.ok)

    fam = admin.get("/piezas/familias")
    caso("la lista de familias de piezas responde", fam.ok, str(fam.cuerpo)[:60])

    # paginacion en los bordes
    caso("pagina 0 tamano 1 devuelve un elemento",
         len(admin.get("/clientes?page=0&size=1").filas) == 1)
    lejos = admin.get("/clientes?page=9999&size=20")
    caso("una pagina muy lejana devuelve vacio sin error",
         lejos.ok and len(lejos.filas) == 0)
    neg = admin.get("/clientes?page=-1&size=10")
    caso("una pagina negativa no revienta el servidor", neg.codigo != 500, f"HTTP {neg.codigo}")
    cero = admin.get("/clientes?page=0&size=0")
    caso("tamano de pagina 0 no revienta el servidor", cero.codigo != 500, f"HTTP {cero.codigo}")
    enorme = admin.get("/clientes?page=0&size=100000")
    caso("un tamano de pagina absurdo no tumba el servidor", enorme.codigo != 500,
         f"HTTP {enorme.codigo}")
    if enorme.ok and enorme.get("tamano", 0) >= 100000:
        aviso("acepta pedir 100.000 registros de golpe: con la base llena eso es un susto de memoria")
    orden_malo = admin.get("/clientes?sort=campoQueNoExiste,asc")
    caso("ordenar por un campo inexistente no revienta", orden_malo.codigo != 500,
         f"HTTP {orden_malo.codigo}")

    inexistentes = [("/clientes/999999", "cliente"), ("/motos/999999", "moto"),
                    ("/piezas/999999", "pieza"), ("/ordenes/999999", "orden"),
                    ("/facturas/999999", "factura"), ("/citas/999999", "cita"),
                    ("/usuarios/999999", "usuario"), ("/roles/999999", "rol")]
    for ruta, que in inexistentes:
        r = admin.get(ruta)
        caso(f"pedir un/a {que} que no existe da 404 limpio", r.codigo == 404, f"HTTP {r.codigo}")

    texto = admin.get("/clientes/no-es-un-numero")
    caso("un identificador con letras no revienta", texto.codigo != 500, f"HTTP {texto.codigo}")


# =====================================================================
# 4. Agenda: citas, reprogramaciones y entradas al taller
# =====================================================================

def s4_agenda(admin: Api, datos: dict) -> dict:
    seccion("4. Agenda de citas")

    moto = datos["moto"]
    cita = admin.post("/citas", {"fechaHora": iso(1, 9), "duracionEstimada": "2",
                                 "motoId": moto["id"], "motivo": "Revision de los 25.000",
                                 "observaciones": "El cliente espera"})
    caso("se coge una cita para una moto conocida", cita.ok, cita.mensaje)

    suelta = admin.post("/citas", {"fechaHora": iso(1, 12), "duracionEstimada": "1",
                                   "contactoNombre": "Luis Perez", "contactoTelefono": "600999888",
                                   "descripcionMoto": "Kawasaki Z650 negra",
                                   "motivo": "Ruido en la cadena"})
    caso("se coge una cita de alguien que aun no es cliente", suelta.ok, suelta.mensaje)
    caso("esa cita queda marcada como moto sin registrar", suelta.get("motoSinRegistrar") is True)

    sin_motivo = admin.post("/citas", {"fechaHora": iso(1, 15), "duracionEstimada": "1",
                                       "motoId": moto["id"], "motivo": ""})
    caso("una cita sin motivo se rechaza", sin_motivo.codigo == 400)

    larga = admin.post("/citas", {"fechaHora": iso(2), "duracionEstimada": "48",
                                  "motoId": moto["id"], "motivo": "Restauracion"})
    caso("una cita de 48 horas se rechaza", larga.codigo == 400, "el limite es 24")

    cero = admin.post("/citas", {"fechaHora": iso(2), "duracionEstimada": "0",
                                 "motoId": moto["id"], "motivo": "Nada"})
    caso("una cita de duracion cero se rechaza", cero.codigo == 400)

    pasada = admin.post("/citas", {"fechaHora": iso(-30), "duracionEstimada": "1",
                                   "motoId": moto["id"], "motivo": "Cita del mes pasado"})
    if pasada.ok:
        aviso("deja crear una cita con fecha del mes pasado sin avisar")

    cid = cita["id"]
    repro = admin.put(f"/citas/{cid}/fecha", {"fechaHora": iso(3, 11)})
    caso("se reprograma una cita a otro dia", repro.ok, repro.mensaje)

    conf = admin.post(f"/citas/{cid}/confirmacion")
    caso("se confirma la cita", conf.ok, f"estado: {conf.get('estado')}")
    reconf = admin.post(f"/citas/{cid}/confirmacion")
    caso("confirmar dos veces no crea un lio", reconf.codigo in (200, 409), f"HTTP {reconf.codigo}")

    dia = HOY + dt.timedelta(days=3)
    lista = admin.get(f"/citas?desde={dia}&hasta={dia}")
    caso("la agenda del dia muestra la cita reprogramada",
         lista.ok and any(c["id"] == cid for c in lista.cuerpo), f"{len(lista.cuerpo)} citas")

    carga = admin.get(f"/citas/carga?desde={HOY}&hasta={HOY + dt.timedelta(days=7)}")
    caso("la carga de trabajo de la semana responde", carga.ok, str(carga.cuerpo)[:80])

    semana = admin.get(f"/citas/semana?desde={HOY}&hasta={HOY + dt.timedelta(days=7)}")
    caso("la vista de semana responde", semana.ok)

    revuelto = admin.get(f"/citas?desde={HOY + dt.timedelta(days=7)}&hasta={HOY}")
    caso("un rango de fechas al reves no revienta", revuelto.codigo != 500, f"HTTP {revuelto.codigo}")

    malformada = admin.get("/citas?desde=32-13-2026&hasta=maniana")
    caso("una fecha imposible en el filtro da 400, no 500", malformada.codigo == 400,
         f"HTTP {malformada.codigo}")

    hist = admin.get(f"/citas/moto/{moto['id']}")
    caso("el historial de citas de la moto responde", hist.ok and len(hist.cuerpo) >= 1)

    # la cita se convierte en orden de trabajo
    entrada = admin.post(f"/citas/{cid}/entrada", {"motoId": moto["id"], "kmEntrada": 26800,
                                                   "problemaReportado": "Revision de los 25.000 km"})
    caso("al llegar la moto, la cita genera una orden de trabajo", entrada.ok, entrada.mensaje)
    caso("la orden queda enlazada con la cita", entrada.get("ordenTrabajoId") is not None,
         f"OT {entrada.get('ordenCodigo')}")

    otra_vez = admin.post(f"/citas/{cid}/entrada", {"motoId": moto["id"], "kmEntrada": 26800,
                                                    "problemaReportado": "otra vez"})
    caso("no deja dar entrada dos veces a la misma cita", not otra_vez.ok,
         f"HTTP {otra_vez.codigo}: {otra_vez.mensaje}")

    cancel = admin.post(f"/citas/{suelta['id']}/cancelacion", {"motivo": "El cliente no puede venir"})
    caso("se cancela una cita con motivo", cancel.ok, cancel.mensaje)

    tocar = admin.put(f"/citas/{suelta['id']}/fecha", {"fechaHora": iso(5)})
    caso("una cita cancelada ya no se reprograma", not tocar.ok, f"HTTP {tocar.codigo}")

    n_a = admin.post("/citas", {"fechaHora": iso(4), "duracionEstimada": "1",
                                "motoId": moto["id"], "motivo": "No vino"})
    falta = admin.post(f"/citas/{n_a['id']}/ausencia", {"motivo": "No se presento"})
    caso("se marca a un cliente como no presentado", falta.ok, falta.mensaje)

    aus = admin.get(f"/citas/ausencias?desde={HOY}&hasta={HOY + dt.timedelta(days=7)}")
    caso("el listado de ausencias responde", aus.ok, f"{len(aus.cuerpo) if aus.ok else '?'} ausencias")

    return {"orden_de_cita": entrada.get("ordenTrabajoId")}


# =====================================================================
# 5. Servicios tipo: las plantillas de trabajo del taller
# =====================================================================

def s5_servicios_tipo(admin: Api, datos: dict) -> dict:
    seccion("5. Servicios tipo (plantillas)")

    pieza = datos["pieza"]
    serv = admin.post("/servicios-tipo", {
        "nombre": f"Revision de 10.000 km {SELLO}",
        "descripcion": "Aceite, filtro, revision de frenos y tension de cadena",
        "lineas": [
            {"descripcion": "Cambio de aceite y filtro", "cantidad": "1.0"},
            {"descripcion": "Revision de frenos", "cantidad": "0.5"},
            {"piezaId": pieza["id"], "cantidad": "1"},
        ]})
    caso("se crea una plantilla de servicio", serv.ok, serv.mensaje)
    if serv.ok:
        caso("la plantilla suma bien las horas", str(serv["horasTotales"]).startswith("1.5"),
             f"horas: {serv['horasTotales']}")
        caso("la plantilla cuenta bien las piezas", serv["numeroDePiezas"] == 1)

    vacia = admin.post("/servicios-tipo", {"nombre": "Plantilla vacia", "lineas": []})
    caso("una plantilla sin lineas se rechaza", vacia.codigo == 400,
         "una plantilla vacia no ahorra trabajo")

    sin_nombre = admin.post("/servicios-tipo", {"nombre": "", "lineas": [{"descripcion": "x", "cantidad": "1"}]})
    caso("una plantilla sin nombre se rechaza", sin_nombre.codigo == 400)

    cant_cero = admin.post("/servicios-tipo", {"nombre": "Cantidad cero",
                                               "lineas": [{"descripcion": "x", "cantidad": "0"}]})
    caso("una linea de cantidad cero se rechaza", cant_cero.codigo == 400)

    if serv.ok:
        off = admin.put(f"/servicios-tipo/{serv['id']}/activo?activo=false")
        caso("se puede desactivar una plantilla", off.ok, off.mensaje)
        on = admin.put(f"/servicios-tipo/{serv['id']}/activo?activo=true")
        caso("y volver a activarla", on.ok)

    return {"servicio": serv.cuerpo if serv.ok else None}


# =====================================================================
# 6. El camino largo de una orden: diagnostico, presupuesto, reparacion
# =====================================================================

def s6_orden_larga(admin: Api, tecnico: Api, datos: dict) -> dict:
    seccion("6. Orden de trabajo por el camino largo (con diagnostico)")

    cli = cliente_facturable(admin, "Marina")
    moto = moto_de(admin, cli["id"], marca="Ducati", modelo="Monster 797")
    pieza = datos["pieza"]

    ot = admin.post("/ordenes", {"motoId": moto["id"], "kmEntrada": 30100,
                                 "problemaReportado": "Hace un ruido raro al frenar en frio",
                                 "fechaEstimadaSalida": str(HOY + dt.timedelta(days=3))})
    caso("se abre la orden de trabajo", ot.ok, ot.mensaje)
    oid = ot["id"]
    caso("la orden nace recibida", ot["estado"] == "RECIBIDA", ot["estado"])
    caso("la orden lleva codigo visible", bool(ot.get("codigo")), ot.get("codigo"))

    # transiciones ilegales desde recepcion
    for paso, ruta, cuerpo in [("entregar", "entrega", None),
                               ("marcar lista", "lista", None),
                               ("empezar la reparacion", "reparacion", None),
                               ("aprobar", "aprobacion", {"aprobadoPor": "X"})]:
        r = admin.post(f"/ordenes/{oid}/{ruta}", cuerpo)
        caso(f"no deja {paso} una orden recien recibida", not r.ok, f"HTTP {r.codigo}")

    sin_diag = admin.post(f"/ordenes/{oid}/presupuesto")
    caso("no deja presupuestar sin haber diagnosticado", not sin_diag.ok, sin_diag.mensaje)

    asig = admin.put(f"/ordenes/{oid}/tecnico", {"tecnicoId": tecnico_id(admin, tecnico)})
    caso("el jefe asigna la orden a un tecnico", asig.ok, asig.mensaje)

    diag = tecnico.post(f"/ordenes/{oid}/diagnostico")
    caso("el tecnico empieza el diagnostico", diag.ok, diag.mensaje)

    vacio = tecnico.put(f"/ordenes/{oid}/diagnostico", {"diagnostico": "   "})
    caso("un diagnostico en blanco se rechaza", vacio.codigo == 400)

    texto = tecnico.put(f"/ordenes/{oid}/diagnostico", {
        "diagnostico": "Pastillas de freno delanteras gastadas y disco con alabeo leve. "
                       "Se sustituyen pastillas y se rectifica el disco."})
    caso("el tecnico escribe el diagnostico", texto.ok, texto.mensaje)

    largo = tecnico.put(f"/ordenes/{oid}/diagnostico", {"diagnostico": "D" * 20000})
    caso("un diagnostico kilometrico no revienta", largo.codigo != 500, f"HTTP {largo.codigo}")

    # --- lineas
    mo = admin.post(f"/ordenes/{oid}/lineas/mano-de-obra",
                    {"descripcion": "Sustitucion de pastillas delanteras", "horas": "1.5"})
    caso("se anade mano de obra", mo.ok, mo.mensaje)

    pz = admin.post(f"/ordenes/{oid}/lineas/piezas", {"piezaId": pieza["id"], "cantidad": "2"})
    caso("se anade una pieza del almacen", pz.ok, pz.mensaje)

    fantasma = admin.post(f"/ordenes/{oid}/lineas/piezas", {"piezaId": 999999, "cantidad": "1"})
    caso("una pieza inexistente se rechaza", fantasma.codigo in (400, 404), f"HTTP {fantasma.codigo}")

    negativa = admin.post(f"/ordenes/{oid}/lineas/piezas", {"piezaId": pieza["id"], "cantidad": "-3"})
    caso("una cantidad negativa se rechaza", negativa.codigo == 400)

    horas_cero = admin.post(f"/ordenes/{oid}/lineas/mano-de-obra",
                            {"descripcion": "Nada", "horas": "0"})
    caso("mano de obra de cero horas se rechaza", horas_cero.codigo == 400)

    lineas = admin.get(f"/ordenes/{oid}/lineas")
    caso("las lineas de la orden se listan", lineas.ok and len(lineas.cuerpo) == 2,
         f"{len(lineas.cuerpo) if lineas.ok else '?'} lineas")

    linea_pieza = next(l for l in lineas.cuerpo if l["tipo"] == "PIEZA")
    linea_mo = next(l for l in lineas.cuerpo if l["tipo"] == "MANO_DE_OBRA")

    cambio = admin.put(f"/ordenes/{oid}/lineas/{linea_pieza['id']}/cantidad", {"cantidad": "3"})
    caso("se corrige la cantidad de una pieza", cambio.ok, cambio.mensaje)

    desc = admin.put(f"/ordenes/{oid}/lineas/{linea_pieza['id']}/descuento", {"descuentoPct": "10"})
    caso("se aplica un descuento a la linea de pieza", desc.ok, desc.mensaje)

    desc_mo = admin.put(f"/ordenes/{oid}/lineas/{linea_mo['id']}/descuento", {"descuentoPct": "5"})
    caso("se aplica un descuento a la mano de obra", desc_mo.ok, desc_mo.mensaje)

    exceso = admin.put(f"/ordenes/{oid}/lineas/{linea_pieza['id']}/descuento", {"descuentoPct": "150"})
    caso("un descuento del 150 % se rechaza", exceso.codigo == 400)

    precio = admin.put(f"/ordenes/{oid}/lineas/{linea_mo['id']}/precio", {"precioUnitario": "52.00"})
    caso("se cambia el precio de la hora en una linea", precio.ok, precio.mensaje)

    ajena = admin.put(f"/ordenes/{oid}/lineas/999999/cantidad", {"cantidad": "1"})
    caso("tocar una linea que no existe da 404", ajena.codigo == 404, f"HTTP {ajena.codigo}")

    borrable = admin.post(f"/ordenes/{oid}/lineas/mano-de-obra",
                          {"descripcion": "Linea que se borra", "horas": "0.25"})
    quitar = admin.delete(f"/ordenes/{oid}/lineas/{borrable['id']}")
    caso("se borra una linea equivocada", quitar.ok or quitar.codigo == 204, f"HTTP {quitar.codigo}")

    general = admin.put(f"/ordenes/{oid}/descuento-general", {"descuentoPct": "15"})
    caso("se aplica un descuento general a la orden", general.ok, general.mensaje[:90])

    tarifa = admin.put(f"/ordenes/{oid}/tarifa-hora", {"tarifaHora": "48.00"})
    caso("se cambia la tarifa por hora de la orden", tarifa.ok, tarifa.mensaje)

    tarifa_neg = admin.put(f"/ordenes/{oid}/tarifa-hora", {"tarifaHora": "-10"})
    caso("una tarifa negativa se rechaza", tarifa_neg.codigo == 400)

    pres = admin.post(f"/ordenes/{oid}/presupuesto")
    caso("se pasa la orden a presupuesto", pres.ok, pres.mensaje)
    caso("el presupuesto trae total calculado", pres.get("total") is not None,
         f"total: {pres.get('total')}")

    pdf = admin.get(f"/ordenes/{oid}/presupuesto/pdf", binario=True)
    caso("el PDF del presupuesto se genera",
         pdf.ok and isinstance(pdf.cuerpo, bytes) and pdf.cuerpo[:4] == b"%PDF",
         f"{len(pdf.cuerpo) if pdf.ok else 0} bytes")

    rep_antes = admin.post(f"/ordenes/{oid}/reparacion")
    caso("no deja reparar antes de que el cliente apruebe", not rep_antes.ok, rep_antes.mensaje)

    stock_antes = existencias(admin, pieza["id"])
    apr = admin.post(f"/ordenes/{oid}/aprobacion", {"aprobadoPor": "Marina (por telefono)"})
    caso("el cliente aprueba el presupuesto", apr.ok, apr.mensaje)
    caso("aprobar todavia NO consume almacen", existencias(admin, pieza["id"]) == stock_antes,
         f"stock: {stock_antes}")

    rep = admin.post(f"/ordenes/{oid}/reparacion")
    caso("empieza la reparacion", rep.ok and rep.get("completo") is True, rep.mensaje)
    ahora = existencias(admin, pieza["id"])
    caso("al entrar en reparacion se descuenta el almacen", ahora == stock_antes - 3,
         f"{stock_antes} - 3 = {ahora}")

    espera = admin.post(f"/ordenes/{oid}/espera-piezas", {"motivo": "Falta el disco"})
    caso("se puede dejar la orden esperando piezas", espera.ok, espera.mensaje)
    vuelta = admin.post(f"/ordenes/{oid}/reanudacion")
    caso("y reanudarla cuando llega el material", vuelta.ok, vuelta.mensaje)

    lista = admin.post(f"/ordenes/{oid}/lista")
    caso("la moto queda lista para entregar", lista.ok, lista.mensaje)

    entrega = admin.post(f"/ordenes/{oid}/entrega")
    caso("se entrega la moto al cliente", entrega.ok, entrega.mensaje)

    tras = admin.post(f"/ordenes/{oid}/lineas/mano-de-obra", {"descripcion": "Tarde", "horas": "1"})
    caso("no deja anadir trabajo a una orden ya entregada", not tras.ok, f"HTTP {tras.codigo}")

    return {"orden": oid, "cliente": cli, "moto": moto, "total": pres.get("total")}


# El id del tecnico de esta ejecucion, que se rellena al arrancar main().
TECNICO_ID = 0


def tecnico_id(admin: Api = None, tecnico: Api = None) -> int:
    return TECNICO_ID


# =====================================================================
# 7. El camino corto: el jefe deja la orden hecha y el tecnico la ejecuta
# =====================================================================

def s7_orden_preparada(admin: Api, tecnico: Api, datos: dict) -> dict:
    seccion("7. Orden preparada por direccion (el tecnico no ve precios)")

    cli = cliente_facturable(admin, "Jordi")
    moto = moto_de(admin, cli["id"], marca="KTM", modelo="Duke 390")
    pieza = datos["pieza"]
    tid = tecnico_id(admin, tecnico)

    ot = admin.post("/ordenes", {"motoId": moto["id"], "kmEntrada": 12000,
                                 "problemaReportado": "Revision de 12.000 km ya pactada"})
    oid = ot["id"]

    # Una orden recien recibida todavia no se puede cargar de trabajo: primero
    # hay que decir por donde va, si a diagnostico o directa a taller.
    pronto = admin.post(f"/ordenes/{oid}/lineas/mano-de-obra",
                        {"descripcion": "Demasiado pronto", "horas": "1"})
    caso("una orden recien recibida no admite lineas todavia, y lo explica",
         pronto.codigo == 409 and "prepara" in pronto.mensaje, pronto.mensaje[:120])

    prep = admin.post(f"/ordenes/{oid}/preparacion", {"tecnicoId": tid})
    caso("direccion deja la orden preparada y se la pasa al tecnico", prep.ok, prep.mensaje)
    caso("la orden queda en estado PREPARADA", prep.get("estado") == "PREPARADA", prep.get("estado"))
    caso("y ya lleva tecnico asignado", prep.get("tecnicoId") == tid)

    # Ya preparada, direccion compone el trabajo y pone precio.
    l1 = admin.post(f"/ordenes/{oid}/lineas/mano-de-obra",
                    {"descripcion": "Revision completa", "horas": "2"})
    l2 = admin.post(f"/ordenes/{oid}/lineas/piezas", {"piezaId": pieza["id"], "cantidad": "1"})
    tf = admin.put(f"/ordenes/{oid}/tarifa-hora", {"tarifaHora": "50.00"})
    caso("direccion compone las lineas de la orden preparada",
         l1.ok and l2.ok and tf.ok, f"{l1.codigo}/{l2.codigo}/{tf.codigo}")
    caso("y el trabajo ya queda valorado antes de que el tecnico lo toque",
         float(admin.get(f"/ordenes/{oid}").get("total") or 0) > 0,
         f"total: {admin.get(f'/ordenes/{oid}').get('total')}")

    # --- lo que ve el tecnico
    vista = tecnico.get(f"/ordenes/{oid}")
    caso("el tecnico abre la orden que le han pasado", vista.ok, vista.mensaje)

    ocultos = ["total", "baseImponible", "totalIva", "tarifaHora", "descuentoPct",
               "subtotal", "totalManoObra", "totalPiezas"]
    escapados = [c for c in ocultos if vista.get(c) is not None]
    caso("la ficha de la orden no lleva ningun importe", not escapados,
         f"se escapan: {escapados}" if escapados else "ni tarifa ni totales")

    lineas = tecnico.get(f"/ordenes/{oid}/lineas")
    campos_precio = ["precioUnitario", "importeBruto", "importeNeto", "importe",
                     "descuentoPct", "baseImponible", "cuotaIva", "total"]
    fugas = [(l.get("descripcion"), c) for l in (lineas.cuerpo if lineas.ok else [])
             for c in campos_precio if l.get(c) is not None]
    caso("las lineas que ve el tecnico no llevan precios", not fugas, str(fugas)[:150])
    caso("pero si ve el trabajo y las cantidades",
         lineas.ok and all(l.get("cantidad") is not None for l in lineas.cuerpo),
         f"{len(lineas.cuerpo) if lineas.ok else 0} lineas con cantidad")

    listado = tecnico.get("/ordenes")
    filas = listado.filas
    fugas_listado = [f["codigo"] for f in filas if f.get("total") is not None]
    caso("el listado de ordenes del tecnico tampoco lleva totales", not fugas_listado,
         str(fugas_listado)[:100])

    pdf_tec = tecnico.get(f"/ordenes/{oid}/presupuesto/pdf", binario=True)
    caso("el tecnico no puede sacar el PDF del presupuesto", not pdf_tec.ok,
         f"HTTP {pdf_tec.codigo}")

    piezas_tec = tecnico.get("/piezas")
    con_precio = [p["sku"] for p in piezas_tec.filas
                  if p.get("precioVenta") is not None or p.get("precioCoste") is not None]
    caso("el tecnico ve el almacen pero sin precios", not con_precio, str(con_precio)[:100])

    fact_tec = tecnico.get("/facturas")
    caso("el tecnico no entra en facturacion", not fact_tec.ok, f"HTTP {fact_tec.codigo}")

    est_tec = tecnico.get("/estadisticas/facturacion")
    caso("el tecnico no entra en las estadisticas de facturacion", not est_tec.ok,
         f"HTTP {est_tec.codigo}")

    sube = tecnico.put(f"/ordenes/{oid}/tarifa-hora", {"tarifaHora": "999"})
    caso("el tecnico no puede cambiar la tarifa por hora", not sube.ok, f"HTTP {sube.codigo}")

    dto = tecnico.put(f"/ordenes/{oid}/descuento-general", {"descuentoPct": "50"})
    caso("el tecnico no puede regalar un descuento", not dto.ok, f"HTTP {dto.codigo}")

    # --- el tecnico hace su trabajo
    rep = tecnico.post(f"/ordenes/{oid}/reparacion")
    caso("el tecnico arranca la reparacion directamente, sin presupuesto",
         rep.ok and rep.get("completo") is True, rep.mensaje)

    extra = tecnico.post(f"/ordenes/{oid}/lineas/mano-de-obra",
                         {"descripcion": "Ajuste de cadena que hacia falta", "horas": "0.25"})
    caso("el tecnico puede apuntar el trabajo extra que ha hecho", extra.ok, extra.mensaje)
    caso("y al apuntarlo tampoco se le devuelve el importe",
         extra.get("importeBruto") is None and extra.get("precioUnitario") is None)

    lista = tecnico.post(f"/ordenes/{oid}/lista")
    caso("el tecnico marca la moto lista", lista.ok, lista.mensaje)

    # --- y el jefe si ve los numeros
    jefe = admin.get(f"/ordenes/{oid}")
    caso("el jefe si ve el total de esa misma orden", jefe.get("total") is not None,
         f"total: {jefe.get('total')}")
    caso("el trabajo extra del tecnico aparece valorado en la ficha del jefe",
         any("Ajuste de cadena" in (l.get("descripcion") or "")
             and l.get("importeBruto") is not None
             for l in admin.get(f"/ordenes/{oid}/lineas").cuerpo))

    return {"orden": oid, "cliente": cli, "moto": moto}


# =====================================================================
# 8. Las cuentas: descuentos, IVA, redondeos y numeros grandes
# =====================================================================

def s8_cuentas(admin: Api) -> None:
    seccion("8. Calculo de importes, descuentos e IVA")

    cli = cliente_facturable(admin, "Calculo")
    moto = moto_de(admin, cli["id"])
    p = pieza_con_stock(admin, unidades=500, coste="10.00", venta="100.00")

    ot = admin.post("/ordenes", {"motoId": moto["id"], "kmEntrada": 1000,
                                 "problemaReportado": "Comprobar cuentas"})
    oid = ot["id"]
    admin.post(f"/ordenes/{oid}/preparacion")
    admin.put(f"/ordenes/{oid}/tarifa-hora", {"tarifaHora": "100.00"})
    admin.post(f"/ordenes/{oid}/lineas/mano-de-obra", {"descripcion": "Dos horas", "horas": "2"})
    admin.post(f"/ordenes/{oid}/lineas/piezas", {"piezaId": p["id"], "cantidad": "3"})

    d = admin.get(f"/ordenes/{oid}")
    caso("2 h x 100 + 3 x 100 = 500 de base", float(d["baseImponible"]) == 500.0,
         f"base: {d['baseImponible']}")
    caso("el IVA del 21 % son 105", float(d["totalIva"]) == 105.0, f"IVA: {d['totalIva']}")
    caso("el total son 605", float(d["total"]) == 605.0, f"total: {d['total']}")

    lineas = admin.get(f"/ordenes/{oid}/lineas").cuerpo
    lp = next(l for l in lineas if l["tipo"] == "PIEZA")
    admin.put(f"/ordenes/{oid}/lineas/{lp['id']}/descuento", {"descuentoPct": "10"})
    d = admin.get(f"/ordenes/{oid}")
    caso("un 10 % en la linea de 300 deja la base en 470", float(d["baseImponible"]) == 470.0,
         f"base: {d['baseImponible']}")

    # El «Dto. General» del pie del presupuesto escribe el mismo porcentaje en
    # todas las lineas. La pregunta es que pasa con lo ya pactado linea a linea.
    admin.put(f"/ordenes/{oid}/descuento-general", {"descuentoPct": "10"})
    d = admin.get(f"/ordenes/{oid}")
    caso("un descuento general del 10 % sobre lineas al 0 y al 10 deja la base en 450",
         float(d["baseImponible"]) == 450.0, f"base: {d['baseImponible']}")

    # El caso que cuesta dinero: un descuento pactado en una linea concreta y
    # despues un descuento general MENOR de cortesia.
    admin.put(f"/ordenes/{oid}/lineas/{lp['id']}/descuento", {"descuentoPct": "40"})
    antes = float(admin.get(f"/ordenes/{oid}")["total"])
    admin.put(f"/ordenes/{oid}/descuento-general", {"descuentoPct": "5"})
    despues = float(admin.get(f"/ordenes/{oid}")["total"])
    caso("hacer un descuento adicional nunca puede subir el total al cliente",
         despues <= antes,
         f"con 40 % en la pieza: {antes} EUR -> tras un 5 % general de cortesia: {despues} EUR")

    aviso_baja = admin.put(f"/ordenes/{oid}/descuento-general", {"descuentoPct": "5"})
    caso("bajar el descuento general por debajo de lo pactado se para y dice que lineas",
         aviso_baja.codigo == 422 and "40" in aviso_baja.mensaje, aviso_baja.mensaje[:150])

    confirmado = admin.put(f"/ordenes/{oid}/descuento-general",
                           {"descuentoPct": "5", "forzar": True})
    caso("y se puede hacer igualmente si se confirma", confirmado.ok, confirmado.mensaje[:80])
    caso("subir el descuento general por encima si va directo",
         admin.put(f"/ordenes/{oid}/descuento-general", {"descuentoPct": "50"}).ok)
    admin.put(f"/ordenes/{oid}/descuento-general", {"descuentoPct": "0", "forzar": True})

    admin.put(f"/ordenes/{oid}/descuento-general", {"descuentoPct": "100"})
    d = admin.get(f"/ordenes/{oid}")
    caso("un descuento del 100 % deja la orden a cero, no en negativo",
         float(d["baseImponible"]) == 0.0 and float(d["total"]) == 0.0,
         f"base: {d['baseImponible']}, total: {d['total']}")
    admin.put(f"/ordenes/{oid}/descuento-general", {"descuentoPct": "0", "forzar": True})

    # --- IVA por orden
    cambio = admin.put(f"/ordenes/{oid}/tipo-iva", {"tipoIva": "REDUCIDO"})
    if cambio.ok:
        d = admin.get(f"/ordenes/{oid}")
        caso("cambiar el tipo de IVA de la orden recalcula el total",
             float(d["totalIva"]) != 105.0, f"IVA ahora: {d['totalIva']}")
        malo = admin.put(f"/ordenes/{oid}/tipo-iva", {"tipoIva": "INVENTADO"})
        caso("un tipo de IVA inexistente se rechaza con mensaje claro",
             not malo.ok and malo.codigo != 500, f"HTTP {malo.codigo}: {malo.mensaje}")
        admin.put(f"/ordenes/{oid}/tipo-iva", {"tipoIva": "GENERAL"})

    # --- redondeos
    moto2 = moto_de(admin, cli["id"])
    ot2 = admin.post("/ordenes", {"motoId": moto2["id"], "kmEntrada": 1001,
                                  "problemaReportado": "Redondeos"})
    o2 = ot2["id"]
    admin.post(f"/ordenes/{o2}/preparacion")
    admin.put(f"/ordenes/{o2}/tarifa-hora", {"tarifaHora": "33.33"})
    admin.post(f"/ordenes/{o2}/lineas/mano-de-obra", {"descripcion": "Un tercio", "horas": "0.333"})
    d2 = admin.get(f"/ordenes/{o2}")
    caso("un importe con muchos decimales se redondea a dos",
         len(str(d2["baseImponible"]).split(".")[-1]) <= 2, f"base: {d2['baseImponible']}")
    caso("base + IVA cuadra con el total",
         abs(float(d2["baseImponible"]) + float(d2["totalIva"]) - float(d2["total"])) < 0.005,
         f"{d2['baseImponible']} + {d2['totalIva']} = {d2['total']}")

    # --- numeros grandes
    caro = pieza_con_stock(admin, unidades=10, coste="1000", venta="99999.99")
    moto3 = moto_de(admin, cli["id"])
    ot3 = admin.post("/ordenes", {"motoId": moto3["id"], "kmEntrada": 1002,
                                  "problemaReportado": "Importe muy alto"})
    o3 = ot3["id"]
    admin.post(f"/ordenes/{o3}/preparacion")
    grande = admin.post(f"/ordenes/{o3}/lineas/piezas", {"piezaId": caro["id"], "cantidad": "9"})
    caso("una linea de casi un millon de euros se admite sin desbordar", grande.ok, grande.mensaje)
    d3 = admin.get(f"/ordenes/{o3}")
    caso("y el total sale bien", d3.get("total") is not None and float(d3["total"]) > 900000,
         f"total: {d3.get('total')}")

    minima = admin.post(f"/ordenes/{o3}/lineas/mano-de-obra",
                        {"descripcion": "Un minuto", "horas": "0.001"})
    caso("una fraccion de hora minima se admite", minima.ok, minima.mensaje)

    aberrante = admin.post(f"/ordenes/{o3}/lineas/piezas",
                           {"piezaId": caro["id"], "cantidad": "99999999999999999999"})
    caso("una cantidad absurda se rechaza sin reventar", not aberrante.ok and aberrante.codigo != 500,
         f"HTTP {aberrante.codigo}")


# =====================================================================
# 9. Almacen: entradas, salidas, ajustes y devoluciones
# =====================================================================

def s9_almacen(admin: Api, tecnico: Api) -> None:
    seccion("9. Almacen")

    p = pieza_con_stock(admin, unidades=10, coste="5.00", venta="12.00")
    pid = p["id"]

    ent = admin.post(f"/inventario/piezas/{pid}/entradas",
                     {"cantidad": "25", "documentoProveedor": "ALB-2026-118",
                      "precioCosteUnitario": "5.50", "motivo": "Pedido semanal"})
    caso("se registra una entrada de mercancia", ent.ok, ent.mensaje)
    caso("el stock sube a 35", existencias(admin, pid) == 35.0, str(existencias(admin, pid)))

    sal = admin.post(f"/inventario/piezas/{pid}/salidas",
                     {"cantidad": "5", "motivo": "Venta en mostrador"})
    caso("se registra una salida", sal.ok, sal.mensaje)
    caso("el stock baja a 30", existencias(admin, pid) == 30.0)

    pasada = admin.post(f"/inventario/piezas/{pid}/salidas",
                        {"cantidad": "1000", "motivo": "Mas de lo que hay"})
    caso("no deja sacar mas de lo que hay", not pasada.ok, f"HTTP {pasada.codigo}: {pasada.mensaje}")
    caso("y el stock no se ha movido", existencias(admin, pid) == 30.0)

    sin_motivo = admin.post(f"/inventario/piezas/{pid}/ajustes", {"cantidad": "5", "motivo": ""})
    caso("un ajuste sin motivo se rechaza", sin_motivo.codigo == 400,
         "un ajuste sin explicacion es un descuadre sin firmar")

    aj = admin.post(f"/inventario/piezas/{pid}/ajustes",
                    {"cantidad": "-2", "motivo": "Recuento: dos rotas en la estanteria"})
    caso("se registra un ajuste negativo por rotura", aj.ok, aj.mensaje)
    caso("el stock queda en 28", existencias(admin, pid) == 28.0)

    hundir = admin.post(f"/inventario/piezas/{pid}/ajustes",
                        {"cantidad": "-500", "motivo": "Ajuste imposible"})
    caso("un ajuste que dejaria el stock en negativo se rechaza", not hundir.ok,
         f"HTTP {hundir.codigo}")

    cero = admin.post(f"/inventario/piezas/{pid}/entradas",
                      {"cantidad": "0", "motivo": "Nada"})
    caso("una entrada de cero unidades se rechaza", cero.codigo == 400)

    movs = admin.get(f"/inventario/piezas/{pid}/movimientos")
    n = len(movs.filas)
    caso("el historial de movimientos de la pieza esta completo", movs.ok and n >= 4,
         f"{n} movimientos")

    todos = admin.get("/inventario/movimientos")
    caso("el libro de movimientos general responde", todos.ok)

    bajo = pieza_con_stock(admin, unidades=1, coste="2", venta="4")
    admin.put(f"/piezas/{bajo['id']}", {"sku": bajo["sku"], "descripcion": bajo["descripcion"],
                                        "stockMinimo": "10", "unidadMedida": "ud"})
    alertas = admin.get("/inventario/alertas")
    caso("la pieza por debajo del minimo sale en las alertas de reposicion",
         alertas.ok and any(a["piezaId"] == bajo["id"] for a in alertas.cuerpo),
         f"{len(alertas.cuerpo) if alertas.ok else 0} alertas")

    a_tec = tecnico.get("/inventario/alertas")
    if a_tec.ok:
        con_precio = [a["sku"] for a in a_tec.cuerpo if a.get("precioCoste") is not None]
        caso("las alertas que ve el tecnico no llevan precio de coste", not con_precio,
             str(con_precio)[:80])

    # --- devolucion de material desde una orden
    cli = cliente_facturable(admin, "Devolucion")
    moto = moto_de(admin, cli["id"])
    ot = admin.post("/ordenes", {"motoId": moto["id"], "kmEntrada": 500,
                                 "problemaReportado": "Se pidio material de mas"})
    oid = ot["id"]
    admin.post(f"/ordenes/{oid}/preparacion")
    admin.post(f"/ordenes/{oid}/lineas/piezas", {"piezaId": pid, "cantidad": "6"})
    admin.post(f"/ordenes/{oid}/lineas/mano-de-obra", {"descripcion": "Montaje", "horas": "1"})
    admin.post(f"/ordenes/{oid}/presupuesto")
    admin.post(f"/ordenes/{oid}/aprobacion", {"aprobadoPor": "Cliente"})
    antes = existencias(admin, pid)
    admin.post(f"/ordenes/{oid}/reparacion")
    caso("la reparacion consume las 6 unidades", existencias(admin, pid) == antes - 6,
         f"{antes} -> {existencias(admin, pid)}")

    linea = next(l for l in admin.get(f"/ordenes/{oid}/lineas").cuerpo if l["tipo"] == "PIEZA")
    dev = admin.post(f"/ordenes/{oid}/lineas/{linea['id']}/devoluciones",
                     {"cantidad": "2", "motivo": "Sobraron dos, vuelven al almacen"})
    caso("se devuelven al almacen las que sobraron", dev.ok, dev.mensaje)
    caso("el stock recupera las 2 unidades", existencias(admin, pid) == antes - 4,
         f"stock: {existencias(admin, pid)}")

    exceso = admin.post(f"/ordenes/{oid}/lineas/{linea['id']}/devoluciones",
                        {"cantidad": "99", "motivo": "Devolver mas de lo que se puso"})
    caso("no deja devolver mas de lo que se monto", not exceso.ok,
         f"HTTP {exceso.codigo}: {exceso.mensaje}")


# =====================================================================
# 10. Facturacion: emitir, rectificar y que la cadena aguante
# =====================================================================

def s10_facturacion(admin: Api, datos: dict, larga: dict, corta: dict) -> dict:
    seccion("10. Facturacion")

    serie = datos["serie"]["id"]

    f1 = admin.post("/facturas", {"ordenTrabajoId": larga["orden"], "serieId": serie})
    caso("se emite la factura de la orden entregada", f1.ok, f1.mensaje)
    caso("la factura lleva numero correlativo", bool(f1.get("numeroCompleto")),
         f1.get("numeroCompleto"))
    caso("la factura tiene huella y numero de registro",
         f1.get("numeroRegistro") is not None, f"registro #{f1.get('numeroRegistro')}")
    caso("la factura cuadra: base + IVA = total",
         abs(float(f1["baseImponible"]) + float(f1["totalIva"]) - float(f1["total"])) < 0.005,
         f"{f1['baseImponible']} + {f1['totalIva']} = {f1['total']}")
    caso("la factura lleva los datos fiscales del taller",
         f1.get("emisor") and f1["emisor"].get("nif"), str(f1.get("emisor", {}))[:60])
    caso("y los del cliente", f1.get("receptor") and f1["receptor"].get("nombre"))
    caso("y la matricula de la moto", bool(f1.get("matricula")), f1.get("matricula"))

    repe = admin.post("/facturas", {"ordenTrabajoId": larga["orden"], "serieId": serie})
    caso("no deja facturar dos veces la misma orden", not repe.ok,
         f"HTTP {repe.codigo}: {repe.mensaje}")

    sin_entregar = admin.post("/facturas", {"ordenTrabajoId": corta["orden"], "serieId": serie})
    if not sin_entregar.ok:
        caso("no deja facturar una orden que aun no se ha entregado", True, sin_entregar.mensaje)
        admin.post(f"/ordenes/{corta['orden']}/entrega")
        sin_entregar = admin.post("/facturas", {"ordenTrabajoId": corta["orden"], "serieId": serie})
    caso("una vez entregada, si se factura", sin_entregar.ok, sin_entregar.mensaje)
    f2 = sin_entregar

    caso("la numeracion avanza de uno en uno",
         f2["numero"] == f1["numero"] + 1, f"{f1['numeroCompleto']} -> {f2['numeroCompleto']}")

    fantasma = admin.post("/facturas", {"ordenTrabajoId": 999999, "serieId": serie})
    caso("facturar una orden inexistente da 404, no 500", fantasma.codigo in (400, 404),
         f"HTTP {fantasma.codigo}")

    mala_serie = admin.post("/facturas", {"ordenTrabajoId": larga["orden"], "serieId": 999999})
    caso("una serie inexistente se rechaza", not mala_serie.ok and mala_serie.codigo != 500,
         f"HTTP {mala_serie.codigo}")

    pdf = admin.get(f"/facturas/{f1['id']}/pdf", binario=True)
    caso("el PDF de la factura se genera",
         pdf.ok and pdf.cuerpo[:4] == b"%PDF", f"{len(pdf.cuerpo) if pdf.ok else 0} bytes")

    por_numero = admin.get(f"/facturas/numero/{f1['serieCodigo']}/{f1['ejercicio']}/{f1['numero']}")
    caso("se busca una factura por su numero", por_numero.ok and por_numero["id"] == f1["id"])

    # --- rectificativas
    series_todas = admin.get("/facturas/series")
    rectificativas = next((s for s in series_todas.cuerpo
                           if s["tipo"] == "RECTIFICATIVA" and s["ejercicio"] == HOY.year), None)
    caso("una instalacion nueva tambien trae serie para rectificar facturas",
         rectificativas is not None,
         "sin ella no se puede corregir una factura ya emitida")
    serie_r = rectificativas["id"] if rectificativas else serie

    rect = admin.post(f"/facturas/{f1['id']}/rectificativas", {
        "serieId": serie_r, "tipoRectificativa": "POR_DIFERENCIAS",
        "motivo": "Se cobro una hora de mas",
        "lineas": [{"tipo": "MANO_DE_OBRA", "descripcion": "Hora cobrada de mas",
                    "cantidad": "-1", "precioUnitario": "48.00",
                    "tipoIva": "GENERAL", "porcentajeIva": "21"}]})
    caso("se emite una rectificativa por diferencias", rect.ok, rect.mensaje)
    if rect.ok:
        caso("la rectificativa apunta a la factura corregida",
             rect.get("facturaRectificadaId") == f1["id"], rect.get("facturaRectificadaNumero"))
        caso("y su importe es negativo", float(rect["total"]) < 0, f"total: {rect['total']}")

    sin_motivo = admin.post(f"/facturas/{f1['id']}/rectificativas", {
        "serieId": serie_r, "tipoRectificativa": "POR_DIFERENCIAS", "motivo": "",
        "lineas": [{"tipo": "MANO_DE_OBRA", "descripcion": "x", "cantidad": "-1",
                    "precioUnitario": "1", "tipoIva": "GENERAL", "porcentajeIva": "21"}]})
    caso("una rectificativa sin motivo se rechaza", sin_motivo.codigo == 400,
         "Hacienda exige el motivo")

    tipo_malo = admin.post(f"/facturas/{f1['id']}/rectificativas", {
        "serieId": serie_r, "tipoRectificativa": "ANULACION_TOTAL", "motivo": "x",
        "lineas": [{"tipo": "MANO_DE_OBRA", "descripcion": "x", "cantidad": "-1",
                    "precioUnitario": "1", "tipoIva": "GENERAL", "porcentajeIva": "21"}]})
    caso("un tipo de rectificativa inventado da 400 y dice cuales valen",
         tipo_malo.codigo == 400 and "POR_" in tipo_malo.mensaje, tipo_malo.mensaje[:110])

    de_rect = admin.get(f"/facturas/{f1['id']}/rectificativas")
    caso("se listan las rectificativas de una factura", de_rect.ok and len(de_rect.cuerpo) >= 1)

    # --- series
    nueva = admin.post("/facturas/series", {"codigo": f"P{SELLO[-3:]}", "ejercicio": HOY.year,
                                            "descripcion": "Serie de pruebas", "tipo": "ORDINARIA"})
    caso("se crea una serie nueva", nueva.ok, nueva.mensaje)
    dup = admin.post("/facturas/series", {"codigo": f"P{SELLO[-3:]}", "ejercicio": HOY.year,
                                          "descripcion": "Repetida", "tipo": "ORDINARIA"})
    caso("no deja repetir codigo de serie en el mismo ejercicio", dup.codigo == 409,
         f"HTTP {dup.codigo}")

    # --- integridad de la cadena
    ver = admin.post("/facturas/verificacion")
    caso("la cadena de facturas se verifica sin anomalias",
         ver.ok and (ver.get("integra") is True or ver.get("anomalias") in (None, [], 0)),
         str(ver.cuerpo)[:140])

    ev = admin.get("/facturacion/eventos")
    caso("el registro de eventos de facturacion responde", ev.ok,
         f"{len(ev.filas)} eventos")
    ev1 = admin.get(f"/facturacion/eventos/factura/{f1['id']}")
    caso("y guarda los eventos de esta factura", ev1.ok and len(ev1.cuerpo) >= 1,
         f"{len(ev1.cuerpo) if ev1.ok else 0} eventos")

    return {"factura": f1.cuerpo, "serie": serie}


# =====================================================================
# 11. Papeles: PDFs, exportaciones para la gestoria e informes
# =====================================================================

def s11_papeles(admin: Api, datos: dict, larga: dict) -> None:
    seccion("11. PDFs, exportaciones e informes")

    hm = admin.get(f"/motos/{larga['moto']['id']}/historial/pdf", binario=True)
    caso("el historial de la moto sale en PDF",
         hm.ok and hm.cuerpo[:4] == b"%PDF", f"{len(hm.cuerpo) if hm.ok else 0} bytes")

    hc = admin.get(f"/clientes/{larga['cliente']['id']}/historial/pdf", binario=True)
    caso("el historial del cliente sale en PDF",
         hc.ok and hc.cuerpo[:4] == b"%PDF", f"{len(hc.cuerpo) if hc.ok else 0} bytes")

    nuevo = cliente_facturable(admin, "SinHistorial")
    vacio = admin.get(f"/clientes/{nuevo['id']}/historial/pdf", binario=True)
    caso("un cliente sin historial tambien saca su PDF, en blanco",
         vacio.ok and vacio.cuerpo[:4] == b"%PDF", f"HTTP {vacio.codigo}")

    hist = admin.get(f"/ordenes/moto/{larga['moto']['id']}/historial")
    caso("el historial de ordenes de la moto responde", hist.ok,
         f"{len(hist.cuerpo) if hist.ok else 0} ordenes")

    desde, hasta = f"{HOY.year}-01-01", f"{HOY.year}-12-31"

    csv = admin.get(f"/facturas/exportacion/csv?desde={desde}&hasta={hasta}", binario=True)
    caso("la exportacion CSV para la gestoria funciona", csv.ok, f"{len(csv.cuerpo) if csv.ok else 0} bytes")
    if csv.ok:
        filas = csv.cuerpo.decode("utf-8", "replace").strip().splitlines()
        caso("el CSV trae cabecera y al menos una factura", len(filas) >= 2, f"{len(filas)} lineas")

    js = admin.get(f"/facturas/exportacion/json?desde={desde}&hasta={hasta}")
    caso("la exportacion JSON funciona", js.ok)

    ids = [f["id"] for f in admin.get("/facturas").filas[:5]]
    zp = admin.get("/facturas/exportacion/pdf?" + "&".join(f"ids={i}" for i in ids), binario=True)
    caso("la exportacion de todos los PDF en ZIP funciona", zp.ok,
         f"{len(zp.cuerpo) if zp.ok else 0} bytes")
    if zp.ok and isinstance(zp.cuerpo, bytes):
        try:
            with zipfile.ZipFile(io.BytesIO(zp.cuerpo)) as z:
                caso("el ZIP se abre y lleva PDFs dentro", len(z.namelist()) >= 1,
                     f"{len(z.namelist())} ficheros")
        except Exception as e:
            caso("el ZIP se abre y lleva PDFs dentro", False, str(e))

    reves = admin.get(f"/facturas/exportacion/csv?desde={hasta}&hasta={desde}", binario=True)
    caso("un rango invertido en la exportacion no revienta", reves.codigo != 500,
         f"HTTP {reves.codigo}")

    sin_ids = admin.get("/facturas/exportacion/pdf", binario=True)
    caso("pedir el ZIP sin seleccionar nada lo explica en vez de reventar",
         sin_ids.codigo == 422, f"HTTP {sin_ids.codigo}")

    fact = admin.get("/estadisticas/facturacion")
    caso("el informe de facturacion responde", fact.ok, str(fact.cuerpo)[:100])

    iva = admin.get(f"/estadisticas/facturacion/por-iva?desde={desde}&hasta={hasta}")
    caso("el resumen por tipo de IVA responde", iva.ok)

    mes = admin.get("/estadisticas/facturacion/mensual")
    caso("el informe mensual responde", mes.ok,
         f"{len(mes.cuerpo) if mes.ok and isinstance(mes.cuerpo, list) else '?'} meses")

    top = admin.get("/estadisticas/clientes?limite=5")
    caso("el ranking de clientes responde", top.ok)

    antiguo = admin.get("/estadisticas/facturacion?ejercicio=1999")
    caso("un ejercicio sin datos devuelve ceros, no un error", antiguo.ok, str(antiguo.cuerpo)[:80])

    raro = admin.get("/estadisticas/facturacion?ejercicio=abc")
    caso("un ejercicio con letras da 400, no 500", raro.codigo == 400, f"HTTP {raro.codigo}")

    lim = admin.get("/estadisticas/clientes?limite=-5")
    caso("un limite negativo en el ranking no revienta", lim.codigo != 500, f"HTTP {lim.codigo}")


# =====================================================================
# 12. Roles a medida: dar y quitar permisos uno a uno
# =====================================================================

def s12_permisos(admin: Api, datos: dict) -> None:
    seccion("12. Roles y permisos a medida")

    rol = admin.post("/roles", {
        "nombre": f"Mostrador {SELLO}",
        "descripcion": "Atiende al publico: da de alta clientes y motos, pero no toca precios",
        "permisos": ["CLIENTES_VER", "CLIENTES_CREAR", "MOTOS_VER", "MOTOS_CREAR",
                     "ORDENES_VER", "AGENDA_VER", "AGENDA_GESTIONAR"]})
    caso("se crea un rol a medida", rol.ok, rol.mensaje)

    vacio = admin.post("/roles", {"nombre": "Sin permisos", "permisos": []})
    caso("un rol sin ningun permiso se rechaza", vacio.codigo == 400,
         "un usuario sin permisos no puede ni entrar")

    inventado = admin.post("/roles", {"nombre": "Inventado", "permisos": ["VOLAR_EN_MOTO"]})
    caso("un permiso inventado se rechaza y dice cuales valen",
         inventado.codigo == 400, inventado.mensaje[:110])

    dup = admin.post("/roles", {"nombre": rol["nombre"], "permisos": ["CLIENTES_VER"]})
    caso("no deja dos roles con el mismo nombre", dup.codigo == 409, f"HTTP {dup.codigo}")

    usu = admin.post("/usuarios", {"username": f"mostrador{SELLO}",
                                   "password": "mostrador1234", "nombreCompleto": "Carla Mostrador",
                                   "email": "carla@taller.es", "rolId": rol["id"]})
    caso("se crea un usuario con ese rol", usu.ok, usu.mensaje)

    corta = admin.post("/usuarios", {"username": "x", "password": "123",
                                     "nombreCompleto": "Clave corta", "rolId": rol["id"]})
    caso("una contrasena de tres caracteres se rechaza", corta.codigo == 400, corta.mensaje[:90])

    repe = admin.post("/usuarios", {"username": usu["username"], "password": "otracosa1234",
                                    "nombreCompleto": "Repetido", "rolId": rol["id"]})
    caso("no deja repetir nombre de usuario", repe.codigo == 409, f"HTTP {repe.codigo}")

    carla = entrar(usu["username"], "mostrador1234")
    caso("el usuario nuevo entra con su contrasena", carla.token is not None)
    caso("y arrastra exactamente los permisos de su rol",
         sorted(carla.permisos) == sorted(["CLIENTES_VER", "CLIENTES_CREAR", "MOTOS_VER",
                                           "MOTOS_CREAR", "ORDENES_VER", "AGENDA_VER", "AGENDA_GESTIONAR"]),
         str(sorted(carla.permisos)))

    puede = [("dar de alta clientes", carla.post("/clientes", {"nombre": "Alta de mostrador"})),
             ("ver el listado de motos", carla.get("/motos")),
             ("ver ordenes", carla.get("/ordenes")),
             ("ver la agenda", carla.get(f"/citas?desde={HOY}&hasta={HOY}"))]
    for que, r in puede:
        caso(f"mostrador puede {que}", r.ok, f"HTTP {r.codigo}")

    no_puede = [("emitir facturas", carla.post("/facturas", {"ordenTrabajoId": 1, "serieId": 1})),
                ("ver el listado de facturas", carla.get("/facturas")),
                ("tocar el almacen", carla.post(f"/inventario/piezas/{datos['pieza']['id']}/entradas",
                                                {"cantidad": "1", "motivo": "x"})),
                ("crear usuarios", carla.post("/usuarios", {"username": "colado",
                                                            "password": "colado1234",
                                                            "nombreCompleto": "Colado"})),
                ("crear roles", carla.post("/roles", {"nombre": "Suyo", "permisos": ["CLIENTES_VER"]})),
                ("ver estadisticas", carla.get("/estadisticas/facturacion")),
                ("cambiar la configuracion del taller", carla.put("/configuracion", {"razonSocial": "Mia"})),
                ("crear una orden", carla.post("/ordenes", {"motoId": datos["moto"]["id"],
                                                            "kmEntrada": 1, "problemaReportado": "x"}))]
    for que, r in no_puede:
        caso(f"mostrador NO puede {que}", r.codigo == 403, f"HTTP {r.codigo}")

    # quitar un permiso en caliente
    quitado = admin.put(f"/roles/{rol['id']}", {
        "nombre": rol["nombre"], "descripcion": rol.get("descripcion"),
        "permisos": ["CLIENTES_VER", "MOTOS_VER", "ORDENES_VER"]})
    caso("se le quitan permisos al rol", quitado.ok, quitado.mensaje)

    intento = carla.post("/clientes", {"nombre": "Ya no deberia poder"})
    caso("con el token viejo ya no puede crear clientes", intento.codigo == 403,
         f"HTTP {intento.codigo} - el permiso se comprueba en cada peticion, no solo al entrar")

    sigue = carla.get("/clientes")
    caso("pero sigue pudiendo consultarlos", sigue.ok, f"HTTP {sigue.codigo}")

    borrar = admin.delete(f"/roles/{rol['id']}")
    caso("no deja borrar un rol que tiene usuarios", not borrar.ok,
         f"HTTP {borrar.codigo}: {borrar.mensaje}")

    suelto = admin.post("/roles", {"nombre": "Rol sin nadie", "permisos": ["CLIENTES_VER"]})
    quitalo = admin.delete(f"/roles/{suelto['id']}")
    caso("un rol sin usuarios si se borra", quitalo.ok or quitalo.codigo == 204,
         f"HTTP {quitalo.codigo}")

    admin_rol = admin.get("/roles")
    protegido = next((r for r in admin_rol.cuerpo if r.get("sistema")), None)
    if protegido:
        toca = admin.put(f"/roles/{protegido['id']}", {"nombre": protegido["nombre"],
                                                       "permisos": ["CLIENTES_VER"]})
        if toca.ok:
            aviso("deja recortar los permisos del rol de administrador: "
                  "si se hace por error, nadie puede volver a entrar a arreglarlo")


# =====================================================================
# 13. Usuarios y sesion
# =====================================================================

def s13_sesion(admin: Api, tecnico: Api) -> None:
    seccion("13. Usuarios y sesion")

    mal = Api().post("/auth/login", {"username": "admin", "password": "loquesea"})
    caso("una contrasena mala no deja entrar", mal.codigo == 401, f"HTTP {mal.codigo}")
    caso("y el mensaje no chiva si el usuario existe",
         "admin" not in mal.mensaje.lower() or "existe" not in mal.mensaje.lower(),
         mal.mensaje[:90])

    nadie = Api().post("/auth/login", {"username": "noexiste", "password": "loquesea"})
    caso("un usuario inexistente da el mismo 401", nadie.codigo == 401)

    vacio = Api().post("/auth/login", {"username": "", "password": ""})
    caso("usuario y contrasena vacios dan 400 o 401", vacio.codigo in (400, 401), f"HTTP {vacio.codigo}")

    sin_token = Api().get("/clientes")
    caso("sin token no se entra a ningun sitio", sin_token.codigo == 401, f"HTTP {sin_token.codigo}")

    falso = Api(token="esto.no.es.un.token").get("/clientes")
    caso("un token inventado se rechaza", falso.codigo == 401, f"HTTP {falso.codigo}")

    trozos = admin.token.split(".")
    manipulado = Api(token=f"{trozos[0]}.{trozos[1]}.firmafalsificada").get("/clientes")
    caso("un token con la firma cambiada se rechaza", manipulado.codigo == 401,
         "es lo que impide que alguien se ascienda a administrador editando el token")

    # cambio de contrasena
    rol_basico = next(r["id"] for r in admin.get("/roles").cuerpo if r["nombre"] == "Mostrador")
    u = admin.post("/usuarios", {"username": f"clave{SELLO}", "password": "primera1234",
                                 "nombreCompleto": "Cambio de clave", "rolId": rol_basico})
    caso("se crea el usuario de la prueba de sesion", u.ok, u.mensaje)
    if u.ok:
        sesion = entrar(u["username"], "primera1234")
        malo = sesion.post("/auth/password", {"passwordActual": "equivocada",
                                              "passwordNueva": "segunda1234"})
        caso("no deja cambiar la contrasena sin acertar la actual", not malo.ok,
             f"HTTP {malo.codigo}")

        bien = sesion.post("/auth/password", {"passwordActual": "primera1234",
                                              "passwordNueva": "segunda1234"})
        caso("con la actual correcta si se cambia", bien.ok, bien.mensaje)

        vieja = Api().post("/auth/login", {"username": u["username"], "password": "primera1234"})
        caso("la contrasena vieja ya no sirve", vieja.codigo == 401)
        nueva = Api().post("/auth/login", {"username": u["username"], "password": "segunda1234"})
        caso("la nueva si", nueva.ok)

        reset = admin.put(f"/usuarios/{u['id']}/password", {"password": "restablecida1234"})
        caso("el administrador restablece la contrasena sin saber la anterior", reset.ok,
             f"HTTP {reset.codigo}")
        tras = Api().post("/auth/login", {"username": u["username"], "password": "restablecida1234"})
        caso("y el usuario entra con la nueva", tras.ok)

        baja = admin.post(f"/usuarios/{u['id']}/baja")
        caso("se da de baja a un usuario", baja.ok, baja.mensaje)
        no_entra = Api().post("/auth/login", {"username": u["username"],
                                              "password": "restablecida1234"})
        caso("un usuario dado de baja ya no entra", no_entra.codigo == 401, f"HTTP {no_entra.codigo}")

        token_viejo = Api(token=tras["token"]).get("/clientes")
        caso("y su token anterior deja de valer al momento", token_viejo.codigo in (401, 403),
             f"HTTP {token_viejo.codigo} - si no, seguiria dentro hasta que caducase")

        admin.post(f"/usuarios/{u['id']}/reactivacion")
        vuelve = Api().post("/auth/login", {"username": u["username"], "password": "restablecida1234"})
        caso("reactivado, vuelve a entrar", vuelve.ok)

    yo_admin = admin.get("/usuarios")
    yo = admin.get("/auth/yo")
    mio = next((x for x in yo_admin.filas if x["id"] == yo.get("id")), None)
    if mio:
        auto = admin.post(f"/usuarios/{mio['id']}/baja")
        caso("el administrador no puede darse de baja a si mismo", not auto.ok,
             f"HTTP {auto.codigo}: {auto.mensaje}")


# =====================================================================
# 14. Dos personas a la vez: el doble clic y la carrera
# =====================================================================

def s14_concurrencia(admin: Api, datos: dict) -> None:
    seccion("14. Concurrencia: botones pulsados a la vez")

    def a_la_vez(n, fn):
        with concurrent.futures.ThreadPoolExecutor(max_workers=n) as ex:
            return [f.result() for f in [ex.submit(fn, i) for i in range(n)]]

    # --- doble clic al emitir factura
    cli = cliente_facturable(admin, "DobleClic")
    moto = moto_de(admin, cli["id"])
    ot = admin.post("/ordenes", {"motoId": moto["id"], "kmEntrada": 100,
                                 "problemaReportado": "Doble clic al facturar"})
    oid = ot["id"]
    admin.post(f"/ordenes/{oid}/preparacion")
    admin.post(f"/ordenes/{oid}/lineas/mano-de-obra", {"descripcion": "Trabajo", "horas": "1"})
    admin.post(f"/ordenes/{oid}/presupuesto")
    admin.post(f"/ordenes/{oid}/aprobacion", {"aprobadoPor": "Cliente"})
    admin.post(f"/ordenes/{oid}/reparacion")
    admin.post(f"/ordenes/{oid}/lista")
    admin.post(f"/ordenes/{oid}/entrega")

    res = a_la_vez(10, lambda _: admin.post("/facturas", {"ordenTrabajoId": oid,
                                                          "serieId": datos["serie"]["id"]}))
    creadas = [r for r in res if r.ok]
    caso("diez pulsaciones a la vez producen UNA sola factura", len(creadas) == 1,
         f"{len(creadas)} facturas: {[r.get('numeroCompleto') for r in creadas]}")
    caso("las demas reciben un aviso entendible, no un error tecnico",
         all(r.codigo in (409, 400) for r in res if not r.ok),
         str(sorted({r.codigo for r in res if not r.ok})))

    # --- muchas facturas a la vez: la numeracion no puede saltarse ni repetirse
    ordenes = []
    for i in range(8):
        c = cliente_facturable(admin, f"Rafaga{i}")
        m = moto_de(admin, c["id"])
        o = admin.post("/ordenes", {"motoId": m["id"], "kmEntrada": 10,
                                    "problemaReportado": f"Rafaga {i}"})["id"]
        admin.post(f"/ordenes/{o}/preparacion")
        admin.post(f"/ordenes/{o}/lineas/mano-de-obra", {"descripcion": "Trabajo", "horas": "1"})
        admin.post(f"/ordenes/{o}/presupuesto")
        admin.post(f"/ordenes/{o}/aprobacion", {"aprobadoPor": "C"})
        admin.post(f"/ordenes/{o}/reparacion")
        admin.post(f"/ordenes/{o}/lista")
        admin.post(f"/ordenes/{o}/entrega")
        ordenes.append(o)

    salidas = a_la_vez(8, lambda i: admin.post("/facturas", {"ordenTrabajoId": ordenes[i],
                                                             "serieId": datos["serie"]["id"]}))
    okey = [r for r in salidas if r.ok]
    numeros = sorted(r["numero"] for r in okey)
    caso("ocho facturas emitidas a la vez salen las ocho", len(okey) == 8, f"{len(okey)} de 8")
    caso("ninguna repite numero", len(set(numeros)) == len(numeros), str(numeros))
    caso("y no queda ningun hueco en la numeracion",
         numeros == list(range(numeros[0], numeros[0] + len(numeros))) if numeros else False,
         str(numeros))

    # --- doble clic consumiendo almacen
    p = pieza_con_stock(admin, unidades=20)
    c = cliente_facturable(admin, "Carrera")
    m = moto_de(admin, c["id"])
    o = admin.post("/ordenes", {"motoId": m["id"], "kmEntrada": 10,
                                "problemaReportado": "Doble clic en reparacion"})["id"]
    admin.post(f"/ordenes/{o}/preparacion")
    admin.post(f"/ordenes/{o}/lineas/piezas", {"piezaId": p["id"], "cantidad": "4"})
    admin.post(f"/ordenes/{o}/presupuesto")
    admin.post(f"/ordenes/{o}/aprobacion", {"aprobadoPor": "C"})
    antes = existencias(admin, p["id"])
    rr = a_la_vez(6, lambda _: admin.post(f"/ordenes/{o}/reparacion"))
    caso("seis pulsaciones en 'iniciar reparacion' descuentan el material UNA vez",
         existencias(admin, p["id"]) == antes - 4,
         f"{antes} -> {existencias(admin, p['id'])} (esperado {antes - 4})")
    caso("y ninguna devuelve un error tecnico sin explicar",
         all(r.codigo != 500 for r in rr), str(sorted({r.codigo for r in rr})))

    # --- dos personas moviendo el mismo stock
    p2 = pieza_con_stock(admin, unidades=50)
    a_la_vez(10, lambda _: admin.post(f"/inventario/piezas/{p2['id']}/salidas",
                                      {"cantidad": "3", "motivo": "Salida simultanea"}))
    caso("diez salidas simultaneas de 3 dejan el stock cuadrado",
         existencias(admin, p2["id"]) == 20.0, f"stock: {existencias(admin, p2['id'])}")

    # --- dos altas del mismo cliente a la vez
    doc = nif()
    dos = a_la_vez(5, lambda _: admin.post("/clientes", {"nombre": "Simultaneo",
                                                         "tipoDocumento": "NIF", "documento": doc}))
    caso("cinco altas simultaneas del mismo NIF crean UN solo cliente",
         len([r for r in dos if r.ok]) == 1, f"{len([r for r in dos if r.ok])} creados")

    mat = matricula()
    cid = [r for r in dos if r.ok][0]["id"]
    motos = a_la_vez(5, lambda _: admin.post("/motos", {"clienteId": cid, "matricula": mat,
                                                        "marca": "X", "modelo": "Y"}))
    caso("cinco altas simultaneas de la misma matricula crean UNA sola moto",
         len([r for r in motos if r.ok]) == 1, f"{len([r for r in motos if r.ok])} creadas")


# =====================================================================
# 15. Robustez: lo que llega cuando algo va mal de verdad
# =====================================================================

def s15_robustez(admin: Api, datos: dict) -> None:
    seccion("15. Datos malos, cuerpos rotos y textos raros")

    roto = admin.crudo("POST", "/clientes", '{"nombre": "sin cerrar"')
    caso("un JSON sin cerrar da 400 con explicacion", roto.codigo == 400, roto.mensaje[:90])

    vacio = admin.crudo("POST", "/clientes", "")
    caso("un cuerpo vacio da 400", vacio.codigo == 400, f"HTTP {vacio.codigo}")

    texto = admin.crudo("POST", "/clientes", "esto no es json")
    caso("un cuerpo que no es JSON da 400", texto.codigo == 400)

    lista = admin.crudo("POST", "/clientes", "[1,2,3]")
    caso("una lista donde se espera un objeto da 400", lista.codigo == 400)

    tipo = admin.crudo("POST", "/motos",
                       '{"clienteId":"no-es-un-numero","matricula":"1234ABC","marca":"X","modelo":"Y"}')
    caso("una letra donde va un numero da 400 y dice el campo",
         tipo.codigo == 400 and "clienteId" in str(tipo.cuerpo), tipo.mensaje[:100])

    fecha = admin.crudo("POST", "/citas",
                        '{"fechaHora":"31-02-2026","duracionEstimada":1,"motivo":"x"}')
    caso("una fecha imposible da 400 y dice el campo", fecha.codigo == 400, fecha.mensaje[:100])

    enum = admin.crudo("POST", "/clientes",
                       '{"nombre":"X","tipoDocumento":"CARNET_DE_LA_BIBLIOTECA"}')
    caso("un tipo de documento inventado da 400 y enumera los validos",
         enum.codigo == 400 and "NIF" in str(enum.cuerpo), enum.mensaje[:110])

    nulo = admin.post("/clientes", {"nombre": None})
    caso("un campo obligatorio a nulo da 400", nulo.codigo == 400)

    hondo = {"nombre": "Profundo"}
    n = hondo
    for _ in range(200):
        n["hijo"] = {}
        n = n["hijo"]
    prof = admin.post("/clientes", hondo)
    caso("un JSON con 200 niveles de anidamiento no tumba el servidor", prof.codigo != 500,
         f"HTTP {prof.codigo}")

    emoji = admin.post("/clientes", {"nombre": "Moto 🏍️ Rapida", "apellidos": "Test 🔧🇪🇸"})
    caso("emojis en los nombres se guardan y se devuelven igual",
         emoji.ok and emoji["nombre"] == "Moto 🏍️ Rapida", emoji.get("nombre"))

    inyeccion = admin.post("/clientes", {"nombre": "Robert'); DROP TABLE cliente;--"})
    caso("un intento de inyeccion SQL se guarda como texto y no rompe nada", inyeccion.ok,
         inyeccion.get("nombre"))
    caso("y la tabla sigue ahi", admin.get("/clientes").ok)

    html = admin.post("/clientes", {"nombre": "<script>alert('x')</script>"})
    caso("etiquetas HTML se guardan tal cual sin interpretarse",
         html.ok and "<script>" in html.get("nombre", ""), html.get("nombre"))

    salto = admin.post("/clientes", {"nombre": "Con\nsalto\tde linea"})
    caso("saltos de linea y tabuladores no rompen nada", salto.ok or salto.codigo == 400,
         f"HTTP {salto.codigo}")

    nulos = admin.crudo("POST", "/clientes", '{"nombre": "Con\\u0000nulo"}')
    caso("un byte nulo en el texto no revienta la base de datos", nulos.codigo != 500,
         f"HTTP {nulos.codigo}")

    espacios = admin.post("/clientes", {"nombre": "  Ana Con Espacios  "})
    if espacios.ok and espacios["nombre"] != espacios["nombre"].strip():
        aviso("no quita los espacios de los extremos: "
              "'Ana' y ' Ana ' quedan como dos clientes distintos en las busquedas")

    obs = admin.put(f"/ordenes/{datos.get('orden_cualquiera', 0)}/datos",
                    {"observaciones": "O" * 100000})
    caso("un texto de 100.000 caracteres no revienta", obs.codigo != 500, f"HTTP {obs.codigo}")

    metodo = admin.llamar("DELETE", "/clientes/1")
    caso("un metodo HTTP no permitido da 405, no 500", metodo.codigo == 405,
         f"HTTP {metodo.codigo}: {metodo.mensaje[:80]}")

    ruta = admin.get("/rutaquenoexiste")
    caso("una ruta inexistente da 404 limpio", ruta.codigo in (401, 403, 404), f"HTTP {ruta.codigo}")


# =====================================================================
# 16. Bajas: no desaparecer cosas que se estan usando
# =====================================================================

def s16_bajas(admin: Api) -> None:
    seccion("16. Bajas y reactivaciones")

    cli = cliente_facturable(admin, "Baja")
    moto = moto_de(admin, cli["id"])
    p = pieza_con_stock(admin, unidades=5)

    ot = admin.post("/ordenes", {"motoId": moto["id"], "kmEntrada": 10,
                                 "problemaReportado": "Trabajo en curso"})
    oid = ot["id"]

    m_baja = admin.post(f"/motos/{moto['id']}/baja")
    caso("no deja dar de baja una moto con trabajo abierto", not m_baja.ok,
         f"HTTP {m_baja.codigo}: {m_baja.mensaje}")

    c_baja = admin.post(f"/clientes/{cli['id']}/baja")
    caso("no deja dar de baja un cliente con trabajo abierto", not c_baja.ok,
         f"HTTP {c_baja.codigo}: {c_baja.mensaje}")

    admin.post(f"/ordenes/{oid}/preparacion")
    admin.post(f"/ordenes/{oid}/lineas/mano-de-obra", {"descripcion": "Trabajo", "horas": "1"})
    admin.post(f"/ordenes/{oid}/presupuesto")
    admin.post(f"/ordenes/{oid}/aprobacion", {"aprobadoPor": "C"})
    admin.post(f"/ordenes/{oid}/reparacion")
    admin.post(f"/ordenes/{oid}/lista")
    admin.post(f"/ordenes/{oid}/entrega")

    m2 = admin.post(f"/motos/{moto['id']}/baja")
    caso("cerrado el trabajo, la moto si se da de baja", m2.ok, m2.mensaje)

    nueva_ot = admin.post("/ordenes", {"motoId": moto["id"], "kmEntrada": 20,
                                       "problemaReportado": "Con la moto de baja"})
    caso("una moto de baja no admite ordenes nuevas", not nueva_ot.ok, f"HTTP {nueva_ot.codigo}")

    react = admin.post(f"/motos/{moto['id']}/reactivacion")
    caso("la moto se puede reactivar", react.ok, react.mensaje)

    con_stock = admin.post(f"/piezas/{p['id']}/baja")
    caso("no deja dar de baja una pieza que todavia tiene existencias",
         not con_stock.ok, con_stock.mensaje[:110])

    admin.post(f"/inventario/piezas/{p['id']}/ajustes",
               {"cantidad": f"-{existencias(admin, p['id']):.0f}", "motivo": "Se retira del catalogo"})
    p_baja = admin.post(f"/piezas/{p['id']}/baja")
    caso("con el stock a cero si se da de baja", p_baja.ok, p_baja.mensaje)

    otra = admin.post("/ordenes", {"motoId": moto["id"], "kmEntrada": 30,
                                   "problemaReportado": "Con pieza de baja"})
    admin.post(f"/ordenes/{otra['id']}/preparacion")
    con_baja = admin.post(f"/ordenes/{otra['id']}/lineas/piezas",
                          {"piezaId": p["id"], "cantidad": "1"})
    caso("una pieza dada de baja no se puede anadir a una orden",
         not con_baja.ok and "baja" in con_baja.mensaje.lower(),
         f"HTTP {con_baja.codigo}: {con_baja.mensaje[:110]}")

    admin.post(f"/piezas/{p['id']}/reactivacion")
    caso("y se puede reactivar", admin.get(f"/piezas/{p['id']}").get("activo") is True)


# =====================================================================

def main() -> int:
    admin = entrar(*ADMIN)

    tec_user = f"tecnico{SELLO}"
    rol_tec = admin.get("/roles")
    tid_rol = next((r["id"] for r in rol_tec.cuerpo if r["nombre"] == "Taller"), None)
    if tid_rol is None:
        raise SystemExit(f"no hay rol de taller: {[r['nombre'] for r in rol_tec.cuerpo]}")
    creado = admin.post("/usuarios", {"username": tec_user, "password": "tecnico1234",
                                      "nombreCompleto": "Pau Tecnico",
                                      "email": "pau@taller.es", "rolId": tid_rol})
    if not creado.ok:
        raise SystemExit(f"No se pudo crear el tecnico: {creado}")
    tecnico = entrar(tec_user, "tecnico1234")
    global TECNICO_ID
    TECNICO_ID = creado["id"]

    disponibles = admin.get("/usuarios/tecnicos")
    if any(u["nombreCompleto"] == "Administrador" for u in (disponibles.cuerpo or [])):
        aviso("la lista para asignar ordenes a un tecnico incluye al administrador")

    base = s1_arranque(admin)
    datos = s2_datos_maestros(admin)
    datos.update(base)
    s3_listados(admin, datos)
    s4_agenda(admin, datos)
    s5_servicios_tipo(admin, datos)
    larga = s6_orden_larga(admin, tecnico, datos)
    corta = s7_orden_preparada(admin, tecnico, datos)
    s8_cuentas(admin)
    s9_almacen(admin, tecnico)
    s10_facturacion(admin, datos, larga, corta)
    s11_papeles(admin, datos, larga)
    s12_permisos(admin, datos)
    s13_sesion(admin, tecnico)
    s14_concurrencia(admin, datos)
    datos["orden_cualquiera"] = larga["orden"]
    s15_robustez(admin, datos)
    s16_bajas(admin)

    return resumen()


if __name__ == "__main__":
    raise SystemExit(main())
