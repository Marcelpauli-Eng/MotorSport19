// Pasa folleto.html a PDF con Chrome sin ventana (puerto 9333), esperando fuentes e imágenes.
import { writeFileSync } from 'node:fs';
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));
const html = new URL('./folleto.html', import.meta.url).href;
const destino = await (await fetch('http://127.0.0.1:9333/json/new?about:blank', { method: 'PUT' })).json();
const ws = new WebSocket(destino.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener('open', r, { once: true }));
let id = 0; const p = new Map();
ws.addEventListener('message', (e) => { const m = JSON.parse(e.data); if (m.id && p.has(m.id)) { p.get(m.id)(m); p.delete(m.id); } });
const cdp = (method, params = {}) => new Promise((r) => { const n = ++id; p.set(n, r); ws.send(JSON.stringify({ id: n, method, params })); });
await cdp('Page.enable');
await cdp('Page.navigate', { url: html });
await esperar(1500);
const listo = await cdp('Runtime.evaluate', { awaitPromise: true, returnByValue: true, expression: `(async () => {
  await document.fonts.ready;
  await Promise.all([...document.images].map(i => i.complete ? 0 : new Promise(r => { i.onload = i.onerror = r; })));
  const rotas = [...document.images].filter(i => !i.naturalWidth).map(i => i.getAttribute('src'));
  const fuentes = [...document.fonts].filter(f => f.status === 'loaded').map(f => f.family + ' ' + f.weight);
  const desbordes = [...document.querySelectorAll('.pagina')].map((pg, n) => {
    const limite = pg.getBoundingClientRect().bottom - 17 * 3.7795;
    const fuera = [...pg.querySelectorAll('*')].filter(e => !e.closest('.pie') && !e.closest('.portada__ventana, .portada__tablet, .portada__movil, .portada__raya') && e.getBoundingClientRect().bottom > limite + 1 && getComputedStyle(e).position !== 'absolute');
    return fuera.length ? (n + 1) + ':' + fuera.slice(0, 2).map(e => e.className || e.tagName).join('|') : null;
  }).filter(Boolean);
  return { rotas, fuentes: [...new Set(fuentes)], desbordes };
})()` });
console.log(JSON.stringify(listo.result.result.value));
const pdf = await cdp('Page.printToPDF', { printBackground: true, preferCSSPageSize: true, marginTop: 0, marginBottom: 0, marginLeft: 0, marginRight: 0 });
const salida = new URL('../MotorSport19-Folleto.pdf', import.meta.url);
writeFileSync(salida, Buffer.from(pdf.result.data, 'base64'));
console.log('PDF', salida.pathname);
ws.close();
