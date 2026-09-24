import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ConfiguracionTaller } from '../modelos/configuracion';

/** Datos fiscales del taller y tarifa por hora. */
@Injectable({ providedIn: 'root' })
export class ConfiguracionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/configuracion`;

  obtener(): Observable<ConfiguracionTaller> {
    return this.http.get<ConfiguracionTaller>(this.base);
  }

  guardar(datos: Partial<ConfiguracionTaller>): Observable<ConfiguracionTaller> {
    return this.http.put<ConfiguracionTaller>(this.base, datos);
  }

  /** Configura la tasa de reciclaje de neumáticos, o la desactiva con nulos. */
  guardarTasaNeumatico(
    familiaNeumaticos: string | null,
    piezaTasaId: number | null,
  ): Observable<ConfiguracionTaller> {
    return this.http.put<ConfiguracionTaller>(`${this.base}/tasa-neumatico`, {
      familiaNeumaticos,
      piezaTasaId,
    });
  }
}
