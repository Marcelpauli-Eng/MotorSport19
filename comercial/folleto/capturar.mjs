// Capturas reales del programa para el folleto, con Chrome sin ventana (protocolo DevTools).
// Uso: node capturar.mjs  (con la demo en http://localhost:4330 y Chrome en el puerto 9333)
import { writeFileSync } from 'node:fs';

const WEB = 'http://localhost:4330';
const SALIDA = new URL('./capturas/', import.meta.url);
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

const destino = await (await fetch('http://127.0.0.1:9333/json/new?about:blank', { method: 'PUT' })).json();
const ws = new WebSocket(destino.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener('open', r, { once: true }));
let id = 0;
const pendientes = new Map();
ws.addEventListener('message', (e) => {
  const m = JSON.parse(e.data);
  if (m.id && pendientes.has(m.id)) { pendientes.get(m.id)(m); pendientes.delete(m.id); }
});
const cdp = (method, params = {}) => new Promise((r) => { const n = ++id; pendientes.set(n, r); ws.send(JSON.stringify({ id: n, method, params })); });
const js = async (expression) => (await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.result?.value;

async function tamano(width, height, mobile = false) {
  await cdp('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 2, mobile });
}
async function ir(ruta) {
  await cdp('Page.navigate', { url: WEB + ruta });
  await esperar(2500);
  // Hasta que no quede ningún «Cargando…» en pantalla.
  for (let i = 0; i < 20 && (await js("!!document.querySelector('app-cargando')")); i++) await esperar(300);
  await esperar(600);
}
async function foto(nombre) {
  // Fuera avisos flotantes y cursor de texto: no son parte de la pantalla.
  await js(`(() => { const s = document.getElementById('estilo-captura') || Object.assign(document.createElement('style'), {id:'estilo-captura'});
    s.textContent = '.avisos, app-avisos, .toast, .notificaciones { display:none !important } * { caret-color: transparent !important }';
    document.head.appendChild(s); document.activeElement?.blur?.(); })()`);
  const r = await cdp('Page.captureScreenshot', { format: 'png' });
  writeFileSync(new URL(nombre + '.png', SALIDA), Buffer.from(r.result.data, 'base64'));
  console.log('captura', nombre);
}

await cdp('Page.enable');
await cdp('Runtime.enable');

let quien = null;
async function entrarComo(usuario, clave) {
  await tamano(1440, 900);
  await ir('/entrar');
  const ok = await js(`(async () => {
    localStorage.clear();
    const r = await fetch('/api/auth/login', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({username:'${usuario}', password:'${clave}'})});
    if (!r.ok) return r.status;
    const j = await r.json();
    localStorage.setItem('motorsport19.token', j.token);
    localStorage.setItem('motorsport19.usuario', JSON.stringify(j.usuario));
    return 'ok';
  })()`);
  if (ok !== 'ok') throw new Error('No se pudo entrar como ' + usuario + ': ' + ok);
  quien = usuario;
}

const capturas = JSON.parse(process.argv[2] ?? '[]');
for (const c of capturas) {
  const [usuario, clave] = c.usuario ?? ['admin', 'admin1234'];
  if (quien !== usuario) await entrarComo(usuario, clave);
  await tamano(c.ancho ?? 1440, c.alto ?? 900, !!c.movil);
  // Para que el saludo diga «Buenos días» aunque se capture de noche.
  if (c.zona) await cdp('Emulation.setTimezoneOverride', { timezoneId: c.zona });
  await ir(c.ruta);
  if (c.antes) { await js(c.antes); await esperar(c.espera ?? 1500); }
  await foto(c.nombre);
}
ws.close();
