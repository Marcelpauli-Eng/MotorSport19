// Utilidades que se inyectan en el programa para grabar la demo: cursor, clics, tecleo, zoom,
// diálogos nativos simulados y visor de PDF. Nada de esto toca la aplicación: son capas encima.
(() => {
  if (window.__demo) return;
  const e = (ms) => new Promise((r) => setTimeout(r, ms));
  const suave = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
  const cuadro = () => new Promise((r) => requestAnimationFrame(r));

  // Los diálogos nativos no salen en la grabación: se aceptan solos y se enseñan con dialogo().
  window.confirm = () => true;
  window.prompt = (_m, d) => d || 'Visto bueno';
  window.open = () => null;

  function montar() {
    if (document.getElementById('demo-capa')) return;
    const st = document.createElement('style');
    st.textContent = `
      #demo-capa { position: fixed; inset: 0; pointer-events: none; z-index: 2147483600; }
      #demo-cursor { position: fixed; left: 0; top: 0; width: 30px; height: 30px; z-index: 2147483647; pointer-events: none;
        filter: drop-shadow(0 2px 3px rgba(0,0,0,.35)); will-change: transform; }
      .demo-onda { position: fixed; width: 44px; height: 44px; margin: -22px 0 0 -22px; border-radius: 50%;
        background: rgba(255,0,109,.35); border: 2px solid #ff006d; z-index: 2147483646; pointer-events: none;
        animation: demo-onda .55s ease-out forwards; }
      @keyframes demo-onda { from { transform: scale(.2); opacity: 1 } to { transform: scale(1.4); opacity: 0 } }
      .demo-velo { position: fixed; inset: 0; z-index: 2147483640; pointer-events: none; opacity: 0; transition: opacity .35s ease; }
      .demo-velo.visible { opacity: 1 }
      .demo-dialogo { position: absolute; top: 18px; left: 50%; width: 460px; margin-left: -230px; background: #fff; border-radius: 10px;
        box-shadow: 0 12px 40px rgba(0,0,0,.28), 0 0 0 1px rgba(0,0,0,.08); padding: 22px 24px 18px; font: 14px/1.45 Inter, system-ui, sans-serif; color: #1f1f1f;
        transform: translateY(-12px); transition: transform .35s ease; }
      .demo-velo.visible .demo-dialogo { transform: none }
      .demo-dialogo h4 { margin: 0 0 10px; font-size: 15px; font-weight: 600; }
      .demo-dialogo p { margin: 0 0 20px; }
      .demo-dialogo .botones { display: flex; justify-content: flex-end; gap: 8px; }
      .demo-dialogo button { font: 600 13px Inter, system-ui, sans-serif; border-radius: 18px; padding: 8px 18px; border: 1px solid #c7c7c7; background: #fff; color: #0b57d0; }
      .demo-dialogo button.si { background: #0b57d0; border-color: #0b57d0; color: #fff; }
      .demo-pdf { background: rgba(32,33,36,.96); display: flex; flex-direction: column; align-items: center; }
      .demo-pdf .barra { width: 100%; height: 48px; flex: none; background: #323639; color: #f1f1f1; display: flex; align-items: center; padding: 0 20px;
        font: 500 14px Inter, system-ui, sans-serif; gap: 14px; box-shadow: 0 1px 4px rgba(0,0,0,.4); }
      .demo-pdf .barra span { opacity: .7; margin-left: auto; }
      .demo-pdf .hoja-marco { flex: 1; width: 100%; overflow: hidden; position: relative; }
      .demo-pdf img { position: absolute; left: 50%; top: 24px; width: 760px; margin-left: -380px; box-shadow: 0 4px 24px rgba(0,0,0,.5);
        transform: translateY(40px); transition: transform .6s cubic-bezier(.2,.8,.2,1); background: #fff; }
      .demo-velo.visible.demo-pdf img { transform: none }
    `;
    document.head.appendChild(st);
    const capa = document.createElement('div');
    capa.id = 'demo-capa';
    document.body.appendChild(capa);
    const cursor = document.createElement('div');
    cursor.id = 'demo-cursor';
    cursor.innerHTML = `<svg viewBox="0 0 30 30" width="30" height="30"><path d="M5 3 L5 24 L10.5 18.8 L14.2 27 L18 25.3 L14.3 17.3 L21.5 17.3 Z" fill="#111" stroke="#fff" stroke-width="1.8" stroke-linejoin="round"/></svg>`;
    document.body.appendChild(cursor);
    colocar(pos.x, pos.y);
  }

  const pos = { x: 720, y: 460 };
  function colocar(x, y) {
    pos.x = x; pos.y = y;
    const c = document.getElementById('demo-cursor');
    if (c) c.style.transform = `translate(${x - 5}px, ${y - 3}px)`;
  }

  function buscar(q) {
    if (q instanceof Element) return q;
    const vis = (el) => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    if (typeof q === 'string' && /^[#.\[a-z]/i.test(q) && !q.startsWith('texto:')) {
      try { const el = [...document.querySelectorAll(q)].find(vis); if (el) return el; } catch {}
    }
    const txt = String(q).replace(/^texto:/, '').toLowerCase();
    const todos = [...document.querySelectorAll('button, a, [role=button], label, th, td, li, h1, h2, h3, h4, p, strong, span, div')].filter(vis);
    const de = (el) => el.textContent.trim().replace(/\s+/g, ' ').toLowerCase();
    // Entre varios que encajan, manda el control (botón o enlace); si no hay, el más interior.
    const mejor = (lista) => lista.find((el) => el.matches('button, a, [role=button]')) ?? lista.at(-1);
    const exactos = todos.filter((el) => de(el) === txt || el.getAttribute('aria-label')?.toLowerCase() === txt);
    if (exactos.length) return mejor(exactos);
    const parciales = todos.filter((el) => de(el).includes(txt));
    const minimo = Math.min(...parciales.map((el) => el.textContent.length));
    return parciales.length ? mejor(parciales.filter((el) => el.textContent.length <= minimo + 40)) : undefined;
  }
  async function esperarA(q, ms = 15000) {
    for (let t = 0; t < ms; t += 150) { const el = buscar(q); if (el) return el; await e(150); }
    throw new Error('No aparece: ' + q);
  }

  async function mover(q, ms = 700, dx = 0, dy = 0) {
    montar();
    const el = await esperarA(q);
    const r = el.getBoundingClientRect();
    if (r.top < 70 || r.bottom > innerHeight - 20) {
      // Fuera de la vista: se desplaza con suavidad hasta dejarlo a la vista.
      await desplazar(scrollY + r.top - innerHeight * 0.4, 700);
    }
    const r2 = el.getBoundingClientRect();
    const x = r2.left + Math.min(r2.width / 2, 60) + dx, y = r2.top + r2.height / 2 + dy;
    const x0 = pos.x, y0 = pos.y;
    const dist = Math.hypot(x - x0, y - y0);
    const dur = Math.max(250, Math.min(ms, 250 + dist * 0.9));
    const t0 = performance.now();
    for (;;) {
      await cuadro();
      const k = Math.min(1, (performance.now() - t0) / dur);
      const s = suave(k);
      colocar(x0 + (x - x0) * s, y0 + (y - y0) * s);
      if (k === 1) break;
    }
    return el;
  }

  function onda() {
    const o = document.createElement('div');
    o.className = 'demo-onda';
    o.style.left = pos.x + 'px'; o.style.top = pos.y + 'px';
    document.body.appendChild(o);
    setTimeout(() => o.remove(), 700);
  }

  async function clic(q, { antes = 180, despues = 350, dx = 0, dy = 0, confirmar = null } = {}) {
    const el = await mover(q, 700, dx, dy);
    await e(antes);
    onda();
    await e(90);
    // El diálogo nativo que saldría en el navegador, pintado para que se vea en el vídeo.
    if (confirmar) await dialogo(confirmar.titulo, confirmar.texto);
    if (el.matches('input, textarea, select')) el.focus();
    el.click();
    await e(despues);
    return el;
  }

  async function escribir(q, texto, { ms = 55, borrar = true } = {}) {
    const el = await clic(q, { despues: 150 });
    if (borrar) { el.value = ''; el.dispatchEvent(new Event('input', { bubbles: true })); }
    for (const ch of texto) {
      el.value += ch;
      el.dispatchEvent(new Event('input', { bubbles: true }));
      await e(ms + Math.random() * ms * 0.6);
    }
    el.dispatchEvent(new Event('change', { bubbles: true }));
    return el;
  }

  async function elegir(q, textoOpcion) {
    const el = await clic(q, { despues: 250 });
    const op = [...el.options].find((o) => o.text.includes(textoOpcion));
    el.value = op.value;
    el.dispatchEvent(new Event('change', { bubbles: true }));
    await e(300);
    return el;
  }

  async function desplazar(y, ms = 900) {
    const y0 = scrollY, y1 = Math.max(0, Math.min(y, document.scrollingElement.scrollHeight - innerHeight));
    const t0 = performance.now();
    for (;;) {
      await cuadro();
      const k = Math.min(1, (performance.now() - t0) / ms);
      scrollTo(0, y0 + (y1 - y0) * suave(k));
      if (k === 1) break;
    }
  }

  // Acerca la cámara a un elemento transformando app-root; el cursor queda fuera y no se deforma.
  async function zoom(q, escala = 1.6, ms = 900) {
    const raiz = document.querySelector('app-root');
    raiz.style.display = 'block';  // app-root es inline por defecto y ahí transform no hace nada
    raiz.style.transition = 'none';
    raiz.style.transformOrigin = '0 0';
    const previo = raiz.style.transform;
    raiz.style.transform = 'none';
    const el = await esperarA(q);
    const r = el.getBoundingClientRect(), rr = raiz.getBoundingClientRect();
    raiz.style.transform = previo;
    // Nunca tan cerca que el elemento no quepa entero: se corta por los lados y queda feo.
    escala = Math.max(1.05, Math.min(escala, (innerWidth * 0.97) / r.width, (innerHeight * 0.92) / r.height));
    const px = r.left + r.width / 2 - rr.left, py = r.top + r.height / 2 - rr.top;
    let tx = innerWidth / 2 - rr.left - escala * px, ty = innerHeight / 2 - rr.top - escala * py;
    tx = Math.min(-rr.left, Math.max(innerWidth - rr.left - escala * rr.width, tx));
    ty = Math.min(-rr.top, Math.max(innerHeight - rr.top - escala * rr.height, ty));
    await cuadro();
    raiz.style.transition = `transform ${ms}ms cubic-bezier(.45,0,.2,1)`;
    raiz.style.transform = `translate(${tx}px, ${ty}px) scale(${escala})`;
    await e(ms);
  }
  async function sinZoom(ms = 800) {
    const raiz = document.querySelector('app-root');
    raiz.style.transition = `transform ${ms}ms cubic-bezier(.45,0,.2,1)`;
    raiz.style.transform = 'translate(0px, 0px) scale(1)';
    await e(ms);
    raiz.style.transition = ''; raiz.style.transform = '';
  }

  async function dialogo(titulo, texto, boton = 'Aceptar') {
    montar();
    const velo = document.createElement('div');
    velo.className = 'demo-velo';
    velo.style.background = 'rgba(0,0,0,.18)';
    velo.innerHTML = `<div class="demo-dialogo"><h4></h4><p></p><div class="botones"><button>Cancelar</button><button class="si"></button></div></div>`;
    velo.querySelector('h4').textContent = titulo;
    velo.querySelector('p').textContent = texto;
    velo.querySelector('.si').textContent = boton;
    document.body.appendChild(velo);
    await cuadro(); velo.classList.add('visible');
    await e(1900);
    await clic(velo.querySelector('.si'), { despues: 120 });
    velo.classList.remove('visible');
    await e(350);
    velo.remove();
  }

  let visor = null;
  async function pdf(dataUrl, nombre) {
    montar();
    visor = document.createElement('div');
    visor.className = 'demo-velo demo-pdf';
    visor.innerHTML = `<div class="barra"><strong></strong><span>1 / 1</span></div><div class="hoja-marco"><img></div>`;
    visor.querySelector('strong').textContent = nombre;
    visor.querySelector('img').src = dataUrl;
    document.body.appendChild(visor);
    await visor.querySelector('img').decode();
    await cuadro(); visor.classList.add('visible');
    await e(700);
  }
  async function pdfDesplazar(px, ms = 1800) {
    const img = visor.querySelector('img');
    img.style.transition = `transform ${ms}ms cubic-bezier(.45,0,.2,1)`;
    img.style.transform = `translateY(${-px}px)`;
    await e(ms);
  }
  async function cerrarPdf() {
    visor.classList.remove('visible');
    await e(400);
    visor.remove(); visor = null;
  }

  window.__demo = { e, montar, colocar, mover, clic, escribir, elegir, desplazar, zoom, sinZoom, dialogo, pdf, pdfDesplazar, cerrarPdf, esperarA, buscar, pos };
  if (document.body) montar(); else addEventListener('DOMContentLoaded', montar);
})();
