"""
Arnes minimo para las pruebas de extremo a extremo contra la API.

No pretende ser un framework: solo lo justo para que cada caso de `suite.py` se
lea como lo que es, una accion de taller ("el tecnico marca la orden lista") y
no como un montaje de peticiones HTTP.

Las tres piezas que aporta:

  - `Api`, que guarda el token y traduce respuestas a algo comodo de mirar.
  - `caso` / `aviso` / `fallo`, para dejar constancia de lo que se comprueba.
  - generadores de datos validos (NIF con letra correcta, matriculas, SKUs),
    porque el programa valida de verdad y un dato inventado a mano no entra.
"""

from __future__ import annotations

import itertools
import json
import random
import string
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field

BASE = "http://localhost:8081/api"

# ---------------------------------------------------------------- resultados

FALLOS: list[str] = []
AVISOS: list[str] = []
_ok = itertools.count(1)
TOTAL_OK = 0

_SECCION = ""


def seccion(titulo: str) -> None:
    global _SECCION
    _SECCION = titulo
    print(f"\n\033[1m{'=' * 74}\n {titulo}\n{'=' * 74}\033[0m")


def caso(descripcion: str, condicion: bool, detalle: str = "") -> bool:
    """Una comprobacion. Devuelve la condicion para poder encadenar."""
    global TOTAL_OK
    if condicion:
        TOTAL_OK = next(_ok)
        print(f"  \033[32mOK\033[0m   {descripcion}" + (f"  ({detalle})" if detalle else ""))
    else:
        FALLOS.append(f"[{_SECCION}] {descripcion}" + (f" -> {detalle}" if detalle else ""))
        print(f"  \033[31mFALLO\033[0m {descripcion}" + (f"  ({detalle})" if detalle else ""))
    return condicion


def aviso(texto: str) -> None:
    """Algo que no es un fallo pero conviene mirar."""
    AVISOS.append(f"[{_SECCION}] {texto}")
    print(f"  \033[33mAVISO\033[0m {texto}")


def resumen() -> int:
    print(f"\n\033[1m{'=' * 74}\033[0m")
    print(f"Comprobaciones correctas: {TOTAL_OK}")
    if AVISOS:
        print(f"\n\033[33mAvisos ({len(AVISOS)}):\033[0m")
        for a in AVISOS:
            print(f"  - {a}")
    if FALLOS:
        print(f"\n\033[31mFallos ({len(FALLOS)}):\033[0m")
        for f in FALLOS:
            print(f"  - {f}")
        return 1
    print("\n\033[32mSin fallos.\033[0m")
    return 0


# ------------------------------------------------------------------ cliente

@dataclass
class Respuesta:
    codigo: int
    cuerpo: object
    crudo: bytes = b""

    @property
    def ok(self) -> bool:
        return 200 <= self.codigo < 300

    def __getitem__(self, clave):
        return self.cuerpo[clave]

    def get(self, clave, defecto=None):
        return self.cuerpo.get(clave, defecto) if isinstance(self.cuerpo, dict) else defecto

    @property
    def filas(self) -> list:
        """Las filas de una respuesta paginada, o la lista tal cual si no lo es."""
        if isinstance(self.cuerpo, dict):
            return self.cuerpo.get("contenido", [])
        return self.cuerpo if isinstance(self.cuerpo, list) else []

    @property
    def mensaje(self) -> str:
        if isinstance(self.cuerpo, dict):
            return str(self.cuerpo.get("mensaje", self.cuerpo))
        return str(self.cuerpo)[:300]

    def __repr__(self) -> str:
        return f"<{self.codigo} {self.mensaje[:120]}>"


@dataclass
class Api:
    """Una sesion. Cada usuario que participa en la prueba tiene la suya."""

    token: str | None = None
    quien: str = "anonimo"
    permisos: list[str] = field(default_factory=list)

    def llamar(self, metodo, ruta, cuerpo=None, binario=False, cabeceras=None) -> Respuesta:
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        pet = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
        pet.add_header("Content-Type", "application/json")
        if self.token:
            pet.add_header("Authorization", f"Bearer {self.token}")
        for k, v in (cabeceras or {}).items():
            pet.add_header(k, v)
        try:
            with urllib.request.urlopen(pet, timeout=60) as r:
                crudo = r.read()
                return Respuesta(r.status, crudo if binario else _json(crudo), crudo)
        except urllib.error.HTTPError as e:
            crudo = e.read()
            return Respuesta(e.code, _json(crudo), crudo)
        except Exception as e:  # conexion caida, timeout...
            return Respuesta(0, {"mensaje": f"sin respuesta: {e}"})

    # atajos
    def get(self, ruta, **kw):
        return self.llamar("GET", ruta, **kw)

    def post(self, ruta, cuerpo=None, **kw):
        return self.llamar("POST", ruta, cuerpo, **kw)

    def put(self, ruta, cuerpo=None, **kw):
        return self.llamar("PUT", ruta, cuerpo, **kw)

    def delete(self, ruta, **kw):
        return self.llamar("DELETE", ruta, **kw)

    def crudo(self, metodo, ruta, texto: str) -> Respuesta:
        """Envia un cuerpo tal cual, sin pasar por json.dumps. Para cuerpos rotos."""
        pet = urllib.request.Request(BASE + ruta, data=texto.encode(), method=metodo)
        pet.add_header("Content-Type", "application/json")
        if self.token:
            pet.add_header("Authorization", f"Bearer {self.token}")
        try:
            with urllib.request.urlopen(pet, timeout=60) as r:
                return Respuesta(r.status, _json(r.read()))
        except urllib.error.HTTPError as e:
            return Respuesta(e.code, _json(e.read()))
        except Exception as e:
            return Respuesta(0, {"mensaje": str(e)})


def _json(crudo: bytes):
    try:
        return json.loads(crudo)
    except Exception:
        return crudo.decode("utf-8", "replace")


def entrar(usuario: str, password: str) -> Api:
    api = Api(quien=usuario)
    r = api.post("/auth/login", {"username": usuario, "password": password})
    if not r.ok:
        raise SystemExit(f"No se pudo entrar como {usuario}: {r}")
    api.token = r["token"]
    yo = api.get("/auth/yo")
    api.permisos = yo.get("permisos", []) if yo.ok else []
    return api


# --------------------------------------------------------------- generadores

_LETRAS_NIF = "TRWAGMYFPDXBNJZSQVHLCKE"
_secuencia = itertools.count(random.randint(1000, 90000))


def nif() -> str:
    n = next(_secuencia) * 7 % 100000000
    return f"{n:08d}{_LETRAS_NIF[n % 23]}"


def matricula() -> str:
    n = next(_secuencia) % 10000
    return f"{n:04d}" + "".join(random.choices("BCDFGHJKLMNPRSTVWXYZ", k=3))


def sku(prefijo="P") -> str:
    return f"{prefijo}-{next(_secuencia)}-{''.join(random.choices(string.ascii_uppercase, k=3))}"


def cliente_facturable(api: Api, nombre="Cliente", **extra) -> dict:
    """Un cliente con datos fiscales completos: se le puede emitir factura."""
    cuerpo = {
        "nombre": nombre,
        "apellidos": "De Pruebas",
        "tipoDocumento": "NIF",
        "documento": nif(),
        "telefono": "600123456",
        "email": f"c{next(_secuencia)}@ejemplo.es",
        "direccion": "Calle Mayor 1",
        "codigoPostal": "08001",
        "ciudad": "Barcelona",
        "provincia": "Barcelona",
        "pais": "ES",
    }
    cuerpo.update(extra)
    r = api.post("/clientes", cuerpo)
    if not r.ok:
        raise SystemExit(f"No se pudo crear el cliente: {r}")
    return r.cuerpo


def moto_de(api: Api, cliente_id: int, **extra) -> dict:
    cuerpo = {
        "clienteId": cliente_id,
        "matricula": matricula(),
        "marca": "Yamaha",
        "modelo": "MT-07",
        "anio": 2021,
        "cilindrada": 689,
        "kmActual": 18000,
        "color": "Azul",
        "numeroBastidor": f"VIN{next(_secuencia):014d}",
    }
    cuerpo.update(extra)
    r = api.post("/motos", cuerpo)
    if not r.ok:
        raise SystemExit(f"No se pudo crear la moto: {r}")
    return r.cuerpo


def pieza_con_stock(api: Api, unidades=100, coste="10.00", venta="20.00", **extra) -> dict:
    cuerpo = {
        "sku": sku(),
        "descripcion": "Pieza de pruebas",
        "marca": "Generica",
        "familia": "Varios",
        "stockMinimo": "2",
        "precioCoste": coste,
        "precioVenta": venta,
        "stockInicial": str(unidades),
        "unidadMedida": "ud",
    }
    cuerpo.update(extra)
    r = api.post("/piezas", cuerpo)
    if not r.ok:
        raise SystemExit(f"No se pudo crear la pieza: {r}")
    return r.cuerpo


def existencias(api: Api, pieza_id: int) -> float:
    return float(api.get(f"/piezas/{pieza_id}")["stockActual"])
