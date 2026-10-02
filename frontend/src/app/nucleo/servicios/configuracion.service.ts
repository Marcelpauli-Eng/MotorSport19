import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ConfiguracionTaller, ReglaCobro } from '../modelos/configuracion';
import { PdfService } from './pdf.service';

/** Datos fiscales del taller y tarifa por hora. */
@Injectable({ providedIn: 'root' })
export class ConfiguracionService {
  private readonly http = inject(HttpClient);
  private readonly pdf = inject(PdfService);
  private readonly base = `${environment.urlApi}/configuracion`;

  obtener(): Observable<ConfiguracionTaller> {
    return this.http.get<ConfiguracionTaller>(this.base);
  }

  guardar(datos: Partial<ConfiguracionTaller>): Observable<ConfiguracionTaller> {
    return this.http.put<ConfiguracionTaller>(this.base, datos);
  }

  /** El presupuesto en blanco, para imprimirlo y rellenarlo a mano. */
  abrirPlantillaPresupuesto(): void {
    this.pdf.abrir(`${this.base}/plantilla-presupuesto/pdf`, 'plantilla-presupuesto.pdf');
  }

  /** Tasas y pluses que se aplican solos al añadir una pieza a una orden. */
  reglas(): Observable<ReglaCobro[]> {
    return this.http.get<ReglaCobro[]>(`${this.base}/reglas`);
  }

  crearRegla(regla: Omit<ReglaCobro, 'id' | 'piezaNombre'>): Observable<ReglaCobro> {
    return this.http.post<ReglaCobro>(`${this.base}/reglas`, regla);
  }

  borrarRegla(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/reglas/${id}`);
  }
}
