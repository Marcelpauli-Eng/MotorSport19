import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../modelos/comunes';
import { Cliente, ClienteResumen, MotoResumen, TipoDocumento } from '../modelos/taller';

export interface DatosFiscalesForm {
  tipoDocumento: TipoDocumento | null;
  documento: string;
  direccion: string;
  codigoPostal: string;
  ciudad: string;
  provincia: string;
  pais?: string;
}

@Injectable({ providedIn: 'root' })
export class ClientesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/clientes`;

  buscar(texto = '', soloActivos = true, pagina = 0, tamano = 20): Observable<Pagina<ClienteResumen>> {
    let params = new HttpParams()
      .set('page', pagina)
      .set('size', tamano)
      .set('soloActivos', soloActivos);
    if (texto.trim()) params = params.set('texto', texto.trim());

    return this.http.get<Pagina<ClienteResumen>>(this.base, { params });
  }

  obtener(id: number): Observable<Cliente> {
    return this.http.get<Cliente>(`${this.base}/${id}`);
  }

  motosDe(id: number, soloActivas = true): Observable<MotoResumen[]> {
    return this.http.get<MotoResumen[]>(`${this.base}/${id}/motos`, {
      params: new HttpParams().set('soloActivas', soloActivas),
    });
  }

  crear(datos: Partial<Cliente>): Observable<Cliente> {
    return this.http.post<Cliente>(this.base, datos);
  }

  actualizarContacto(
    id: number,
    datos: { nombre: string; apellidos?: string | null; telefono?: string | null; email?: string | null; observaciones?: string | null },
  ): Observable<Cliente> {
    return this.http.put<Cliente>(`${this.base}/${id}/contacto`, datos);
  }

  /** Completa o corrige los datos fiscales. El backend valida el dígito de control. */
  actualizarDatosFiscales(id: number, datos: DatosFiscalesForm): Observable<Cliente> {
    return this.http.put<Cliente>(`${this.base}/${id}/datos-fiscales`, datos);
  }

  darDeBaja(id: number): Observable<Cliente> {
    return this.http.post<Cliente>(`${this.base}/${id}/baja`, null);
  }

  reactivar(id: number): Observable<Cliente> {
    return this.http.post<Cliente>(`${this.base}/${id}/reactivacion`, null);
  }
}
