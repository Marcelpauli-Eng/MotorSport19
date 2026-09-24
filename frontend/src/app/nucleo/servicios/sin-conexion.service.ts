import { HttpClient, HttpContext, HttpContextToken, HttpRequest } from '@angular/common/http';
import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { NotificacionesService } from './notificaciones.service';
import { SesionService } from './sesion.service';
import { TiempoRealService } from './tiempo-real.service';

/** Marca los reenvíos, para que si vuelven a fallar no se encolen otra vez. */
export const REINTENTO = new HttpContextToken(() => false);

/**
 * El cuerpo del error con el que termina una petición que se ha quedado en cola.
 *
 * <p>Termina como error y no fingiendo la respuesta del servidor: las pantallas
 * guardan esa respuesta como la ficha («la orden», «el movimiento») y con una
 * inventada se quedaban en blanco. Por la rama de error todas hacen ya lo
 * correcto —reactivar el botón, dejar el formulario como estaba— y el
 * interceptor de errores la reconoce para no decir que no se ha guardado.
 */
export const PENDIENTE = 'pendiente-de-envio';

/** El navegador no llegó al servidor (0), o nginx no llegó a la API (502, 503). */
export function noLlegoAlServidor(estado: number): boolean {
  return estado === 0 || estado === 502 || estado === 503;
}

export interface Pendiente {
  id: string;
  usuarioId: number;
  metodo: string;
  url: string;
  cuerpo: unknown;
}

const CLAVE_PENDIENTES = 'motorsport19.pendientes';

/**
 * Lo que no se guarda para después.
 *
 * - Facturas: el número y la fecha los pone el servidor al emitir, y una
 *   factura con fecha de mañana hecha hoy no es algo que se pueda arreglar.
 * - Fichajes: la hora la marca el servidor al recibirlos; fichar sin conexión
 *   apuntaría la hora a la que volvió la red, no la de entrada.
 * - Altas (ver {@link esAlta}).
 */
const SOLO_CON_CONEXION = ['/auth/', '/facturas', '/facturacion', '/fichajes'];

/**
 * ¿Crea algo nuevo? Un POST a la colección («/ordenes», «/clientes»...) o la
 * entrada de una cita, que abre su orden.
 *
 * <p>Lo nuevo recibe del servidor su número y su identificador, y la pantalla
 * los necesita para seguir: abrir la ficha, preparar la orden para un técnico,
 * darle una moto al cliente recién creado. Guardarlo para después dejaría todo
 * eso a medias, así que se pide conexión y no se guarda nada.
 */
export function esAlta(metodo: string, url: string): boolean {
  const ruta = url.split('?')[0].replace(/^.*\/api/, '');
  return metodo === 'POST' && (/^\/[^/]+\/?$/.test(ruta) || /^\/citas\/[^/]+\/entrada$/.test(ruta));
}

/**
 * Trabajar sin conexión.
 *
 * <p><b>Lectura</b>: cada respuesta de la API se guarda en IndexedDB. Si el
 * servidor no contesta, la pantalla se pinta con lo último que se recibió.
 *
 * <p><b>Escritura</b>: lo que se guarda sin conexión se apunta en una cola y se
 * reenvía en orden cuando vuelve la red. Cada petición lleva un identificador
 * y el servidor ignora las repetidas, así que reenviar nunca duplica nada.
 * Al aplicarse, el servidor avisa a todos los puestos como con cualquier otro
 * cambio.
 */
@Injectable({ providedIn: 'root' })
export class SinConexionService {
  private readonly http = inject(HttpClient);
  private readonly sesion = inject(SesionService);
  private readonly tiempoReal = inject(TiempoRealService);
  private readonly avisos = inject(NotificacionesService);

  readonly pendientes = signal<Pendiente[]>(leerPendientes());
  private sincronizando = false;

  constructor() {
    window.addEventListener('online', () => void this.sincronizar());

    effect(() => {
      const conectado = this.sesion.autenticado() && !this.tiempoReal.caido();
      if (conectado) untracked(() => void this.sincronizar());
    });

    // Al salir no deben quedar en este equipo datos que el siguiente no podría ver.
    effect(() => {
      if (!this.sesion.autenticado()) void respuestas('readwrite', (s) => s.clear()).catch(() => {});
    });
  }

  sePuedeGuardar(peticion: HttpRequest<unknown>): boolean {
    const cuerpo = peticion.body;
    return (
      peticion.responseType === 'json' &&
      !(cuerpo instanceof FormData || cuerpo instanceof Blob) &&
      !esAlta(peticion.method, peticion.url) &&
      !SOLO_CON_CONEXION.some((ruta) => peticion.url.includes(ruta))
    );
  }

  encolar(peticion: HttpRequest<unknown>): void {
    const usuarioId = this.sesion.usuario()?.id;
    if (usuarioId === undefined) return;

    const nueva: Pendiente = {
      id: peticion.headers.get('X-Id-Peticion')!,
      usuarioId,
      metodo: peticion.method,
      url: peticion.urlWithParams,
      cuerpo: peticion.body ?? null,
    };
    // Pulsar otra vez lo mismo mientras no hay red no lo apunta dos veces: una
    // línea de presupuesto repetida acabaría duplicada al sincronizar.
    const repetida = this.pendientes().some(
      (p) =>
        p.usuarioId === usuarioId &&
        p.metodo === nueva.metodo &&
        p.url === nueva.url &&
        JSON.stringify(p.cuerpo) === JSON.stringify(nueva.cuerpo),
    );
    if (!repetida) {
      this.pendientes.update((lista) => [...lista, nueva]);
      this.persistir();
    }
    this.avisos.info('Sin conexión: el cambio queda pendiente y se enviará solo en cuanto vuelva la red.');
  }

  /** Clave por usuario: un técnico no debe ver lo que se guardó con la sesión del jefe. */
  claveDe(peticion: HttpRequest<unknown>): string {
    return `${this.sesion.usuario()?.id ?? ''}|${peticion.urlWithParams}`;
  }

  guardarRespuesta(clave: string, cuerpo: unknown): void {
    void respuestas('readwrite', (s) => s.put(cuerpo, clave)).catch(() => {});
  }

  leerRespuesta(clave: string): Promise<unknown> {
    return respuestas('readonly', (s) => s.get(clave)).catch(() => undefined);
  }

  /** Reenvía en orden lo que se hizo sin conexión. */
  async sincronizar(): Promise<void> {
    const usuarioId = this.sesion.usuario()?.id;
    if (this.sincronizando || usuarioId === undefined) return;

    const mios = this.pendientes().filter((p) => p.usuarioId === usuarioId);
    if (!mios.length) return;

    this.sincronizando = true;
    let enviados = 0;
    try {
      for (const p of mios) {
        try {
          await firstValueFrom(
            this.http.request(p.metodo, p.url, {
              body: p.cuerpo,
              headers: { 'X-Id-Peticion': p.id },
              context: new HttpContext().set(REINTENTO, true),
            }),
          );
          enviados++;
        } catch (error) {
          const estado = (error as { status?: number }).status;
          // Sigue sin red, o hay que volver a entrar o fichar: se deja para luego.
          if (estado !== undefined && (noLlegoAlServidor(estado) || [401, 423, 504].includes(estado))) break;
          // El servidor lo rechaza (sin stock, datos que ya no valen...). El
          // interceptor de errores ya ha enseñado su motivo; reintentarlo no
          // cambiaría la respuesta.
        }
        this.pendientes.update((lista) => lista.filter((x) => x.id !== p.id));
        this.persistir();
      }
    } finally {
      this.sincronizando = false;
    }

    if (enviados) {
      this.avisos.exito(`Guardados ${enviados} cambio(s) hechos sin conexión.`);
      this.tiempoReal.cambios.next('*');
    }
  }

  private persistir(): void {
    localStorage.setItem(CLAVE_PENDIENTES, JSON.stringify(this.pendientes()));
  }
}

function leerPendientes(): Pendiente[] {
  try {
    return JSON.parse(localStorage.getItem(CLAVE_PENDIENTES) ?? '[]') as Pendiente[];
  } catch {
    return [];
  }
}

let baseDeDatos: Promise<IDBDatabase> | null = null;

/** Un único almacén clave → respuesta. Se abre la primera vez que hace falta. */
function respuestas<T>(
  modo: IDBTransactionMode,
  operacion: (almacen: IDBObjectStore) => IDBRequest,
): Promise<T> {
  baseDeDatos ??= new Promise((listo, fallo) => {
    const apertura = indexedDB.open('motorsport19', 1);
    apertura.onupgradeneeded = () => apertura.result.createObjectStore('respuestas');
    apertura.onsuccess = () => listo(apertura.result);
    apertura.onerror = () => fallo(apertura.error);
  });

  return baseDeDatos.then(
    (db) =>
      new Promise<T>((listo, fallo) => {
        const peticion = operacion(db.transaction('respuestas', modo).objectStore('respuestas'));
        peticion.onsuccess = () => listo(peticion.result as T);
        peticion.onerror = () => fallo(peticion.error);
      }),
  );
}
