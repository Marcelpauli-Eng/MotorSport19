import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../modelos/comunes';
import {
  EstadoOT,
  LineaOT,
  OrdenTrabajo,
  OrdenTrabajoResumen,
  ResultadoConsumo,
} from '../modelos/taller';

export interface FiltroOrdenes {
  estado?: EstadoOT | null;
  tecnicoId?: number | null;
  clienteId?: number | null;
  motoId?: number | null;
  soloAbiertas?: boolean;
  pagina?: number;
  tamano?: number;
}

@Injectable({ providedIn: 'root' })
export class OrdenesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/ordenes`;

  buscar(filtro: FiltroOrdenes = {}): Observable<Pagina<OrdenTrabajoResumen>> {
    let params = new HttpParams()
      .set('page', filtro.pagina ?? 0)
      .set('size', filtro.tamano ?? 20)
      .set('soloAbiertas', filtro.soloAbiertas ?? true);

    if (filtro.estado) params = params.set('estado', filtro.estado);
    if (filtro.tecnicoId) params = params.set('tecnicoId', filtro.tecnicoId);
    if (filtro.clienteId) params = params.set('clienteId', filtro.clienteId);
    if (filtro.motoId) params = params.set('motoId', filtro.motoId);

    return this.http.get<Pagina<OrdenTrabajoResumen>>(this.base, { params });
  }

  obtener(id: number): Observable<OrdenTrabajo> {
    return this.http.get<OrdenTrabajo>(`${this.base}/${id}`);
  }

  historialDeMoto(motoId: number): Observable<OrdenTrabajoResumen[]> {
    return this.http.get<OrdenTrabajoResumen[]>(`${this.base}/moto/${motoId}/historial`);
  }

  abrir(datos: {
    motoId: number;
    problemaReportado: string;
    kmEntrada: number;
    fechaEstimadaSalida?: string | null;
    tecnicoId?: number | null;
    observaciones?: string | null;
  }): Observable<OrdenTrabajo> {
    return this.http.post<OrdenTrabajo>(this.base, datos);
  }

  /** Pone o cambia el técnico que lleva la orden. `null` la deja sin asignar. */
  asignarTecnico(id: number, tecnicoId: number | null): Observable<OrdenTrabajo> {
    return this.http.put<OrdenTrabajo>(`${this.base}/${id}/tecnico`, { tecnicoId });
  }

  /**
   * Cambia el precio de la hora de esta orden, sin tocar el del taller.
   *
   * Las horas ya apuntadas se revaloran, así que la respuesta trae los totales
   * recalculados.
   */
  cambiarTarifaHora(id: number, tarifaHora: number): Observable<OrdenTrabajo> {
    return this.http.put<OrdenTrabajo>(`${this.base}/${id}/tarifa-hora`, { tarifaHora });
  }

  registrarDiagnostico(id: number, diagnostico: string): Observable<OrdenTrabajo> {
    return this.http.put<OrdenTrabajo>(`${this.base}/${id}/diagnostico`, { diagnostico });
  }

  anadirManoDeObra(
    id: number,
    datos: { descripcion: string; horas: number; descuentoPct?: number; tipoIva?: string },
  ): Observable<LineaOT> {
    return this.http.post<LineaOT>(`${this.base}/${id}/lineas/mano-de-obra`, datos);
  }

  anadirPieza(
    id: number,
    datos: { piezaId: number; cantidad: number; descuentoPct?: number },
  ): Observable<LineaOT> {
    return this.http.post<LineaOT>(`${this.base}/${id}/lineas/piezas`, datos);
  }

  /**
   * Pone un precio cerrado a una línea de mano de obra.
   *
   * Es el escalón fino sobre `cambiarTarifaHora`: aquella mueve todo el
   * presupuesto, esta deja un concepto suelto a un precio pactado.
   */
  cambiarPrecioDeLinea(
    id: number,
    lineaId: number,
    precioUnitario: number,
  ): Observable<LineaOT> {
    return this.http.put<LineaOT>(`${this.base}/${id}/lineas/${lineaId}/precio`, {
      precioUnitario,
    });
  }

  quitarLinea(id: number, lineaId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/lineas/${lineaId}`);
  }

  // ----- Transiciones de estado -----

  iniciarDiagnostico(id: number, tecnicoId?: number | null): Observable<OrdenTrabajo> {
    let params = new HttpParams();
    if (tecnicoId) params = params.set('tecnicoId', tecnicoId);
    return this.http.post<OrdenTrabajo>(`${this.base}/${id}/diagnostico`, null, { params });
  }

  presupuestar(id: number): Observable<OrdenTrabajo> {
    return this.http.post<OrdenTrabajo>(`${this.base}/${id}/presupuesto`, null);
  }

  aprobar(id: number, aprobadoPor?: string): Observable<OrdenTrabajo> {
    return this.http.post<OrdenTrabajo>(
      `${this.base}/${id}/aprobacion`,
      { aprobadoPor: aprobadoPor ?? null },
    );
  }

  rechazar(id: number, motivo: string): Observable<OrdenTrabajo> {
    return this.http.post<OrdenTrabajo>(
      `${this.base}/${id}/rechazo`,
      { motivo },
    );
  }

  /**
   * Entra en reparación consumiendo el material.
   *
   * Si falta alguna pieza la respuesta sigue siendo correcta (200): la orden
   * queda en ESPERANDO_PIEZAS y el resultado detalla qué hay que pedir.
   */
  iniciarReparacion(id: number): Observable<ResultadoConsumo> {
    return this.http.post<ResultadoConsumo>(`${this.base}/${id}/reparacion`, null);
  }

  reanudarReparacion(id: number): Observable<ResultadoConsumo> {
    return this.http.post<ResultadoConsumo>(`${this.base}/${id}/reanudacion`, null);
  }

  marcarLista(id: number): Observable<OrdenTrabajo> {
    return this.http.post<OrdenTrabajo>(`${this.base}/${id}/lista`, null);
  }

  entregar(id: number): Observable<OrdenTrabajo> {
    return this.http.post<OrdenTrabajo>(`${this.base}/${id}/entrega`, null);
  }
}
