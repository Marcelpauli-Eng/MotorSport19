"""Genera SportMotor-Folleto.pdf a partir del folleto original.

- Cambia la marca «MotorSport19» por «SportMotor» en el texto del folleto.
- Cambia el logo de 19 Racing Motorsport por el de SportMotor (../marca).
- Rellena los datos de contacto de la última página.
- Inserta la página de planes y precios (pagina-precios.html) antes del contacto.

Uso:  pip install pymupdf  &&  python3 generar.py
Necesita Chromium para convertir la página HTML a PDF (variable CHROMIUM si no
está en /opt/pw-browsers/chromium).
"""
import os
import subprocess
import tempfile
from pathlib import Path

import pymupdf

AQUI = Path(__file__).resolve().parent
ORIGINAL = AQUI / "original" / "MotorSport19-Folleto.pdf"
SALIDA = AQUI / "SportMotor-Folleto.pdf"
PRECIOS_HTML = AQUI / "pagina-precios.html"
LOGO = AQUI.parent / "marca" / "logo" / "logo-horizontal-claro.svg"
XREF_LOGO_ORIGINAL = 5  # el mismo PNG en la portada y en el contacto
FUENTES = AQUI / "fuentes"
CHROMIUM = os.environ.get("CHROMIUM", "/opt/pw-browsers/chromium")

MARCA = "SportMotor"
EMAIL = "marcelpaulilara@gmail.com"
TELEFONO = "634 27 63 85"

ROSA = (1, 0, 109 / 255)
ROSA_FUERTE = (217 / 255, 0, 92 / 255)
GRIS_400 = (154 / 255, 162 / 255, 177 / 255)
GRIS_500 = (109 / 255, 118 / 255, 134 / 255)
BLANCO = (1, 1, 1)

FUENTE = {
    "regular": pymupdf.Font(fontfile=str(FUENTES / "Inter-400.ttf")),
    "medio": pymupdf.Font(fontfile=str(FUENTES / "Inter-500.ttf")),
    "semi": pymupdf.Font(fontfile=str(FUENTES / "Inter-600.ttf")),
    "negrita": pymupdf.Font(fontfile=str(FUENTES / "Inter-700.ttf")),
}


def borrar(pagina, *rects):
    """Quita el texto de esas zonas sin tocar fondos ni imágenes."""
    for r in rects:
        pagina.add_redact_annot(pymupdf.Rect(r), fill=False)
    pagina.apply_redactions(
        images=pymupdf.PDF_REDACT_IMAGE_NONE,
        graphics=pymupdf.PDF_REDACT_LINE_ART_NONE,
    )


def escribir(pagina, x, y, texto, fuente, tam, color, espaciado=0.0, alinear="izq"):
    """Escribe con interletrado (las etiquetas en mayúsculas lo llevan)."""
    f = FUENTE[fuente]
    ancho = f.text_length(texto, tam) + espaciado * (len(texto) - 1)
    if alinear == "centro":
        x -= ancho / 2
    elif alinear == "der":
        x -= ancho
    tw = pymupdf.TextWriter(pagina.rect, color=color)
    if espaciado:
        for c in texto:
            tw.append((x, y), c, font=f, fontsize=tam)
            x += f.text_length(c, tam) + espaciado
    else:
        tw.append((x, y), texto, font=f, fontsize=tam)
    tw.write_text(pagina)


def pie(pagina, numero=None, color=GRIS_400):
    borrar(pagina, (42, 809.5, 209, 819))
    escribir(pagina, 42.6, 816.8, f"{MARCA} — Software de gestión para talleres", "regular", 7, color)
    if numero is not None:
        borrar(pagina, (540, 809.5, 553.5, 819))
        escribir(pagina, 552.9, 816.8, str(numero), "regular", 7, GRIS_400, alinear="der")


def pagina_precios():
    with tempfile.TemporaryDirectory() as tmp:
        pdf = Path(tmp) / "precios.pdf"
        subprocess.run(
            [CHROMIUM, "--headless", "--no-sandbox", "--disable-gpu", "--no-pdf-header-footer",
             f"--print-to-pdf={pdf}", PRECIOS_HTML.as_uri()],
            check=True, capture_output=True,
        )
        return pymupdf.open("pdf", pdf.read_bytes())


def cambiar_logo(doc):
    """Quita el logo antiguo y pone el nuevo en vectorial, alineado a la izquierda
    y con la misma altura que tenía."""
    logo = pymupdf.open(LOGO)
    logo = pymupdf.open("pdf", logo.convert_to_pdf())
    proporcion = logo[0].rect.width / logo[0].rect.height
    for pagina in (doc[0], doc[-1]):
        cajas = [i["bbox"] for i in pagina.get_image_info(xrefs=True) if i["xref"] == XREF_LOGO_ORIGINAL]
        for caja in cajas:
            x0, y0, _, y1 = caja
            pagina.show_pdf_page(pymupdf.Rect(x0, y0, x0 + (y1 - y0) * proporcion, y1), logo, 0)
        pagina.delete_image(XREF_LOGO_ORIGINAL)


def main():
    doc = pymupdf.open(ORIGINAL)
    cambiar_logo(doc)

    # Portada: antetítulo y pestaña de la captura del panel.
    p = doc[0]
    borrar(p, (42, 117.9, 121.5, 126.9), (177, 379.2, 270, 386))
    escribir(p, 42.6, 125.2, MARCA.upper(), "negrita", 8, ROSA_FUERTE, espaciado=1.07)
    escribir(p, (178.3 + 268.6) / 2, 384.8, f"{MARCA} · Panel del taller", "medio", 6.3, GRIS_400, alinear="centro")

    # Cabecera de la tabla «Antes / Con ...» de beneficios.
    p = doc[7]
    borrar(p, (368, 560.2, 460, 568.5))
    escribir(p, 368.6, 567.0, f"CON {MARCA.upper()}", "negrita", 7.4, ROSA, espaciado=0.71)

    # Pie de página de todas las páginas interiores (5 y 6 lo llevan más oscuro).
    for i in range(1, len(doc) - 1):
        pie(doc[i], color=GRIS_500 if i in (4, 5) else GRIS_400)

    # Contacto: dos filas (email y teléfono) centradas en la columna de tres.
    p = doc[-1]
    borrar(p, (369, 603, 440, 611.3), (369, 616.2, 440, 627.7), (369, 641.4, 440, 649.6),
           (369, 653.7, 440, 665.2), (369, 679.6, 440, 687.8), (369, 692, 440, 703.5))
    salto = 38.2 / 2
    escribir(p, 369.4, 609.8 + salto, "EMAIL", "negrita", 6.8, ROSA, espaciado=0.81)
    escribir(p, 369.4, 625.5 + salto, EMAIL, "semi", 10, BLANCO)
    escribir(p, 369.4, 648.0 + salto, "TELÉFONO", "negrita", 6.8, ROSA, espaciado=0.81)
    escribir(p, 369.4, 663.0 + salto, TELEFONO, "semi", 10, BLANCO)
    pie(p, numero=len(doc) + 1)

    doc.insert_pdf(pagina_precios(), start_at=len(doc) - 1)
    doc.set_metadata({**doc.metadata, "title": f"{MARCA} · Folleto comercial"})
    doc.save(SALIDA, garbage=3, deflate=True)
    print(f"{SALIDA.name}: {len(doc)} páginas")


if __name__ == "__main__":
    main()
