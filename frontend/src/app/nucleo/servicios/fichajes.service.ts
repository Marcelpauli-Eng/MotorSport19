import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Cómo está mi jornada ahora mismo. */
export interface Jornada {
  abierta: boolean;
  fichajeId: number | null;
  inicio: string | null;
  minutosTrabajados: number;
  /** Exacto al segundo, calculado por el servidor. */
  segundosTrabajados: number;
}

export interface Fichaje {
  id: number;
  usuarioId: number;
  usuarioNombre: string;
  /** La hora que cuenta: la corregida si la hubo, y si no la fichada. */
  inicio: string;
  fin: string | null;
  minutos: number;
  /** Exacto al segundo, calculado por el servidor. */
  segundos: number;
  abierta: boolean;
  corregida: boolean;
  cerradaPorOlvido: boolean;
  motivoCorreccion: string | null;
  /** Lo que se fichó de verdad, para poder comparar con la corrección. */
  inicioFichado: string;
  finFichado: string | null;
}

/** Una cosa que se hizo durante la jornada. */
export interface ApunteActividad {
  momento: string;
  tipo: string;
  texto: string;
  /** A dónde lleva al pulsarlo, si lleva a algún sitio. */
  enlaceTipo: string | null;
  enlaceId: number | null;
}

export interface PorTrabajador {
  usuarioId: number;
  usuarioNombre: string;
  minutos: number;
  segundos: number;
  horasLegibles: string;
  jornadas: number;
  detalle: Fichaje[];
}

export interface ResumenFichajes {
  trabajadores: PorTrabajador[];
  minutosTotales: number;
  segundosTotales: number;
  jornadas: number;
  abiertas: number;
}

/**
 * Fichar la jornada y consultar las horas.
 *
 * <p>El estado de la jornada se guarda en una señal compartida porque lo miran
 * dos sitios a la vez: el botón de la barra superior y la pantalla que bloquea
 * el programa. Con dos copias, una se quedaría desactualizada y el usuario
 * vería el botón de empezar cuando ya había empezado.
 */
@Injectable({ providedIn: 'root' })
export class FichajesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/fichajes`;

  /**
   * Cuanto adelanta o atrasa el reloj de este ordenador respecto al del servidor.
   *
   * <p>Las horas que valen son las del servidor: es el unico reloj comun a todo
   * el taller, y el del PC del mostrador puede estar desajustado. Para una
   * jornada cerrada eso da igual —los segundos vienen ya calculados—, pero una
   * abierta hay que seguir contandola aqui, y sin corregir el desfase el
   * contador arrancaria torcido desde el primer segundo.
   *
   * <p>Se deduce de cualquier respuesta: el servidor manda la hora de entrada y
   * los segundos que llevaba al responder, o sea que dice implicitamente que
   * hora tenia el.
   */
  private readonly desfaseMs = signal(0);

  /** Nula mientras no se haya consultado ni una vez. */
  readonly jornada = signal<Jornada | null>(null);

  /**
   * Los segundos que lleva una jornada, exactos.
   *
   * <p>Cerrada, los que dijo el servidor. Abierta, esos mismos avanzando con el
   * reloj de {@code ahora}, que es una senal para que la pantalla se repinte
   * sola cada segundo.
   */
  segundosDe(
    jornada: { inicio: string | null; abierta: boolean; segundos: number },
    ahora: number,
  ): number {
    if (!jornada.abierta || !jornada.inicio) return jornada.segundos;
    return Math.max(0, Math.floor((ahora - this.desfaseMs() - Date.parse(jornada.inicio)) / 1000));
  }

  /** «3:20:05». El formato del contador en todas las pantallas. */
  static reloj(segundos: number): string {
    const dos = (n: number) => String(n).padStart(2, '0');
    return `${Math.floor(segundos / 3600)}:${dos(Math.floor(segundos / 60) % 60)}:${dos(segundos % 60)}`;
  }

  /** Pone el reloj en hora con el del servidor a partir de una jornada abierta. */
  private ajustarReloj(inicio: string | null, segundos: number): void {
    if (!inicio) return;
    this.desfaseMs.set(Date.now() - (Date.parse(inicio) + segundos * 1000));
  }

  private guardarJornada(j: Jornada): void {
    if (j.abierta) this.ajustarReloj(j.inicio, j.segundosTrabajados);
    this.jornada.set(j);
  }

  consultar(): Observable<Jornada> {
    return this.http
      .get<Jornada>(`${this.base}/jornada`)
      .pipe(tap((j) => this.guardarJornada(j)));
  }

  empezar(): Observable<Jornada> {
    return this.http
      .post<Jornada>(`${this.base}/jornada`, {})
      .pipe(tap((j) => this.guardarJornada(j)));
  }

  terminar(): Observable<Fichaje> {
    return this.http.post<Fichaje>(`${this.base}/jornada/cierre`, {}).pipe(
      tap((f) =>
        this.jornada.set({
          abierta: false,
          fichajeId: null,
          inicio: null,
          minutosTrabajados: f.minutos,
          segundosTrabajados: f.segundos,
        }),
      ),
    );
  }

  /** Mis horas. No hace falta permiso: son las de uno mismo. */
  mias(desde: string, hasta: string): Observable<ResumenFichajes> {
    return this.http.get<ResumenFichajes>(`${this.base}/mias`, {
      params: new HttpParams().set('desde', desde).set('hasta', hasta),
    });
  }

  /** Las de todo el taller. Requiere el permiso FICHAJES_VER. */
  periodo(desde: string, hasta: string, usuarioId?: number | null): Observable<ResumenFichajes> {
    let params = new HttpParams().set('desde', desde).set('hasta', hasta);
    if (usuarioId) params = params.set('usuarioId', usuarioId);
    return this.http.get<ResumenFichajes>(this.base, { params });
  }

  /** Lo que esa persona hizo durante esa jornada. */
  actividad(fichajeId: number): Observable<ApunteActividad[]> {
    return this.http.get<ApunteActividad[]>(`${this.base}/${fichajeId}/actividad`);
  }

  abiertas(): Observable<Fichaje[]> {
    return this.http.get<Fichaje[]>(`${this.base}/abiertas`).pipe(
      tap((lista) => {
        const alguna = lista[0];
        if (alguna) this.ajustarReloj(alguna.inicio, alguna.segundos);
      }),
    );
  }

  cerrarPorOlvido(id: number, fin: string, motivo: string): Observable<Fichaje> {
    return this.http.put<Fichaje>(`${this.base}/${id}/cierre-manual`, { fin, motivo });
  }

  corregir(
    id: number,
    inicio: string | null,
    fin: string | null,
    motivo: string,
  ): Observable<Fichaje> {
    return this.http.put<Fichaje>(`${this.base}/${id}/correccion`, { inicio, fin, motivo });
  }
}
