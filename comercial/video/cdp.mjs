// Cliente mínimo del protocolo DevTools, igual que comercial/folleto/capturar.mjs.
export const WEB = 'http://localhost:4340';
export const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

export async function conectar(puerto = 9335) {
  const destino = await (await fetch(`http://127.0.0.1:${puerto}/json/new?about:blank`, { method: 'PUT' })).json();
  const ws = new WebSocket(destino.webSocketDebuggerUrl);
  await new Promise((r) => ws.addEventListener('open', r, { once: true }));
  let id = 0;
  const pendientes = new Map();
  const oyentes = new Map();
  ws.addEventListener('message', (e) => {
    const m = JSON.parse(e.data);
    if (m.id && pendientes.has(m.id)) { pendientes.get(m.id)(m); pendientes.delete(m.id); }
    else if (m.method && oyentes.has(m.method)) oyentes.get(m.method)(m.params);
  });
  const cdp = (method, params = {}) => new Promise((r) => { const n = ++id; pendientes.set(n, r); ws.send(JSON.stringify({ id: n, method, params })); });
  const js = async (expression) => {
    const r = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (r.result?.exceptionDetails) throw new Error(JSON.stringify(r.result.exceptionDetails).slice(0, 600));
    return r.result?.result?.value;
  };
  await cdp('Page.enable');
  await cdp('Runtime.enable');
  return { cdp, js, ws, on: (ev, fn) => oyentes.set(ev, fn) };
}

export async function entrar(t, usuario = 'admin', clave = 'admin1234') {
  await t.cdp('Page.navigate', { url: WEB + '/entrar' });
  await esperar(1500);
  const ok = await t.js(`(async () => {
    localStorage.clear();
    const r = await fetch('/api/auth/login', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({username:'${usuario}', password:'${clave}'})});
    if (!r.ok) return r.status;
    const j = await r.json();
    localStorage.setItem('motorsport19.token', j.token);
    localStorage.setItem('motorsport19.usuario', JSON.stringify(j.usuario));
    return 'ok';
  })()`);
  if (ok !== 'ok') throw new Error('No se pudo entrar como ' + usuario + ': ' + ok);
}

export async function ir(t, ruta) {
  await t.cdp('Page.navigate', { url: WEB + ruta });
  await esperar(1200);
  for (let i = 0; i < 50 && (await t.js("!!document.querySelector('app-cargando') || /Cargando/.test(document.querySelector('main')?.innerText ?? 'Cargando')")); i++) await esperar(200);
  await esperar(300);
}
