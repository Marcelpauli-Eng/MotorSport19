// Vídeo 1 · Dirección del taller, de principio a fin: un cliente nuevo desde el alta hasta la factura,
// con la configuración delante y el control del negocio detrás. Una toma por capítulo.
// node guion-direccion.mjs    Antes: ./reiniciar.sh semilla-jornadas.sql semilla-hoy.sql
import { grabacion, esperar } from './grabador.mjs';

const g = await grabacion({ carpeta: 'direccion', ancho: 1536, alto: 864 });
const { D, js, rotulo } = g;
const hoy = new Date().toLocaleString('sv-SE', { timeZone: 'Europe/Madrid' }).slice(0, 10);
const tk = await g.token('admin', 'admin1234');
// El «Editar» que toca de una lista de botones iguales (el n-ésimo visible).
const enesimo = (texto, n) => `[...document.querySelectorAll('button')].filter(b => b.offsetParent && b.textContent.trim() === '${texto}')[${n}]`;

await g.abrir('/entrar', { limpiar: true });

// ---------------------------------------------------------------- 1 · Puesta en marcha
await g.empezarToma();
rotulo('Acceso', 'Dirección entra con todos los permisos');
await D(`d.colocar(1000, 600); await d.e(500);`);
await D(`await d.escribir('#username', 'admin', { ms: 90 });`);
await D(`await d.escribir('#password', 'admin1234', { ms: 70 });`);
await D(`await d.clic('button[type=submit]', { despues: 300 }); await d.esperarA('texto:Trabajo en curso'); await d.e(1800);`);

rotulo('Ajustes · Empresa', 'Los datos fiscales que salen en cada factura');
await D(`await d.clic('a[href="/ajustes"]', { despues: 300 }); await d.esperarA('#razonSocial'); await d.e(900);`);
await D(`await d.mover('#razonSocial', 800); await d.e(700); await d.mover('#nifTaller', 700); await d.e(900);`);
rotulo('Precios y capacidad', 'Precio de la hora, IVA por defecto y horas de trabajo al día');
await D(`await d.mover('#tarifaHora', 900); await d.e(900); await d.mover('#capacidad', 800); await d.e(1300);`);

rotulo('Series de facturación', 'Numeración propia para ordinarias y rectificativas, año a año');
await D(`await d.clic('texto:Series de facturación', { despues: 1800 });`);

rotulo('Roles y permisos', '48 permisos para decidir quién ve y quién hace qué');
await D(`await d.clic('texto:Roles y permisos', { despues: 1500 });`);
await D(`await d.clic(${enesimo('Editar', 1)}, { despues: 300 }); await d.esperarA('texto:Editar el rol Taller'); await d.e(1200);`);
rotulo('El rol del técnico', 'Ve sus órdenes y apunta trabajo. Precios e importes, no');
await D(`let c = d.buscar('texto:Ver importes y precios'); while (c.parentElement && c.getBoundingClientRect().height < 180) c = c.parentElement; await d.zoom(c, 1.8, 900); await d.mover('texto:Ver importes y precios', 700); await d.e(2400); await d.sinZoom(700);`);
await D(`await d.clic('texto:Cancelar', { despues: 900 });`);

rotulo('Usuarios', 'El equipo, cada uno con su rol. Un usuario nunca se borra: se da de baja');
await D(`await d.clic('texto:Usuarios', { despues: 1400 });`);
await D(`await d.mover('texto:Nuria Sanz Belmonte', 900); await d.e(900); await d.mover('texto:Nuevo usuario', 900); await d.e(1200);`);
await g.cerrarToma();

// ---------------------------------------------------------------- 2 · Clientes y motos
await g.empezarToma();
rotulo('Nuevo cliente', 'Contacto y datos fiscales: el NIF se comprueba al darlo de alta');
await D(`await d.clic('a[href="/clientes"]', { despues: 300 }); await d.esperarA('texto:Nuevo cliente'); await d.e(900);`);
await D(`await d.clic('texto:Nuevo cliente', { despues: 700 });`);
await D(`await d.escribir('#nombre', 'Pablo', { ms: 70 }); await d.escribir('#apellidos', 'Serrano Vidal', { ms: 55 });`);
await D(`await d.escribir('#telefono', '612 345 678', { ms: 60 });`);
await D(`await d.elegir('#tipoDoc', 'NIF'); await d.escribir('#documento', '48291536T', { ms: 70 });`);
await D(`await d.escribir('#direccion', 'Calle de Colón 12', { ms: 50 });`);
rotulo('Código postal', 'Con el código postal, la ciudad y la provincia salen solas');
await D(`await d.escribir('#cp', '46001', { ms: 110 }); document.getElementById('cp').dispatchEvent(new Event('blur')); await d.e(1600);`);
await D(`await d.mover('#ciudad', 700); await d.e(1200);`);
await D(`await d.clic('texto:Dar de alta', { despues: 1500 });`);
await D(`await d.clic('texto:Pablo Serrano Vidal', { despues: 300 }); await d.esperarA('texto:Datos fiscales'); await d.e(1800);`);

rotulo('Nueva moto', 'Cada moto con su propietario, sus kilómetros y su bastidor');
await D(`await d.clic('a[href="/motos"]', { despues: 300 }); await d.esperarA('texto:Nueva moto'); await d.e(900);`);
await D(`await d.clic('texto:Nueva moto', { despues: 700 });`);
await D(`await d.elegir('#cliente', 'Pablo Serrano'); await d.escribir('#matricula', '4821 LKM', { ms: 80 });`);
await D(`await d.escribir('#marca', 'Yamaha', { ms: 70 }); await d.escribir('#modelo', 'MT-09', { ms: 70 });`);
await D(`await d.escribir('#anio', '2021', { ms: 80 }); await d.escribir('#cc', '890', { ms: 80 }); await d.escribir('#km', '15400', { ms: 80 });`);
await D(`await d.escribir('#color', 'Azul', { ms: 70 }); await d.escribir('#bastidor', 'JYARN59E000012345', { ms: 30 }); await d.e(500);`);
await D(`await d.clic('texto:Dar de alta', { despues: 2200 });`);
await g.cerrarToma();

// ---------------------------------------------------------------- 3 · Agenda y recepción
await g.empezarToma();
rotulo('Agenda', 'La semana del taller y cuánto trabajo tiene cada día');
await D(`await d.clic('a[href="/agenda"]', { despues: 300 }); await d.esperarA('texto:Nueva cita'); await d.e(1600);`);
await D(`await d.mover('texto:18 / 16 h · lleno', 900); await d.e(1300);`);
rotulo('Nueva cita', 'Se aparta el hueco y se le asigna el técnico');
await D(`await d.clic('texto:Nueva cita', { despues: 700 });`);
await D(`await d.fijar('#citaFecha', '${hoy}T11:30'); await d.escribir('#citaDuracion', '2', { ms: 100 });`);
await D(`await d.elegir('#citaMoto', '4821 LKM'); await d.e(600);`);
await D(`await d.escribir('#citaMotivo', 'Revisión de los 15.000 km y pastillas delanteras', { ms: 38 });`);
await D(`await d.elegir('#citaTecnico', 'Nuria'); await d.e(500);`);
await D(`await d.clic('texto:Apuntar cita', { despues: 1800 });`);
rotulo('Llega la moto', 'La cita se convierte en orden de trabajo con un clic');
await D(`await d.clic('texto:Revisión de los 15.000 km', { despues: 900 });`);
await D(`await d.clic('texto:Ha llegado', { despues: 500 });`);
await D(`await d.escribir('#entradaKm', '15420', { ms: 110 }); await d.e(400);`);
await D(`await d.clic('texto:Abrir orden', { despues: 300 }); await d.esperarA('texto:Iniciar diagnóstico'); await d.e(1500);`);
const idOrden = (await js('location.pathname')).split('/')[2];
await g.cerrarToma();

// ---------------------------------------------------------------- 4 · Orden y presupuesto
await g.empezarToma();
rotulo('Orden de trabajo', 'Con su técnico asignado y el recorrido completo, paso a paso');
await D(`await d.zoom('section.pasos-tarjeta', 1.3, 1000); await d.e(2200); await d.sinZoom(800);`);
rotulo('Diagnóstico', 'Lo escribe el técnico desde la tablet, o dirección desde aquí');
await D(`await d.clic('texto:Iniciar diagnóstico', { despues: 1200 });`);
await D(`await d.clic('texto:Escribir diagnóstico', { despues: 500 });`);
await D(`await d.escribir('textarea', 'Revisión de 15.000 km. Pastillas delanteras al límite: hay que cambiarlas.', { ms: 34 }); await d.e(300);`);
await D(`await d.clic('texto:Guardar', { despues: 1300 });`);
rotulo('Presupuesto', 'Plantillas de trabajo: mano de obra y piezas de un solo clic');
await D(`await d.clic('texto:Crear presupuesto', { despues: 300 }); await d.esperarA('select[aria-label="Plantilla a volcar en el presupuesto"]'); await d.e(900);`);
await D(`await d.elegir('select[aria-label="Plantilla a volcar en el presupuesto"]', 'Cambio de aceite'); await d.clic('texto:Añadir al presupuesto', { despues: 1400 });`);
await D(`await d.elegir('select[aria-label="Plantilla a volcar en el presupuesto"]', 'pastillas'); await d.clic('texto:Añadir al presupuesto', { despues: 1400 });`);
await D(`await d.clic('texto:Materiales', { despues: 1500 });`);
rotulo('Descuentos', 'Descuento general y los totales, al céntimo');
await D(`await d.escribir('input[aria-label="Descuento general en porcentaje"]', '5', { ms: 150 }); document.activeElement.blur(); await d.e(1200);`);
await D(`await d.zoom('texto:TOTAL €', 1.6, 900); await d.e(2000); await d.sinZoom(700);`);
rotulo('Presupuesto en PDF', 'En PDF o por WhatsApp, para que el cliente lo apruebe');
const pdfPresu = await g.pdfComoImagen(`/api/ordenes/${idOrden}/presupuesto/pdf`, tk);
await D(`await d.clic('texto:Presupuesto en PDF', { despues: 200 });`);
await js(`window.__demo.pdf(${JSON.stringify(pdfPresu)}, 'Presupuesto · Yamaha MT-09 4821 LKM.pdf')`);
await esperar(2000);
await D(`await d.pdfDesplazar(330, 2000); await d.e(1300); await d.cerrarPdf();`);
await D(`await d.mover('texto:Enviar por WhatsApp', 800); await d.e(1300);`);
rotulo('Aprobación', 'El cliente dice que sí y queda apuntado quién y cuándo');
await D(`await d.clic('texto:Volver a la orden', { despues: 300 }); await d.esperarA('texto:Pasar a presupuestada'); await d.e(700);`);
await D(`await d.clic('texto:Pasar a presupuestada', { despues: 1300 });`);
await D(`await d.clic('texto:El cliente aprueba', { despues: 1300, dialogos: [{ titulo: 'Aprobación del presupuesto', texto: '¿Quién aprueba el presupuesto?', entrada: 'Pablo Serrano Vidal' }], respuestas: ['Pablo Serrano Vidal'] });`);
rotulo('Reparación', 'Al entrar en reparación, las piezas salen solas del almacén');
await D(`await d.clic('texto:Entrar en reparación', { despues: 2000 });`);
await D(`await d.clic('texto:Marcar lista para entregar', { despues: 1600 });`);
await g.cerrarToma();

// ---------------------------------------------------------------- 5 · Facturación
await g.empezarToma();
rotulo('Factura', 'Sale de la orden, sin volver a escribir nada');
await D(`await d.clic('texto:Emitir factura', { despues: 300, confirmar: { titulo: 'Emitir factura', texto: 'Se emitirá una factura en la serie A. Una factura emitida no se puede modificar. ¿Continuar?' } }); await d.esperarA('texto:Huella de esta factura'); await d.e(1500);`);
const idFactura = (await js('location.pathname')).split('/')[2];
rotulo('Factura sellada', 'Numeración sin huecos y una huella que impide alterarla');
await D(`await d.zoom('section.sello', 1.3, 900); await d.e(2400); await d.sinZoom(700);`);
await D(`await d.mover('texto:Conceptos', 800); await d.desplazar(520, 1300); await d.e(1800); await d.desplazar(0, 900);`);
rotulo('PDF de la factura', 'Lista para imprimir o mandar al cliente');
const pdfFactura = await g.pdfComoImagen(`/api/facturas/${idFactura}/pdf`, tk);
await D(`await d.clic('texto:Ver PDF', { despues: 200 });`);
await js(`window.__demo.pdf(${JSON.stringify(pdfFactura)}, 'Factura · Pablo Serrano Vidal.pdf')`);
await esperar(2000);
await D(`await d.pdfDesplazar(420, 2200); await d.e(1400); await d.cerrarPdf();`);
rotulo('Rectificativas', '¿Un error? Se corrige con una rectificativa y la original no se toca');
await D(`await d.clic('texto:Rectificar', { despues: 1200 });`);
await D(`const q = [...document.querySelectorAll('input[aria-label="Cantidad"]')][1]; await d.escribir(q, '2', { ms: 120 }); await d.e(900);`);
await D(`await d.escribir('#motivoRect', 'Se cobró un litro de aceite de más', { ms: 38 }); await d.e(500);`);
await D(`await d.mover('texto:Emitir la corrección', 900); await d.e(1800);`);
await D(`await d.clic('texto:Cancelar', { despues: 800 });`);
rotulo('Libro de facturas', 'Todo listo para la gestoría, y la cadena de facturas verificada');
await D(`await d.clic('a[href="/facturas"]', { despues: 300 }); await d.esperarA('texto:Verificar integridad'); await d.e(1200);`);
await D(`await d.mover('texto:Descargar CSV para la gestoría', 900); await d.e(900);`);
await D(`await d.clic('texto:Verificar integridad', { despues: 2600 });`);
await g.cerrarToma();

// ---------------------------------------------------------------- 6 · Almacén
await g.empezarToma();
rotulo('Almacén', 'Existencias al día, con su ubicación y su mínimo');
await D(`await d.clic('a[href="/inventario"]', { despues: 300 }); await d.esperarA('#buscar'); await d.e(1500);`);
await D(`await d.desplazar(300, 1300); await d.e(1200); await d.desplazar(0, 900);`);
rotulo('Entrada de mercancía', 'Llega el pedido: se registra con su albarán y el stock se actualiza');
await D(`await d.escribir('#buscar', 'ESP-RET', { ms: 90 }); await d.clic('texto:Buscar', { despues: 1400 });`);
await D(`const fila = [...document.querySelectorAll('tr')].find(r => r.textContent.includes('ESP-RET-DER'));
         const b = [...fila.querySelectorAll('button')].find(x => x.textContent.trim() === 'Entrada');
         await d.clic(b, { despues: 1800, respuestas: ['4', 'ALB-2026-0918'], dialogos: [
           { titulo: 'Entrada de mercancía', texto: 'Unidades que entran de ESP-RET-DER:', entrada: '4' },
           { titulo: 'Entrada de mercancía', texto: 'Albarán o factura del proveedor (opcional):', entrada: 'ALB-2026-0918' }] });`);
rotulo('Reposición', 'Lo que está por debajo del mínimo, listo para pedir');
await D(`await d.clic('texto:Reposición', { despues: 2200 });`);
rotulo('Movimientos', 'Cada entrada y salida de piezas, con quién y por qué');
await D(`await d.clic('texto:Movimientos', { despues: 2600 });`);
rotulo('Plantillas', 'Los trabajos de siempre, listos para volcarlos en un presupuesto');
await D(`await d.clic('a[href="/plantillas"]', { despues: 300 }); await d.esperarA('texto:Nueva plantilla'); await d.e(1200);`);
await D(`await d.mover('texto:Revisión 10.000 km', 900); await d.e(1500);`);
await g.cerrarToma();

// ---------------------------------------------------------------- 7 · El negocio, bajo control
await g.empezarToma();
rotulo('Registro de jornada', 'Quién está en el taller ahora y las horas de cada uno');
await D(`await d.clic('a[href="/horas"]', { despues: 300 }); await d.esperarA('texto:Horas por trabajador'); await d.e(1600);`);
await D(`await d.mover('texto:En el taller ahora', 900); await d.e(1200); await d.mover('texto:Horas por trabajador', 900); await d.desplazar(360, 1300); await d.e(2000); await d.desplazar(0, 900);`);
rotulo('Informes', 'Lo facturado, el margen y el trabajo que falta por cobrar');
await D(`await d.clic('a[href="/informes"]', { despues: 300 }); await d.esperarA('texto:Trabajo hecho sin facturar'); await d.e(1400);`);
await D(`await d.mover('texto:Margen bruto', 900); await d.e(900); await d.mover('texto:Ticket medio', 800); await d.e(700);`);
await D(`await d.desplazar(480, 1500); await d.e(2200); await d.desplazar(0, 1000);`);
rotulo('Panel del taller', 'Y cada mañana, todo el taller en una sola pantalla');
await D(`await d.clic('a[href="/panel"]', { despues: 300 }); await d.esperarA('texto:Trabajo en curso'); await d.e(1200);`);
await D(`await d.mover('texto:Trabajo en curso', 900); await d.e(2800);`);
await g.cerrarToma();

g.cerrar();
