// Graba el recorrido por el programa: node grabar.mjs
// Deja los fotogramas en tomas/app/ y los rótulos (con su segundo) en tomas/app/rotulos.json.
import { mkdirSync, rmSync, writeFileSync, readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { conectar, esperar, WEB } from './cdp.mjs';

const DIR = new URL('./tomas/app/', import.meta.url).pathname;
rmSync(DIR, { recursive: true, force: true });
mkdirSync(DIR, { recursive: true });
const LIB = readFileSync(new URL('./demo-lib.js', import.meta.url), 'utf8');

// Una sola pestaña: en Chrome sin ventana las de fondo van a cámara lenta.
for (const p of await (await fetch('http://127.0.0.1:9335/json/list')).json())
  if (p.type === 'page') await fetch('http://127.0.0.1:9335/json/close/' + p.id);
const t = await conectar();
const { cdp, js } = t;
const D = (expr) => js(`(async () => { const d = window.__demo; ${expr} })()`);

await cdp('Emulation.setDeviceMetricsOverride', { width: 1536, height: 864, deviceScaleFactor: 1, mobile: false });
await cdp('Emulation.setFocusEmulationEnabled', { enabled: true });
await cdp('Emulation.setTimezoneOverride', { timezoneId: 'Europe/Madrid' });
await cdp('Page.addScriptToEvaluateOnNewDocument', { source: LIB });

// ---------- Grabación ----------
const fotogramas = [];
const rotulos = [];
let grabando = false;
let t0 = 0;
t.on('Page.screencastFrame', (f) => {
  cdp('Page.screencastFrameAck', { sessionId: f.sessionId });
  if (!grabando) return;
  const ahora = Date.now() / 1000;
  if (!t0) t0 = ahora;
  const nombre = `f${String(fotogramas.length).padStart(6, '0')}.jpg`;
  writeFileSync(DIR + nombre, Buffer.from(f.data, 'base64'));
  fotogramas.push({ nombre, t: ahora - t0 });
});
const ahora = () => Date.now() / 1000 - t0;
function rotulo(ceja, texto) { rotulos.push({ t: ahora(), ceja, texto }); console.log(ahora().toFixed(1), ceja, '·', texto); }

// PDF real de la API, pasado a imagen para enseñarlo dentro del vídeo.
const token = await (async () => {
  const r = await fetch(WEB + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: 'admin1234' }) });
  return (await r.json()).token;
})();
async function pdfComoImagen(ruta) {
  const r = await fetch(WEB + ruta, { headers: { Authorization: 'Bearer ' + token } });
  const pdf = new URL('./tomas/tmp.pdf', import.meta.url).pathname;
  writeFileSync(pdf, Buffer.from(await r.arrayBuffer()));
  execFileSync('pdftoppm', ['-png', '-r', '150', '-f', '1', '-l', '1', '-singlefile', pdf, pdf.replace('.pdf', '')]);
  return 'data:image/png;base64,' + readFileSync(pdf.replace('.pdf', '.png')).toString('base64');
}

// ---------- Guion ----------
await cdp('Page.navigate', { url: WEB + '/entrar' });
await esperar(1500);
await js('localStorage.clear()');
await cdp('Page.navigate', { url: WEB + '/entrar' });
await esperar(2500);
await cdp('Page.startScreencast', { format: 'jpeg', quality: 92, maxWidth: 1536, maxHeight: 864, everyNthFrame: 1 });
grabando = true;
await esperar(600);

// A. Entrada
rotulo('Acceso', 'Cada persona entra con su usuario y ve lo que le toca');
await D(`d.colocar(900, 620); await d.e(500);`);
await D(`await d.escribir('#username', 'admin', { ms: 90 });`);
await D(`await d.escribir('#password', 'admin1234', { ms: 70 });`);
await D(`await d.clic('button[type=submit]', { despues: 200 });`);
await esperar(2600);

// B. Panel
rotulo('Panel del taller', 'Todo lo abierto de un vistazo: órdenes, entregas y piezas que faltan');
await D(`await d.mover('texto:ÓRDENES ABIERTAS', 900); await d.e(700); await d.mover('texto:PIEZAS BAJO MÍNIMO', 900); await d.e(600);`);
await D(`await d.mover('texto:Trabajo en curso', 800); await d.e(900); await d.desplazar(420, 1400); await d.e(1600); await d.desplazar(0, 1100); await d.e(300);`);

// C. Agenda → la cita se convierte en orden
rotulo('Agenda', 'Qué entra cada día y cuánto trabajo tiene cada técnico');
await D(`await d.clic('a[href="/agenda"]'); await d.esperarA('texto:Revision de los 20.000 km'); await d.e(1400);`);
await D(`await d.mover('texto:18 / 16 h · lleno', 900); await d.e(1300);`);
rotulo('De la cita a la orden', 'Cuando llega la moto, la cita se convierte en orden de trabajo');
await D(`await d.clic('texto:Revision de los 20.000 km', { despues: 900 });`);
await D(`await d.clic('texto:Ha llegado', { despues: 500 });`);
await D(`await d.escribir('#entradaKm', '20150', { ms: 110 }); await d.e(400);`);
await D(`await d.clic('texto:Abrir orden', { despues: 300 }); await d.esperarA('texto:Iniciar diagnóstico'); await d.e(1200);`);
const idOrden = (await js('location.pathname')).split('/')[2];
console.log('orden', idOrden);

// D. Orden y diagnóstico
rotulo('Orden de trabajo', 'Cada orden sabe por dónde va, de la recepción a la entrega');
await D(`await d.zoom('section.pasos-tarjeta', 1.3, 1000); await d.e(2200); await d.sinZoom(800);`);
await D(`await d.clic('texto:Iniciar diagnóstico', { despues: 1200 });`);
await D(`await d.clic('texto:Escribir diagnóstico', { despues: 500 });`);
await D(`await d.escribir('textarea', 'Revisión de los 20.000 km. Pastillas delanteras al límite: hay que cambiarlas.', { ms: 38 }); await d.e(300);`);
await D(`await d.clic('texto:Guardar', { despues: 1300 });`);

// E. Presupuesto con plantillas y PDF
rotulo('Presupuesto', 'Mano de obra y piezas con plantillas: un clic y está hecho');
await D(`await d.clic('texto:Crear presupuesto', { despues: 300 }); await d.esperarA('select[aria-label="Plantilla a volcar en el presupuesto"]'); await d.e(1000);`);
await D(`await d.elegir('select[aria-label="Plantilla a volcar en el presupuesto"]', 'Cambio de aceite'); await d.clic('texto:Añadir al presupuesto', { despues: 1500 });`);
await D(`await d.elegir('select[aria-label="Plantilla a volcar en el presupuesto"]', 'pastillas'); await d.clic('texto:Añadir al presupuesto', { despues: 1500 });`);
await D(`await d.clic('texto:Materiales', { despues: 1600 });`);
await D(`await d.zoom('texto:TOTAL €', 1.6, 900); await d.e(1800); await d.sinZoom(700);`);
rotulo('Presupuesto en PDF', 'En PDF o por WhatsApp, para que el cliente lo apruebe');
const pdfPresu = await pdfComoImagen(`/api/ordenes/${idOrden}/presupuesto/pdf`);
await D(`await d.clic('texto:Presupuesto en PDF', { despues: 200 });`);
await js(`window.__demo.pdf(${JSON.stringify(pdfPresu)}, 'Presupuesto · OT de la Honda CB650R.pdf')`);
await esperar(2200);
await D(`await d.pdfDesplazar(330, 2000); await d.e(1500); await d.cerrarPdf();`);

// F. Aprobación y reparación
rotulo('Aprobación y reparación', 'El cliente aprueba, se repara y las piezas salen solas del almacén');
await D(`await d.clic('texto:Volver a la orden', { despues: 300 }); await d.esperarA('texto:Pasar a presupuestada'); await d.e(700);`);
await D(`await d.clic('texto:Pasar a presupuestada', { despues: 1300 });`);
await D(`await d.clic('texto:El cliente aprueba', { despues: 1300 });`);
await D(`await d.clic('texto:Entrar en reparación', { despues: 1300 });`);
await D(`await d.clic('texto:Servir material', { despues: 1800 });`);
await D(`await d.clic('texto:Marcar lista para entregar', { despues: 1500 });`);

// G. Factura
rotulo('Facturación', 'La factura sale de la orden, sin volver a escribir nada');
await D(`await d.clic('texto:Emitir factura', { despues: 300, confirmar: { titulo: 'Emitir factura', texto: 'Se emitirá una factura en la serie A. Una factura emitida no se puede modificar. ¿Continuar?' } }); await d.esperarA('texto:Huella de esta factura'); await d.e(1500);`);
const idFactura = (await js('location.pathname')).split('/')[2];
rotulo('Factura sellada', 'Numeración sin huecos y una huella que impide alterarla');
await D(`await d.zoom('section.sello', 1.3, 900); await d.e(2400); await d.sinZoom(700);`);
await D(`await d.mover('texto:Conceptos', 800); await d.desplazar(520, 1300); await d.e(2000); await d.desplazar(0, 900);`);
rotulo('PDF de la factura', 'Lista para imprimir o mandar al cliente');
const pdfFactura = await pdfComoImagen(`/api/facturas/${idFactura}/pdf`);
await D(`await d.clic('texto:Ver PDF', { despues: 200 });`);
await js(`window.__demo.pdf(${JSON.stringify(pdfFactura)}, 'Factura A-2026.pdf')`);
await esperar(2000);
await D(`await d.pdfDesplazar(420, 2200); await d.e(1600); await d.cerrarPdf();`);

// H. Rectificativa (se enseña y se cancela)
rotulo('Rectificativas', '¿Un error? Se corrige con una rectificativa y la original no se toca');
await D(`await d.clic('texto:Rectificar', { despues: 1200 });`);
await D(`const q = [...document.querySelectorAll('input[aria-label="Cantidad"]')][1]; await d.escribir(q, '2', { ms: 120 }); await d.e(900);`);
await D(`await d.escribir('#motivoRect', 'Se cobró un litro de aceite de más', { ms: 40 }); await d.e(600);`);
await D(`await d.mover('texto:Emitir la corrección', 900); await d.e(1800);`);
await D(`await d.clic('texto:Cancelar', { despues: 800 });`);

// I. Libro de facturas
rotulo('Libro de facturas', 'Todas las facturas, con exportación para la gestoría');
await D(`await d.clic('a[href="/facturas"]'); await d.esperarA('texto:Verificar integridad'); await d.e(1300);`);
await D(`await d.mover('texto:Descargar CSV para la gestoría', 900); await d.e(900);`);
await D(`await d.clic('texto:Verificar integridad', { despues: 2600 });`);

// J. Almacén
rotulo('Almacén', 'Existencias al día y aviso de lo que hay que pedir');
await D(`await d.clic('a[href="/inventario"]'); await d.esperarA('texto:Nueva pieza'); await d.e(1600);`);
await D(`await d.clic('texto:Reposición', { despues: 2400 });`);

// K. Informes
rotulo('Informes', 'Lo facturado, el margen y el trabajo que falta por cobrar');
await D(`await d.clic('a[href="/informes"]'); await d.esperarA('texto:Trabajo hecho sin facturar'); await d.e(1400);`);
await D(`await d.mover('texto:Margen bruto', 900); await d.e(900); await d.mover('texto:Trabajo hecho sin facturar', 900); await d.e(1000);`);
await D(`await d.desplazar(480, 1500); await d.e(2500);`);

await esperar(400);
grabando = false;
await cdp('Page.stopScreencast');
const fin = ahora();
writeFileSync(DIR + 'rotulos.json', JSON.stringify({ fin, rotulos, fotogramas }, null, 1));
console.log('fotogramas', fotogramas.length, 'duración', fin.toFixed(1), 's');
t.ws.close();
