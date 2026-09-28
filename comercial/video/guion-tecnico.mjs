// Vídeo 2 · La vista del técnico, en una tablet apaisada (1194×834). node guion-tecnico.mjs
// Antes: ./reiniciar.sh semilla-jornadas.sql   (Nuria sin fichar hoy, para enseñar el fichaje)
import { grabacion, esperar } from './grabador.mjs';

const g = await grabacion({ carpeta: 'tecnico', ancho: 1194, alto: 834, tactil: true });
const { D, js } = g;
const TOTAL = 11;
let n = 0;
const paso = (ceja, titulo, texto) => g.rotulo(ceja, texto, { titulo, paso: ++n, total: TOTAL });
const ordenes = (id) => `a[href="/ordenes/${id}"]`;

await g.abrir('/entrar', { limpiar: true });
await g.empezarToma();

paso('Acceso', 'Cada técnico, su usuario', 'Entra desde la tablet del taller con su nombre y su contraseña.');
await D(`d.colocar(700, 560); await d.e(900);`);
await D(`await d.escribir('#username', 'nsanz', { ms: 110 });`);
await D(`await d.escribir('#password', 'tecnico1234', { ms: 80 });`);
await D(`await d.clic('button[type=submit]', { despues: 300 }); await d.esperarA('texto:Empezar jornada'); await d.e(1200);`);

paso('Registro de jornada', 'Ficha al empezar', 'Sin jornada abierta no se trabaja. La hora de entrada queda guardada cuatro años.');
await D(`await d.mover('texto:Empezar jornada', 900); await d.e(1800);`);
await D(`await d.clic('texto:Empezar jornada', { despues: 300 }); await d.esperarA('texto:Trabajo en curso'); await d.e(1500);`);

paso('Su panel', 'Solo su trabajo', 'Ve las órdenes que tiene asignadas y las piezas que faltan. El reloj de la jornada, siempre arriba.');
await D(`await d.mover('texto:Trabajo en curso', 900); await d.e(1200);`);
await D(`await d.mover('texto:Salir', 900, -60); await d.e(1600);`);
await D(`await d.mover('texto:Piezas por reponer', 900); await d.e(1400);`);

paso('Diagnóstico', 'Escribe qué le pasa a la moto', 'El mostrador lo ve al momento para preparar el presupuesto.');
await D(`await d.clic('${ordenes(9)}', { despues: 300 }); await d.esperarA('texto:Escribir diagnóstico'); await d.e(1300);`);
await D(`await d.mover('texto:Se apaga en marcha', 900); await d.e(1400);`);
await D(`await d.clic('texto:Escribir diagnóstico', { despues: 500 });`);
await D(`await d.escribir('textarea', 'Bujía muy desgastada y electrodo sucio: falla la chispa en caliente. Se cambia la bujía.', { ms: 36 }); await d.e(400);`);
await D(`await d.clic('texto:Guardar', { despues: 1500 });`);

paso('Mano de obra', 'Apunta sus horas', 'Qué ha hecho y cuánto le ha llevado. De cobrarlo se encarga el mostrador.');
await D(`await d.clic('texto:Apuntar el trabajo', { despues: 300 }); await d.esperarA('texto:Trabajo a realizar'); await d.e(1200);`);
await D(`await d.clic('texto:Añadir mano de obra', { despues: 600 });`);
await D(`await d.escribir('#descTrabajo', 'Diagnóstico de encendido y cambio de bujía', { ms: 40 });`);
await D(`await d.escribir('#horas', '1', { ms: 120 }); await d.e(400);`);
await D(`await d.clic('texto:Añadir', { despues: 1600 });`);

paso('Material', 'Y las piezas que monta', 'Las elige del almacén. Ni precios ni importes: eso no llega a su pantalla.');
await D(`await d.clic('texto:Materiales', { despues: 800 });`);
await D(`await d.clic('texto:Añadir material', { despues: 700 });`);
await D(`await d.elegir('#familia', 'Eléctrico'); await d.e(500);`);
await D(`await d.elegir('#pieza', 'BUJ-CR8E'); await d.e(600);`);
await D(`await d.clic([...document.querySelectorAll('button')].find(b => b.offsetParent && b.textContent.trim() === 'Añadir'), { despues: 1600 });`);
await D(`await d.zoom('table', 1.35, 900); await d.e(2400); await d.sinZoom(700);`);

paso('Cada uno, lo suyo', 'El presupuesto, en el mostrador', 'Lo que apunta le llega al mostrador, que presupuesta y cobra. Sin permiso, ese botón no se pulsa.');
await D(`await d.clic('texto:Volver a la orden', { despues: 300 }); await d.esperarA('texto:Pasar a presupuestada'); await d.e(900);`);
await D(`await d.zoom('section.pasos-tarjeta', 1.3, 900); await d.mover('texto:Pasar a presupuestada', 900); await d.e(2600); await d.sinZoom(700);`);

paso('Reparación', 'El cliente ha dicho que sí', 'Entra en reparación y las piezas se descuentan solas del almacén.');
await D(`await d.clic('a[href="/panel"]', { despues: 300 }); await d.esperarA('${ordenes(10)}'); await d.e(900);`);
await D(`await d.clic('${ordenes(10)}', { despues: 300 }); await d.esperarA('texto:Entrar en reparación'); await d.e(1200);`);
await D(`await d.clic('texto:Entrar en reparación', { despues: 2200 });`);

paso('Terminada', 'Moto lista para entregar', 'El mostrador se entera al momento, sin tener que ir al elevador a preguntar.');
await D(`await d.clic('a[href="/panel"]', { despues: 300 }); await d.esperarA('${ordenes(6)}'); await d.e(900);`);
await D(`await d.clic('${ordenes(6)}', { despues: 300 }); await d.esperarA('texto:Marcar lista para entregar'); await d.e(1200);`);
await D(`await d.clic('texto:Marcar lista para entregar', { despues: 2200 });`);

paso('Consultas', 'Historial y almacén', 'Todo lo que se le ha hecho a la moto, y qué piezas quedan y dónde están.');
await D(`await d.clic('a[href^="/motos/"]', { despues: 300 }); await d.esperarA('texto:Historial de intervenciones'); await d.e(1000);`);
await D(`await d.mover('texto:Historial de intervenciones', 900); await d.desplazar(330, 1400); await d.e(1600); await d.desplazar(0, 900);`);
await D(`await d.clic('a[href="/inventario"]', { despues: 300 }); await d.esperarA('#buscar'); await d.e(900);`);
await D(`await d.escribir('#buscar', 'pastillas', { ms: 90 }); await d.clic('texto:Buscar', { despues: 1800 });`);

paso('Fin de la jornada', 'Ficha la salida', 'Un toque en «Salir» y la jornada queda cerrada con su hora.');
await D(`await d.clic('texto:Salir', { despues: 300, confirmar: { titulo: 'Terminar la jornada', texto: '¿Terminar la jornada? Se registrará ahora la hora de salida.' } });`);
await esperar(3000);

await g.cerrarToma();
g.cerrar();
