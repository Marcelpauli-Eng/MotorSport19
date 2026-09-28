// Lo común a los guiones: una pestaña de Chrome sin ventana, el screencast guardado a disco por tomas
// (una carpeta por capítulo) y los rótulos con su segundo dentro de cada toma.
import { mkdirSync, rmSync, writeFileSync, readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { conectar, esperar, WEB } from './cdp.mjs';

export { esperar };

export async function grabacion({ carpeta, ancho, alto, tactil = false }) {
  const RAIZ = new URL(`./tomas/${carpeta}/`, import.meta.url).pathname;
  rmSync(RAIZ, { recursive: true, force: true });
  const LIB = readFileSync(new URL('./demo-lib.js', import.meta.url), 'utf8');

  // Una sola pestaña: en Chrome sin ventana las de fondo van a cámara lenta.
  for (const p of await (await fetch('http://127.0.0.1:9335/json/list')).json())
    if (p.type === 'page') await fetch('http://127.0.0.1:9335/json/close/' + p.id);
  const t = await conectar();
  const { cdp, js } = t;
  await cdp('Emulation.setDeviceMetricsOverride', { width: ancho, height: alto, deviceScaleFactor: 1, mobile: false });
  await cdp('Emulation.setFocusEmulationEnabled', { enabled: true });
  await cdp('Emulation.setTimezoneOverride', { timezoneId: 'Europe/Madrid' });
  await cdp('Page.addScriptToEvaluateOnNewDocument', { source: (tactil ? 'window.__tactil = true;\n' : '') + LIB });

  let toma = null;
  let numero = 0;
  t.on('Page.screencastFrame', (f) => {
    cdp('Page.screencastFrameAck', { sessionId: f.sessionId });
    if (!toma) return;
    const ahora = Date.now() / 1000;
    if (!toma.t0) toma.t0 = ahora;
    const nombre = `f${String(toma.fotogramas.length).padStart(6, '0')}.jpg`;
    writeFileSync(toma.dir + nombre, Buffer.from(f.data, 'base64'));
    toma.fotogramas.push({ nombre, t: ahora - toma.t0 });
  });

  async function empezarToma() {
    numero++;
    const dir = `${RAIZ}${String(numero).padStart(2, '0')}/`;
    mkdirSync(dir, { recursive: true });
    toma = { dir, t0: 0, fotogramas: [], rotulos: [] };
    await cdp('Page.startScreencast', { format: 'jpeg', quality: 92, maxWidth: ancho, maxHeight: alto, everyNthFrame: 1 });
    await esperar(500);
  }
  async function cerrarToma() {
    if (!toma) return;
    await esperar(400);
    const fin = Date.now() / 1000 - toma.t0;
    await cdp('Page.stopScreencast');
    writeFileSync(toma.dir + 'rotulos.json', JSON.stringify({ fin, rotulos: toma.rotulos, fotogramas: toma.fotogramas }, null, 1));
    console.log(`toma ${numero}: ${toma.fotogramas.length} fotogramas, ${fin.toFixed(1)} s`);
    toma = null;
  }

  const D = (expr) => js(`(async () => { const d = window.__demo; ${expr} })()`);
  function rotulo(ceja, texto, extra = {}) {
    const t = Date.now() / 1000 - toma.t0;
    toma.rotulos.push({ t, ceja, texto, ...extra });
    console.log(`  ${t.toFixed(1)}  ${ceja} · ${texto}`);
  }

  async function token(usuario, clave) {
    const r = await fetch(WEB + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: usuario, password: clave }) });
    return (await r.json()).token;
  }
  // PDF real de la API, pasado a imagen para enseñarlo dentro del vídeo.
  async function pdfComoImagen(ruta, tk) {
    const r = await fetch(WEB + ruta, { headers: { Authorization: 'Bearer ' + tk } });
    const pdf = new URL('./tomas/tmp.pdf', import.meta.url).pathname;
    writeFileSync(pdf, Buffer.from(await r.arrayBuffer()));
    execFileSync('pdftoppm', ['-png', '-r', '150', '-f', '1', '-l', '1', '-singlefile', pdf, pdf.replace('.pdf', '')]);
    return 'data:image/png;base64,' + readFileSync(pdf.replace('.pdf', '.png')).toString('base64');
  }

  async function abrir(ruta, { limpiar = false } = {}) {
    await cdp('Page.navigate', { url: WEB + ruta });
    await esperar(1500);
    if (limpiar) {
      await js('localStorage.clear()');
      await cdp('Page.navigate', { url: WEB + ruta });
      await esperar(2500);
    }
  }

  return { t, cdp, js, D, rotulo, empezarToma, cerrarToma, token, pdfComoImagen, abrir, cerrar: () => t.ws.close() };
}
