import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LineaOT } from '../modelos/taller';
import { GuardarServicioTipo, ServicioTipo } from '../modelos/servicios';

/**
 * Plantillas de servicio.
 *
 * No pagina: un taller tiene diez o quince plantillas, y paginarlas obligaría
 * a la pantalla a manejar un estado que no aporta nada.
 */
@Injectable({ providedIn: 'root' })
export class ServiciosTipoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/servicios-tipo`;

  /**
   * @param soloActivos por defecto sí: el desplegable de la OT no debe ofrecer
   *   plantillas retiradas. La pantalla de mantenimiento pide todas.
   */
  listar(soloActivos = true): Observable<ServicioTipo[]> {
    const params = new HttpParams().set('soloActivos', soloActivos);
    return this.http.get<ServicioTipo[]>(this.base, { params });
  }

  obtener(id: number): Observable<ServicioTipo> {
    return this.http.get<ServicioTipo>(`${this.base}/${id}`);
  }

  crear(datos: GuardarServicioTipo): Observable<ServicioTipo> {
    return this.http.post<ServicioTipo>(this.base, datos);
  }

  actualizar(id: number, datos: GuardarServicioTipo): Observable<ServicioTipo> {
    return this.http.put<ServicioTipo>(`${this.base}/${id}`, datos);
  }

  /** Baja lógica. No hay borrado: una plantilla retirada explica OT antiguas. */
  cambiarActivo(id: number, activo: boolean): Observable<ServicioTipo> {
    const params = new HttpParams().set('activo', activo);
    return this.http.put<ServicioTipo>(`${this.base}/${id}/activo`, null, { params });
  }

  /**
   * Vuelca la plantilla en una orden. Devuelve solo las líneas añadidas, para
   * que la ficha de la OT pueda incorporarlas sin recargarla entera.
   */
  aplicarAOrden(ordenId: number, servicioTipoId: number): Observable<LineaOT[]> {
    return this.http.post<LineaOT[]>(
      `${environment.urlApi}/ordenes/${ordenId}/servicios-tipo/${servicioTipoId}`,
      null,
    );
  }
}
