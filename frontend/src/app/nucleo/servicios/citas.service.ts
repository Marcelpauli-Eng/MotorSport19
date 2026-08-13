import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AgendaSemanal, CargaDiaria, Cita, SeguimientoAusencias } from '../modelos/agenda';

/** Datos de una cita al darla de alta o modificarla. */
export interface DatosCita {
  fechaHora: string;
  duracionEstimada: number;
  motoId?: number | null;
  contactoNombre?: string | null;
  contactoTelefono?: string | null;
  descripcionMoto?: string | null;
  motivo: string;
  tecnicoId?: number | null;
  observaciones?: string | null;
}

@Injectable({ providedIn: 'root' })
export class CitasService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/citas`;

  /**
   * Citas de un rango de días, ambos incluidos.
   *
   * No va paginado a propósito: así es como se mira un calendario («enséñame
   * esta semana»), y una semana de taller son unas decenas de citas.
   */
  agenda(desde: string, hasta: string): Observable<Cita[]> {
    const params = new HttpParams().set('desde', desde).set('hasta', hasta);
    return this.http.get<Cita[]>(this.base, { params });
  }

  /** Trabajo comprometido día a día, incluidos los días vacíos. */
  carga(desde: string, hasta: string): Observable<CargaDiaria[]> {
    const params = new HttpParams().set('desde', desde).set('hasta', hasta);
    return this.http.get<CargaDiaria[]>(`${this.base}/carga`, { params });
  }

  /**
   * La semana repartida por técnico, con el hueco de cada uno.
   *
   * Salen todos los técnicos, tengan citas o no: el que tiene la semana libre
   * es justo a quien se busca al mirar esta pantalla.
   */
  semana(desde: string, hasta: string): Observable<AgendaSemanal> {
    const params = new HttpParams().set('desde', desde).set('hasta', hasta);
    return this.http.get<AgendaSemanal>(`${this.base}/semana`, { params });
  }

  /** Plantones del periodo: cuántos, cuántas horas se perdieron y quién repite. */
  ausencias(desde: string, hasta: string): Observable<SeguimientoAusencias> {
    const params = new HttpParams().set('desde', desde).set('hasta', hasta);
    return this.http.get<SeguimientoAusencias>(`${this.base}/ausencias`, { params });
  }

  obtener(id: number): Observable<Cita> {
    return this.http.get<Cita>(`${this.base}/${id}`);
  }

  historialDeMoto(motoId: number): Observable<Cita[]> {
    return this.http.get<Cita[]>(`${this.base}/moto/${motoId}`);
  }

  agendar(datos: DatosCita): Observable<Cita> {
    return this.http.post<Cita>(this.base, datos);
  }

  actualizar(id: number, datos: DatosCita): Observable<Cita> {
    return this.http.put<Cita>(`${this.base}/${id}`, datos);
  }

  /** Mueve la cita de fecha sin tocar el resto de la ficha. */
  reprogramar(id: number, fechaHora: string): Observable<Cita> {
    return this.http.put<Cita>(`${this.base}/${id}/fecha`, { fechaHora });
  }

  confirmar(id: number): Observable<Cita> {
    return this.http.post<Cita>(`${this.base}/${id}/confirmacion`, null);
  }

  /** La moto ha llegado: abre su orden de trabajo y cierra la cita. */
  atender(
    id: number,
    datos: { motoId?: number | null; kmEntrada: number; problemaReportado?: string | null },
  ): Observable<Cita> {
    return this.http.post<Cita>(`${this.base}/${id}/entrada`, datos);
  }

  cancelar(id: number, motivo?: string): Observable<Cita> {
    return this.http.post<Cita>(`${this.base}/${id}/cancelacion`, { motivo: motivo ?? null });
  }

  marcarNoPresentado(id: number, motivo?: string): Observable<Cita> {
    return this.http.post<Cita>(`${this.base}/${id}/ausencia`, { motivo: motivo ?? null });
  }
}
