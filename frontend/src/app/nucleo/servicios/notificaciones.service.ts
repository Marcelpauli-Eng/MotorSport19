import { Injectable, signal } from '@angular/core';

/** Lo que dura un aviso en la esquina antes de irse solo. */
const MS_EN_PANTALLA = 5000;

export type TipoAviso = 'exito' | 'error' | 'info';

export interface Aviso {
  id: number;
  tipo: TipoAviso;
  mensaje: string;
  detalles: string[];
}

/**
 * Avisos que se muestran en la esquina de la pantalla.
 *
 * Todos se van a los cinco segundos, errores incluidos: lo que no se leyó en
 * ese rato no se iba a leer, y una esquina con avisos viejos apilados acaba
 * tapando la pantalla. Lo que sí falla no desaparece por esto, porque lo dice
 * la propia pantalla: la orden no cambia de estado, el campo se queda en rojo.
 */
@Injectable({ providedIn: 'root' })
export class NotificacionesService {
  private siguienteId = 1;
  private readonly _avisos = signal<Aviso[]>([]);

  readonly avisos = this._avisos.asReadonly();

  exito(mensaje: string): void {
    this.anadir('exito', mensaje, [], MS_EN_PANTALLA);
  }

  info(mensaje: string): void {
    this.anadir('info', mensaje, [], MS_EN_PANTALLA);
  }

  error(mensaje: string, detalles: string[] = []): void {
    this.anadir('error', mensaje, detalles, MS_EN_PANTALLA);
  }

  cerrar(id: number): void {
    this._avisos.update((avisos) => avisos.filter((a) => a.id !== id));
  }

  private anadir(tipo: TipoAviso, mensaje: string, detalles: string[], msAutoCierre?: number): void {
    // El mismo aviso dos veces a la vez no dice nada nuevo: una pantalla que pide
    // cinco cosas a un servidor caído apilaba cinco errores idénticos.
    if (this._avisos().some((a) => a.tipo === tipo && a.mensaje === mensaje)) return;

    const id = this.siguienteId++;
    this._avisos.update((avisos) => [...avisos, { id, tipo, mensaje, detalles }]);

    if (msAutoCierre) {
      setTimeout(() => this.cerrar(id), msAutoCierre);
    }
  }
}
