import { Injectable, signal } from '@angular/core';

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
 * Los errores no se cierran solos: en un mostrador, un aviso que desaparece a
 * los tres segundos es un aviso que nadie ha leído.
 */
@Injectable({ providedIn: 'root' })
export class NotificacionesService {
  private siguienteId = 1;
  private readonly _avisos = signal<Aviso[]>([]);

  readonly avisos = this._avisos.asReadonly();

  exito(mensaje: string): void {
    this.anadir('exito', mensaje, [], 5000);
  }

  info(mensaje: string): void {
    this.anadir('info', mensaje, [], 5000);
  }

  error(mensaje: string, detalles: string[] = []): void {
    this.anadir('error', mensaje, detalles);
  }

  cerrar(id: number): void {
    this._avisos.update((avisos) => avisos.filter((a) => a.id !== id));
  }

  private anadir(tipo: TipoAviso, mensaje: string, detalles: string[], msAutoCierre?: number): void {
    const id = this.siguienteId++;
    this._avisos.update((avisos) => [...avisos, { id, tipo, mensaje, detalles }]);

    if (msAutoCierre) {
      setTimeout(() => this.cerrar(id), msAutoCierre);
    }
  }
}
