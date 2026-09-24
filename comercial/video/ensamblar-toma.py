# Convierte los fotogramas del screencast (llegan a ritmo variable) en un vídeo a 30 fps constantes:
# para cada instante k/30 se usa el último fotograma recibido. Así los rótulos cuadran al segundo.
# Uso: python3 ensamblar-toma.py tomas/app salida.mp4
import bisect, json, os, subprocess, sys

carpeta, salida = sys.argv[1], sys.argv[2]
datos = json.load(open(os.path.join(carpeta, 'rotulos.json')))
fs, fin = datos['fotogramas'], datos['fin']
tiempos = [f['t'] for f in fs]
proc = subprocess.Popen(['ffmpeg', '-loglevel', 'error', '-y', '-f', 'image2pipe', '-framerate', '30', '-c:v', 'mjpeg', '-i', '-',
                         '-vf', 'format=yuv420p', '-c:v', 'libx264', '-crf', '16', '-preset', 'medium', '-r', '30', salida],
                        stdin=subprocess.PIPE)
cache = {}
for k in range(int(fin * 30)):
    i = max(0, bisect.bisect_right(tiempos, k / 30) - 1)
    if i not in cache:
        cache.clear()
        cache[i] = open(os.path.join(carpeta, fs[i]['nombre']), 'rb').read()
    proc.stdin.write(cache[i])
proc.stdin.close()
if proc.wait():
    sys.exit('ffmpeg ha fallado')
print(salida, f'{fin:.1f}s')
