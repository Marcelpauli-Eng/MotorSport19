# Monta el vídeo final: portada → recorrido por el programa (en su ventana, con rótulos) → tablet y móvil → cierre.
#
# Todo desde comercial/video/, con Docker, Chrome, Node y ffmpeg (brew install ffmpeg poppler):
#   "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --remote-debugging-port=9335 \
#       --user-data-dir="$PWD/perfil-chrome" --hide-scrollbars --force-color-profile=srgb about:blank &
#   ./reiniciar.sh                      # demo aparte en :4340 con su propia base; la real no se toca
#   node grabar.mjs                     # recorrido por el programa → tomas/app/
#   node renderizar.mjs png fondo piezas/fondo.png
#   node renderizar.mjs rotulos tomas/app/rotulos.json piezas/rotulos
#   node renderizar.mjs anim intro 6.5 piezas/intro
#   node renderizar.mjs anim puestos 9.5 piezas/puestos
#   node renderizar.mjs anim cierre 8 piezas/cierre "contacto=Teléfono: 600 000 000|Email: hola@ejemplo.es"
#   python3 montar.py [salida.mp4]
import json, os, subprocess, sys

AQUI = os.path.dirname(os.path.abspath(__file__))
os.chdir(AQUI)
SALIDA = sys.argv[1] if len(sys.argv) > 1 else 'MotorSport19-demo.mp4'
FUNDIDO = 0.6


def ff(*args):
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', *args], check=True)


def duracion(ruta):
    return float(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', ruta]))


X264 = ['-c:v', 'libx264', '-crf', '17', '-preset', 'medium', '-pix_fmt', 'yuv420p', '-r', '30']

# 1. Escenas animadas (fotogramas ya pintados por renderizar.mjs)
for escena in ('intro', 'puestos', 'cierre'):
    ff('-framerate', '30', '-i', f'piezas/{escena}/f%05d.jpg', *X264, f'piezas/{escena}.mp4')

# 2. Recorrido: fotogramas a 30 fps constantes
subprocess.run(['python3', 'ensamblar-toma.py', 'tomas/app', 'piezas/app.mp4'], check=True)
datos = json.load(open('tomas/app/rotulos.json'))
fin, rotulos = datos['fin'], datos['rotulos']

# 3. Ventana + rótulos con fundido, cada uno hasta que entra el siguiente
entradas = ['-loop', '1', '-framerate', '30', '-t', f'{fin:.3f}', '-i', 'piezas/fondo.png', '-i', 'piezas/app.mp4']
filtros = ['[0][1]overlay=192:64:shortest=1[v0]']
for i, r in enumerate(rotulos):
    ini = r['t'] + 0.15
    fin_r = (rotulos[i + 1]['t'] - 0.05) if i + 1 < len(rotulos) else fin
    dur = max(fin_r - ini, 0.8)
    entradas += ['-loop', '1', '-framerate', '30', '-t', f'{dur:.3f}', '-i', f'piezas/rotulos/r{i:02d}.png']
    k = i + 2
    filtros.append(f'[{k}]format=rgba,fade=t=in:st=0:d=0.35:alpha=1,fade=t=out:st={dur - 0.3:.3f}:d=0.3:alpha=1,'
                   f'setpts=PTS-STARTPTS+{ini:.3f}/TB[r{i}]')
    filtros.append(f'[v{i}][r{i}]overlay=0:0:eof_action=pass[v{i + 1}]')
ff(*entradas, '-filter_complex', ';'.join(filtros), '-map', f'[v{len(rotulos)}]', *X264, 'piezas/app-compuesta.mp4')

# 4. Todo seguido, con fundidos encadenados
partes = ['piezas/intro.mp4', 'piezas/app-compuesta.mp4', 'piezas/puestos.mp4', 'piezas/cierre.mp4']
durs = [duracion(p) for p in partes]
cadena, previo, acumulado = [], '0', durs[0]
for i in range(1, len(partes)):
    offset = acumulado - FUNDIDO
    cadena.append(f'[{previo}][{i}]xfade=transition=fade:duration={FUNDIDO}:offset={offset:.3f}[x{i}]')
    previo = f'x{i}'
    acumulado = offset + durs[i]
args = []
for p in partes:
    args += ['-i', p]
ff(*args, '-filter_complex', ';'.join(cadena), '-map', f'[{previo}]', '-c:v', 'libx264', '-crf', '20', '-preset', 'slow',
   '-pix_fmt', 'yuv420p', '-r', '30', '-movflags', '+faststart', SALIDA)

# 5. Versión ligera para WhatsApp
ligera = SALIDA.replace('.mp4', '-whatsapp.mp4')
ff('-i', SALIDA, '-vf', 'scale=1280:720', '-c:v', 'libx264', '-crf', '26', '-preset', 'slow', '-pix_fmt', 'yuv420p',
   '-movflags', '+faststart', ligera)
for f in (SALIDA, ligera):
    print(f, f'{duracion(f):.1f} s', f'{os.path.getsize(f) / 1e6:.1f} MB')
