import { Injectable, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SesionService } from './sesion.service';

/** Espera antes de volver a conectar cuando el servidor no responde. */
const MS_REINTENTO = 3000;

/**
 * Aviso de que otro puesto ha cambiado algo.
 *
 * <p>El servidor manda solo el nombre de lo que se ha tocado («piezas»,
 * «usuarios»…), nunca los datos: cada pantalla los vuelve a pedir con su
 * token, y así cada uno sigue viendo solo lo que su rol le deja ver.
 *
 * <p>Se usa `fetch` y no `EventSource` porque este no permite mandar la
 * cabecera Authorization.
 */
@Injectable({ providedIn: 'root' })
export class TiempoRealService {
  private readonly sesion = inject(SesionService);

  /** Distingue esta pestaña: sus propios cambios no le llegan de vuelta. */
  readonly idPestana = Math.random().toString(36).slice(2) + Date.now().toString(36);

  /** Nombre de lo que ha cambiado; «*» si hay que suponer que todo. */
  readonly cambios = new Subject<string>();

  /** El servidor no contesta: se trabaja con los últimos datos recibidos. */
  readonly caido = signal(false);

  private corte: AbortController | null = null;

  constructor() {
    effect(() => {
      const token = this.sesion.token();
      this.corte?.abort();
      this.corte = null;
      if (token) {
        this.corte = new AbortController();
        void this.escuchar(token, this.corte.signal);
      }
    });
  }

  private async escuchar(token: string, corte: AbortSignal): Promise<void> {
    let primeraVez = true;

    while (!corte.aborted) {
      try {
        const respuesta = await fetch(`${environment.urlApi}/eventos`, {
          headers: { Authorization: `Bearer ${token}` },
          signal: corte,
        });
        // Token caducado o sesión cerrada desde otro puesto: no es que el servidor
        // no conteste. Esto no pasa por los interceptores, así que se pregunta a
        // /auth/yo, que sí pasa, y su 401 cierra la sesión como en el resto del
        // programa. Antes se pintaba «sin conexión» y se reintentaba sin fin.
        if (respuesta.status === 401) {
          await firstValueFrom(this.sesion.revalidar()).catch(() => undefined);
          await new Promise((listo) => setTimeout(listo, MS_REINTENTO));
          continue;
        }
        if (!respuesta.ok || !respuesta.body) throw new Error(String(respuesta.status));

        this.caido.set(false);
        // Lo que cambió mientras estaba cortado no llegó: se recarga todo una vez.
        if (!primeraVez) this.cambios.next('*');
        primeraVez = false;

        await this.leer(respuesta.body);
      } catch {
        if (corte.aborted) return;
        this.caido.set(true);
        await new Promise((listo) => setTimeout(listo, MS_REINTENTO));
      }
    }
  }

  /** Formato SSE: bloques separados por línea en blanco, el dato en «data:». */
  private async leer(cuerpo: ReadableStream<Uint8Array>): Promise<void> {
    const lector = cuerpo.getReader();
    const texto = new TextDecoder();
    let resto = '';

    for (;;) {
      const { value, done } = await lector.read();
      if (done) return;

      const bloques = (resto + texto.decode(value, { stream: true })).split('\n\n');
      resto = bloques.pop() ?? '';

      for (const bloque of bloques) {
        const linea = bloque.split('\n').find((l) => l.startsWith('data:'));
        if (!linea) continue;
        const cambio = JSON.parse(linea.slice(5)) as { recurso: string; cliente: string };
        if (cambio.cliente !== this.idPestana) this.cambios.next(cambio.recurso);
      }
    }
  }
}

/**
 * Vuelve a cargar la pantalla cuando otro puesto cambia algo.
 *
 * <p>Se llama en el constructor del componente, con su propia función de carga.
 * Recarga ante cualquier cambio y no solo los «suyos»: consumir una pieza en
 * una orden cambia el almacén, y adivinar esas relaciones es donde se cuelan
 * los fallos.
 *
 * <p>Si se está escribiendo en un campo, espera a que se salga de él: que la
 * pantalla se recargue a media palabra sería peor que verla un momento vieja.
 */
// ponytail: recarga la pantalla entera ante cualquier cambio; si con muchos
// puestos pesa, filtrar por el recurso que llega en el aviso.
export function alCambiarDatos(recargar: () => void): void {
  let esperando = false;

  const cuandoNoSeEscriba = () => {
    const activo = document.activeElement;
    const escribiendo =
      activo instanceof HTMLInputElement ||
      activo instanceof HTMLTextAreaElement ||
      activo instanceof HTMLSelectElement;
    if (!escribiendo) {
      esperando = false;
      recargar();
    } else if (!esperando) {
      esperando = true;
      activo.addEventListener('blur', () => setTimeout(cuandoNoSeEscriba), { once: true });
    }
  };

  inject(TiempoRealService)
    .cambios.pipe(debounceTime(300), takeUntilDestroyed())
    .subscribe(() => cuandoNoSeEscriba());
}
