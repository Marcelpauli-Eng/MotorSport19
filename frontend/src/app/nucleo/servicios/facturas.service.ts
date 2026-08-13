import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../modelos/comunes';
import {
  EventoFactura,
  Factura,
  FacturaResumen,
  InformeVerificacion,
  SerieFactura,
  TipoRectificativa,
} from '../modelos/facturacion';
import { PdfService } from './pdf.service';

export interface FiltroFacturas {
  tipo?: string | null;
  desde?: string | null;
  hasta?: string | null;
  receptorId?: number | null;
  /** `true` solo con IVA, `false` solo las emitidas al 0 %, ausente todas. */
  conIva?: boolean | null;
  pagina?: number;
  tamano?: number;
}

@Injectable({ providedIn: 'root' })
export class FacturasService {
  private readonly http = inject(HttpClient);
  private readonly pdf = inject(PdfService);
  private readonly base = `${environment.urlApi}/facturas`;

  buscar(filtro: FiltroFacturas = {}): Observable<Pagina<FacturaResumen>> {
    let params = new HttpParams()
      .set('page', filtro.pagina ?? 0)
      .set('size', filtro.tamano ?? 20);

    if (filtro.tipo) params = params.set('tipo', filtro.tipo);
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    if (filtro.receptorId) params = params.set('receptorId', filtro.receptorId);
    // Se compara con null a propósito: `false` es un filtro válido —las de 0 %—
    // y con un `if (filtro.conIva)` se perdería.
    if (filtro.conIva != null) params = params.set('conIva', filtro.conIva);

    return this.http.get<Pagina<FacturaResumen>>(this.base, { params });
  }

  obtener(id: number): Observable<Factura> {
    return this.http.get<Factura>(`${this.base}/${id}`);
  }

  series(): Observable<SerieFactura[]> {
    return this.http.get<SerieFactura[]>(`${this.base}/series`);
  }

  rectificativasDe(id: number): Observable<FacturaResumen[]> {
    return this.http.get<FacturaResumen[]>(`${this.base}/${id}/rectificativas`);
  }

  emitir(ordenTrabajoId: number, serieId: number, fechaEmision?: string): Observable<Factura> {
    return this.http.post<Factura>(this.base, { ordenTrabajoId, serieId, fechaEmision });
  }

  rectificar(
    facturaId: number,
    serieId: number,
    tipoRectificativa: TipoRectificativa,
    motivo: string,
  ): Observable<Factura> {
    return this.http.post<Factura>(
      `${this.base}/${facturaId}/rectificativas`,
      { serieId, tipoRectificativa, motivo, lineas: [] },
    );
  }

  /** Recorre el registro comprobando la cadena de huellas de extremo a extremo. */
  verificarCadena(): Observable<InformeVerificacion> {
    return this.http.post<InformeVerificacion>(`${this.base}/verificacion`, null);
  }

  /** Abre el PDF de una factura en otra pestaña. */
  abrirPdf(id: number): void {
    this.pdf.abrir(`${this.base}/${id}/pdf`, `factura-${id}.pdf`);
  }

  /** Descarga el libro registro en el formato indicado. */
  exportar(formato: 'csv' | 'json', desde?: string | null, hasta?: string | null): Observable<Blob> {
    let params = new HttpParams();
    if (desde) params = params.set('desde', desde);
    if (hasta) params = params.set('hasta', hasta);

    return this.http.get(`${this.base}/exportacion/${formato}`, {
      params,
      responseType: 'blob',
    });
  }

  /**
   * Los PDF de varias facturas en un ZIP.
   *
   * <p>Va por HttpClient y no por un enlace normal porque el token viaja en una
   * cabecera que pone el interceptor, y un `<a href>` no la lleva.
   */
  descargarPdfsEnZip(ids: number[]): Observable<Blob> {
    const params = ids.reduce((p, id) => p.append('ids', id), new HttpParams());

    return this.http.get(`${this.base}/exportacion/pdf`, { params, responseType: 'blob' });
  }

  eventos(facturaId: number): Observable<EventoFactura[]> {
    return this.http.get<EventoFactura[]>(
      `${environment.urlApi}/facturacion/eventos/factura/${facturaId}`,
    );
  }

  eventosRecientes(tamano = 50): Observable<Pagina<EventoFactura>> {
    return this.http.get<Pagina<EventoFactura>>(`${environment.urlApi}/facturacion/eventos`, {
      params: new HttpParams().set('size', tamano),
    });
  }
}
