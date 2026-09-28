# Monta los vídeos a partir de las tomas grabadas (guion-*.mjs) y las escenas de escenas.html.
#
# Todo desde comercial/video/, con Docker, Chrome, Node y ffmpeg (brew install ffmpeg poppler):
#   "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --remote-debugging-port=9335 \
#       --user-data-dir="$PWD/perfil-chrome" --hide-scrollbars --force-color-profile=srgb about:blank &
#   ./reiniciar.sh semilla-jornadas.sql semilla-hoy.sql && node guion-direccion.mjs   # demo aparte en :4340
#   ./reiniciar.sh semilla-jornadas.sql && node guion-tecnico.mjs                     # la base real no se toca
#
#   python3 montar.py tecnico      → MotorSport19-tecnico.mp4 (+ versión para WhatsApp)
#   python3 montar.py direccion    → MotorSport19-direccion.mp4
#   python3 montar.py demo         → MotorSport19-demo.mp4 (el recorrido corto del primer vídeo)
#
# Contacto en el cierre (opcional): CONTACTO="Teléfono: 600 000 000|Email: hola@ejemplo.es" python3 montar.py ...
import json, os, subprocess, sys
from urllib.parse import urlencode

AQUI = os.path.dirname(os.path.abspath(__file__))
os.chdir(AQUI)
FUNDIDO = 0.5
X264 = ['-c:v', 'libx264', '-crf', '17', '-preset', 'medium', '-pix_fmt', 'yuv420p', '-r', '30']
CIERRE = ('cierre', 8, urlencode({'contacto': os.environ['CONTACTO']}) if os.environ.get('CONTACTO') else '')

CAPITULOS_DIRECCION = [
    ('Puesta en marcha', 'Datos del taller, series de facturas, permisos y usuarios.'),
    ('Clientes y motos', 'Fichas completas, con el NIF comprobado y la ciudad por código postal.'),
    ('Agenda y recepción', 'Se da la cita y, cuando llega la moto, se abre la orden.'),
    ('Orden y presupuesto', 'Del diagnóstico al visto bueno del cliente.'),
    ('Facturación', 'Facturas selladas, PDF, rectificativas y la gestoría.'),
    ('Almacén', 'Existencias al día, entradas de mercancía y plantillas de trabajo.'),
    ('El negocio, bajo control', 'Registro de jornada, informes y el panel del taller.'),
]

VIDEOS = {
    'direccion': {
        'salida': 'MotorSport19-direccion.mp4',
        'segmentos': [('escena', 'intro', 7, urlencode({
            'ceja': 'Vídeo 1 · Dirección del taller', 't1': 'Todo el taller,', 't2': 'de principio a fin.',
            'sub': 'Lo que puede hacer la dirección con MotorSport19: de configurar el taller a leer los números del mes.',
            'pasos': 'Puesta en marcha|Clientes|Agenda|Orden|Factura|Almacén|Informes'}))]
        + [s for i, (titulo, sub) in enumerate(CAPITULOS_DIRECCION, 1) for s in (
            ('escena', 'capitulo', 3.2, urlencode({'n': f'{i:02d}', 'ceja': f'Capítulo {i}', 'titulo': titulo, 'sub': sub})),
            ('toma', f'tomas/direccion/{i:02d}', 'ventana'))]
        + [('escena', *CIERRE)],
    },
    'tecnico': {
        'salida': 'MotorSport19-tecnico.mp4',
        'segmentos': [
            ('escena', 'intro', 7, urlencode({
                'ceja': 'Vídeo 2 · La vista del técnico', 't1': 'El taller,', 't2': 'desde la tablet.',
                'sub': 'El día del técnico con MotorSport19: fichar, diagnosticar, apuntar horas y piezas y dejar la moto lista. Sin ver un solo precio.',
                'pasos': 'Fichar|Diagnosticar|Apuntar trabajo|Reparar|Terminar|Salir'})),
            ('toma', 'tomas/tecnico/01', 'tablet'),
            ('escena', *CIERRE),
        ],
    },
    'demo': {
        'salida': 'MotorSport19-demo.mp4',
        'segmentos': [('escena', 'intro', 6.5, ''), ('toma', 'tomas/app', 'ventana'), ('escena', 'puestos', 9.5, ''), ('escena', *CIERRE)],
    },
}
MARCOS = {  # fondo, posición de la toma y plantilla de rótulo
    'ventana': ('fondo', (192, 64), 'rotulo'),
    'tablet': ('fondo-tablet', (638, 123), 'lateral'),
}


def ff(*args):
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', *args], check=True)


def node(*args):
    subprocess.run(['node', 'renderizar.mjs', *args], check=True, stdout=subprocess.DEVNULL)


def duracion(ruta):
    return float(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', ruta]))


def escena(dest, nombre, segundos, consulta):
    carpeta = dest + '-fotogramas'
    node('anim', nombre, str(segundos), carpeta, consulta)
    ff('-framerate', '30', '-i', f'{carpeta}/f%05d.jpg', *X264, dest)


def toma(dest, carpeta, marco):
    fondo, (x, y), plantilla = MARCOS[marco]
    png_fondo = f'piezas/{fondo}.png'
    node('png', fondo, png_fondo)
    base = dest.replace('.mp4', '')
    subprocess.run(['python3', 'ensamblar-toma.py', carpeta, base + '-bruto.mp4'], check=True, stdout=subprocess.DEVNULL)
    node('rotulos', f'{carpeta}/rotulos.json', base + '-rotulos', plantilla)
    datos = json.load(open(f'{carpeta}/rotulos.json'))
    fin, rotulos = datos['fin'], datos['rotulos']

    # Ventana (o tablet) + rótulos con fundido, cada uno hasta que entra el siguiente
    entradas = ['-loop', '1', '-framerate', '30', '-t', f'{fin:.3f}', '-i', png_fondo, '-i', base + '-bruto.mp4']
    filtros = [f'[0][1]overlay={x}:{y}:shortest=1[v0]']
    for i, r in enumerate(rotulos):
        ini = max(r['t'] - 0.1, 0)
        fin_r = (rotulos[i + 1]['t'] - 0.15) if i + 1 < len(rotulos) else fin
        dur = max(fin_r - ini, 0.8)
        entradas += ['-loop', '1', '-framerate', '30', '-t', f'{dur:.3f}', '-i', f'{base}-rotulos/r{i:02d}.png']
        filtros.append(f'[{i + 2}]format=rgba,fade=t=in:st=0:d=0.35:alpha=1,fade=t=out:st={dur - 0.3:.3f}:d=0.3:alpha=1,'
                       f'setpts=PTS-STARTPTS+{ini:.3f}/TB[r{i}]')
        filtros.append(f'[v{i}][r{i}]overlay=0:0:eof_action=pass[v{i + 1}]')
    ff(*entradas, '-filter_complex', ';'.join(filtros), '-map', f'[v{len(rotulos)}]', *X264, dest)


def montar(nombre):
    cfg = VIDEOS[nombre]
    os.makedirs(f'piezas/{nombre}', exist_ok=True)
    partes = []
    for i, seg in enumerate(cfg['segmentos']):
        dest = f'piezas/{nombre}/{i:02d}.mp4'
        if seg[0] == 'escena':
            escena(dest, *seg[1:])
        else:
            toma(dest, *seg[1:])
        partes.append(dest)
        print(f'  {dest}  {duracion(dest):.1f} s', flush=True)

    # Todo seguido, con fundidos encadenados
    durs = [duracion(p) for p in partes]
    cadena, previo, acumulado = [], '0', durs[0]
    for i in range(1, len(partes)):
        offset = acumulado - FUNDIDO
        cadena.append(f'[{previo}][{i}]xfade=transition=fade:duration={FUNDIDO}:offset={offset:.3f}[x{i}]')
        previo = f'x{i}'
        acumulado = offset + durs[i]
    args = [a for p in partes for a in ('-i', p)]
    salida = cfg['salida']
    ff(*args, '-filter_complex', ';'.join(cadena), '-map', f'[{previo}]', '-c:v', 'libx264', '-crf', '20', '-preset', 'slow',
       '-pix_fmt', 'yuv420p', '-r', '30', '-movflags', '+faststart', salida)
    ligera = salida.replace('.mp4', '-whatsapp.mp4')
    ff('-i', salida, '-vf', 'scale=1280:720', '-c:v', 'libx264', '-crf', '26', '-preset', 'slow', '-pix_fmt', 'yuv420p',
       '-movflags', '+faststart', ligera)
    for f in (salida, ligera):
        print(f, f'{duracion(f):.1f} s', f'{os.path.getsize(f) / 1e6:.1f} MB')


for nombre in sys.argv[1:] or ['direccion', 'tecnico']:
    montar(nombre)
