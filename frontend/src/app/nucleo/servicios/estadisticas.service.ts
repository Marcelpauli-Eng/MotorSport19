import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { InformeFacturacion } from '../modelos/estadisticas';

@Injectable({ providedIn: 'root' })
export class EstadisticasService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/estadisticas`;

  /**
   * Informe completo de un ejercicio.
   *
   * <p>Viene todo en una llamada a propósito: la pantalla necesita las cinco
   * piezas para pintarse, y pedirlas por separado haría que las tarjetas y las
   * gráficas fueran apareciendo de una en una.
   */
  facturacion(ejercicio?: number): Observable<InformeFacturacion> {
    let params = new HttpParams();
    if (ejercicio) params = params.set('ejercicio', ejercicio);
    return this.http.get<InformeFacturacion>(`${this.base}/facturacion`, { params });
  }
}
