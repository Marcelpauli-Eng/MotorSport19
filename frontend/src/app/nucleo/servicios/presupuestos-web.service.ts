import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CanalPresupuesto, PresupuestoWeb } from '../modelos/solicitudes';
import { LineaOT } from '../modelos/taller';
import { PdfService } from './pdf.service';

/**
 * Presupuesto de una solicitud web. Los métodos se llaman igual que los de
 * `OrdenesService` porque los usa la misma pantalla de presupuesto.
 */
@Injectable({ providedIn: 'root' })
export class PresupuestosWebService {
  private readonly http = inject(HttpClient);
  private readonly pdf = inject(PdfService);

  private url(id: number): string {
    return `${environment.urlApi}/solicitudes-web/${id}/presupuesto`;
  }

  /** Si aún no estaba empezado, el servidor lo empieza. */
  obtener(id: number): Observable<PresupuestoWeb> {
    return this.http.get<PresupuestoWeb>(this.url(id));
  }

  abrirPresupuestoPdf(id: number, _codigo?: string): void {
    this.pdf.abrir(`${this.url(id)}/pdf`, `presupuesto-web-${id}.pdf`);
  }

  aplicarTipoIva(id: number, tipoIva: string): Observable<PresupuestoWeb> {
    return this.http.put<PresupuestoWeb>(`${this.url(id)}/tipo-iva`, { tipoIva });
  }

  cambiarTarifaHora(id: number, tarifaHora: number): Observable<PresupuestoWeb> {
    return this.http.put<PresupuestoWeb>(`${this.url(id)}/tarifa-hora`, { tarifaHora });
  }

  aplicarDescuentoGeneral(id: number, descuentoPct: number): Observable<PresupuestoWeb> {
    return this.http.put<PresupuestoWeb>(`${this.url(id)}/descuento-general`, { descuentoPct });
  }

  anadirManoDeObra(
    id: number,
    datos: { descripcion: string; horas: number; descuentoPct?: number; tipoIva?: string },
  ): Observable<LineaOT> {
    return this.http.post<LineaOT>(`${this.url(id)}/lineas/mano-de-obra`, datos);
  }

  anadirPieza(id: number, datos: { piezaId: number; cantidad: number; descuentoPct?: number }): Observable<LineaOT> {
    return this.http.post<LineaOT>(`${this.url(id)}/lineas/piezas`, datos);
  }

  aplicarServicioTipo(id: number, servicioTipoId: number): Observable<LineaOT[]> {
    return this.http.post<LineaOT[]>(`${this.url(id)}/servicios-tipo/${servicioTipoId}`, null);
  }

  cambiarCantidadDeLinea(id: number, lineaId: number, cantidad: number): Observable<LineaOT> {
    return this.http.put<LineaOT>(`${this.url(id)}/lineas/${lineaId}/cantidad`, { cantidad });
  }

  cambiarPrecioDeLinea(id: number, lineaId: number, precioUnitario: number): Observable<LineaOT> {
    return this.http.put<LineaOT>(`${this.url(id)}/lineas/${lineaId}/precio`, { precioUnitario });
  }

  cambiarDescuentoDeLinea(id: number, lineaId: number, descuentoPct: number): Observable<LineaOT> {
    return this.http.put<LineaOT>(`${this.url(id)}/lineas/${lineaId}/descuento`, { descuentoPct });
  }

  quitarLinea(id: number, lineaId: number): Observable<void> {
    return this.http.delete<void>(`${this.url(id)}/lineas/${lineaId}`);
  }

  /** Apunta por dónde se le mandó. El mensaje lo manda quien lo atiende. */
  enviar(id: number, canal: CanalPresupuesto): Observable<PresupuestoWeb> {
    return this.http.post<PresupuestoWeb>(`${this.url(id)}/envio`, { canal });
  }

  /** Vuelve a pendiente para corregirlo. */
  reescribir(id: number): Observable<PresupuestoWeb> {
    return this.http.post<PresupuestoWeb>(`${this.url(id)}/reescritura`, null);
  }

  rechazar(id: number, nota: string | null): Observable<PresupuestoWeb> {
    return this.http.post<PresupuestoWeb>(`${this.url(id)}/rechazo`, { nota });
  }

  /** El cliente acepta: se abre la orden de su moto, ya aprobada, con estas líneas. */
  aceptar(
    id: number,
    datos: {
      motoId: number;
      kmEntrada: number;
      fechaEstimadaSalida: string | null;
      tecnicoId: number | null;
      observaciones: string | null;
    },
  ): Observable<{ id: number; codigo: string }> {
    return this.http.post<{ id: number; codigo: string }>(`${this.url(id)}/aceptacion`, datos);
  }
}
