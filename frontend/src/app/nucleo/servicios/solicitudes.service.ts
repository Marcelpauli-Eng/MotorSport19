import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CanalPresupuesto, EstadoSolicitud, SolicitudWeb } from '../modelos/solicitudes';
import { DatosCita } from './citas.service';

@Injectable({ providedIn: 'root' })
export class SolicitudesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/solicitudes-web`;

  bandeja(estado: EstadoSolicitud): Observable<SolicitudWeb[]> {
    return this.http.get<SolicitudWeb[]>(this.base, { params: new HttpParams().set('estado', estado) });
  }

  /**
   * Solicitudes sin atender, para el número del menú.
   *
   * Lo refrescan el menú (cuando otro puesto cambia algo) y la propia bandeja
   * después de cada cambio: el aviso en directo no le llega a la pestaña que
   * hizo el cambio, y sin esto el número se quedaba viejo justo delante de
   * quien acababa de atender la solicitud.
   */
  readonly pendientes = signal(0);

  contarPendientes(): void {
    this.http.get<{ total: number }>(`${this.base}/pendientes`).subscribe({
      next: (r) => this.pendientes.set(r.total),
      error: () => {},
    });
  }

  /**
   * Una foto como blob. Un `<img src>` no lleva la cabecera con el token, así
   * que se baja por HttpClient y se enseña desde memoria.
   */
  foto(id: number, orden: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/fotos/${orden}`, { responseType: 'blob' });
  }

  /** Apunta la cita en la agenda y cierra la solicitud, todo de una vez. */
  darCita(id: number, datos: DatosCita): Observable<SolicitudWeb> {
    return this.http.post<SolicitudWeb>(`${this.base}/${id}/cita`, datos);
  }

  /**
   * Apunta el presupuesto que se le manda. El mensaje sale del WhatsApp o del
   * correo de quien lo atiende; aquí solo queda constancia de cuánto y por dónde.
   */
  enviarPresupuesto(
    id: number,
    datos: { importe: number; detalle: string | null; canal: CanalPresupuesto },
  ): Observable<SolicitudWeb> {
    return this.http.post<SolicitudWeb>(`${this.base}/${id}/presupuesto`, datos);
  }

  marcarAtendida(id: number, nota: string | null): Observable<SolicitudWeb> {
    return this.http.post<SolicitudWeb>(`${this.base}/${id}/atencion`, { nota });
  }

  descartar(id: number, nota: string | null): Observable<SolicitudWeb> {
    return this.http.post<SolicitudWeb>(`${this.base}/${id}/descarte`, { nota });
  }
}
