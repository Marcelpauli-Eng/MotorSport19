"""Genera el logotipo de SportMotor en SVG y PNG.

Mismo lenguaje que el de 19 Racing Motorsport: un emblema en cursiva gruesa con
doble contorno (rosa y otro exterior) y cortes de velocidad, y al lado el nombre
en dos líneas del mismo ancho: «SPORT» en cursiva de carreras y «MOTOR» en una
letra cuadrada. Todo el texto se convierte a trazados, así que el SVG no depende
de tener las fuentes instaladas.

Uso:  pip install fonttools cairosvg  &&  python3 generar_logo.py
"""
from pathlib import Path

import cairosvg
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

AQUI = Path(__file__).resolve().parent
SALIDA = AQUI / "logo"
CURSIVA = TTFont(AQUI / "fuentes" / "Exo2.ttf")       # Exo 2 Black Italic
CUADRADA = TTFont(AQUI / "fuentes" / "Oxanium.ttf")   # Oxanium ExtraBold

ROSA = "#ff006d"
NEGRO = "#0a0c11"
BLANCO = "#ffffff"

# Versión normal para fondos claros y versión «claro» para fondos oscuros,
# igual que los PNG que ya usa la app.
VARIANTES = {
    "": {"relleno": NEGRO, "exterior": BLANCO, "texto": NEGRO},
    "-claro": {"relleno": BLANCO, "exterior": NEGRO, "texto": BLANCO},
}


class Texto:
    """Un texto convertido a trazado, con su caja real."""

    def __init__(self, fuente, texto, tam, interletra=0.0):
        glifos = fuente.getGlyphSet()
        cmap = fuente.getBestCmap()
        upm = fuente["head"].unitsPerEm
        esc = tam / upm
        pen = SVGPathPen(glifos)
        caja = BoundsPen(glifos)
        x = 0.0
        for c in texto:
            nombre = cmap[ord(c)]
            t = (esc, 0, 0, -esc, x, 0)
            glifos[nombre].draw(TransformPen(pen, t))
            glifos[nombre].draw(TransformPen(caja, t))
            x += fuente["hmtx"][nombre][0] * esc + interletra * tam
        self.d = pen.getCommands()
        self.x0, self.y0, self.x1, self.y1 = caja.bounds  # y0 negativo: por encima de la línea base

    @property
    def ancho(self):
        return self.x1 - self.x0

    @property
    def alto(self):
        return self.y1 - self.y0

    def colocado(self, x, y):
        """Transform para que la esquina superior izquierda de la caja quede en (x, y)."""
        return f"translate({x - self.x0:.2f} {y - self.y0:.2f})"


def a_tamano(fuente, texto, ancho, interletra=0.0):
    """El mismo texto con el tamaño que lo hace medir exactamente `ancho`."""
    prueba = Texto(fuente, texto, 100, interletra)
    return Texto(fuente, texto, 100 * ancho / prueba.ancho, interletra)


# ---------- Emblema «SM» ----------

EMBLEMA = Texto(CURSIVA, "SM", 200, interletra=-0.035)
TRAZO_ROSA = 20
TRAZO_EXTERIOR = 42  # centrado: asoma (42 - 20) / 2 = 11 por fuera del rosa
MARGEN = TRAZO_EXTERIOR / 2


def emblema(x, y, colores, id_):
    """Emblema con la esquina superior izquierda de su caja (con contornos) en (x, y)."""
    tr = EMBLEMA.colocado(x + MARGEN, y + MARGEN)
    d = EMBLEMA.d
    # Cortes de velocidad: cuñas rosas que entran por la izquierda en la base de
    # cada letra y se afilan hacia la derecha, como las del «19».
    h, w = EMBLEMA.alto, EMBLEMA.ancho
    bx, by = x + MARGEN, y + MARGEN
    cunas = []
    for (ini, fin) in ((-0.02, 0.30), (0.44, 0.74)):
        xa, xb = bx + w * ini, bx + w * fin
        ya = by + h * 0.80
        cunas.append(f"M{xa:.1f} {ya - 5:.1f} L{xb:.1f} {ya - 11:.1f} L{xa:.1f} {ya + 7:.1f} Z")
    return f"""
  <defs><clipPath id="{id_}"><path transform="{tr}" d="{d}"/></clipPath></defs>
  <g stroke-linejoin="miter" stroke-miterlimit="4">
    <path transform="{tr}" d="{d}" fill="{colores['exterior']}" stroke="{colores['exterior']}" stroke-width="{TRAZO_EXTERIOR}"/>
    <path transform="{tr}" d="{d}" fill="{ROSA}" stroke="{ROSA}" stroke-width="{TRAZO_ROSA}"/>
    <path transform="{tr}" d="{d}" fill="{colores['relleno']}"/>
    <path clip-path="url(#{id_})" d="{' '.join(cunas)}" fill="{ROSA}"/>
  </g>"""


def emblema_caja():
    return EMBLEMA.ancho + 2 * MARGEN, EMBLEMA.alto + 2 * MARGEN


# ---------- Nombre en dos líneas ----------

def nombre(ancho):
    sport = a_tamano(CURSIVA, "SPORT", ancho, interletra=0.02)
    motor = a_tamano(CUADRADA, "MOTOR", ancho * 0.985, interletra=0.06)
    return sport, motor


def nombre_svg(x, y, ancho, color):
    sport, motor = nombre(ancho)
    hueco = sport.alto * 0.22
    # «MOTOR» un pelo a la izquierda, como «MOTORSPORT» bajo la cursiva de «RACING».
    return (
        f'<path transform="{sport.colocado(x + ancho * 0.015, y)}" d="{sport.d}" fill="{color}"/>'
        f'<path transform="{motor.colocado(x, y + sport.alto + hueco)}" d="{motor.d}" fill="{color}"/>',
        sport.alto + hueco + motor.alto,
    )


def svg(w, h, cuerpo):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w:.0f} {h:.0f}" '
            f'width="{w:.0f}" height="{h:.0f}">{cuerpo}\n</svg>\n')


def horizontal(colores, sufijo):
    ew, eh = emblema_caja()
    ancho_nombre = ew * 1.02
    _, alto_nombre = nombre_svg(0, 0, ancho_nombre, "none")
    sep = ew * 0.10
    w, h = ew + sep + ancho_nombre, eh
    texto, _ = nombre_svg(ew + sep, (eh - alto_nombre) / 2, ancho_nombre, colores["texto"])
    return svg(w, h, emblema(0, 0, colores, "c" + sufijo) + texto)


def vertical(colores, sufijo):
    ew, eh = emblema_caja()
    ancho_nombre = ew * 1.0
    _, alto_nombre = nombre_svg(0, 0, ancho_nombre, "none")
    w = max(ew, ancho_nombre)
    sep = eh * 0.10
    texto, _ = nombre_svg((w - ancho_nombre) / 2, eh + sep, ancho_nombre, colores["texto"])
    return svg(w, eh + sep + alto_nombre, emblema((w - ew) / 2, 0, colores, "c" + sufijo) + texto)


def isotipo(colores, sufijo):
    ew, eh = emblema_caja()
    return svg(ew, eh, emblema(0, 0, colores, "c" + sufijo))


def main():
    SALIDA.mkdir(exist_ok=True)
    # Alturas en px de los PNG, las mismas que los logos actuales de la app.
    piezas = {"isotipo": (isotipo, 128), "logo-horizontal": (horizontal, 160), "logo-vertical": (vertical, 240)}
    for base, (fn, alto_png) in piezas.items():
        for sufijo, colores in VARIANTES.items():
            nombre_f = f"{base}{sufijo}"
            contenido = fn(colores, sufijo)
            (SALIDA / f"{nombre_f}.svg").write_text(contenido)
            # PNG a doble resolución para pantallas retina.
            cairosvg.svg2png(bytestring=contenido.encode(), write_to=str(SALIDA / f"{nombre_f}.png"),
                             output_height=alto_png * 2)
            print(nombre_f)


if __name__ == "__main__":
    main()
