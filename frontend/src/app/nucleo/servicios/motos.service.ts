import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina } from '../modelos/comunes';
import { Moto, MotoResumen } from '../modelos/taller';

@Injectable({ providedIn: 'root' })
export class MotosService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/motos`;

  buscar(texto = '', soloActivas = true, pagina = 0, tamano = 20): Observable<Pagina<MotoResumen>> {
    let params = new HttpParams()
      .set('page', pagina)
      .set('size', tamano)
      .set('soloActivas', soloActivas);
    if (texto.trim()) params = params.set('texto', texto.trim());

    return this.http.get<Pagina<MotoResumen>>(this.base, { params });
  }

  obtener(id: number): Observable<Moto> {
    return this.http.get<Moto>(`${this.base}/${id}`);
  }

  crear(datos: {
    clienteId: number;
    matricula: string;
    marca: string;
    modelo: string;
    anio?: number | null;
    cilindrada?: number | null;
    color?: string | null;
    numeroBastidor?: string | null;
    kmActual?: number | null;
    observaciones?: string | null;
  }): Observable<Moto> {
    return this.http.post<Moto>(this.base, datos);
  }

  actualizar(id: number, datos: Partial<Moto>): Observable<Moto> {
    return this.http.put<Moto>(`${this.base}/${id}`, datos);
  }

  /** El cuentakilómetros no retrocede: el backend rechaza una lectura menor. */
  registrarKilometraje(id: number, km: number): Observable<Moto> {
    return this.http.put<Moto>(`${this.base}/${id}/kilometraje`, { km });
  }

  cambiarPropietario(id: number, nuevoClienteId: number): Observable<Moto> {
    return this.http.put<Moto>(`${this.base}/${id}/propietario`, { nuevoClienteId });
  }

  darDeBaja(id: number): Observable<Moto> {
    return this.http.post<Moto>(`${this.base}/${id}/baja`, null);
  }

  reactivar(id: number): Observable<Moto> {
    return this.http.post<Moto>(`${this.base}/${id}/reactivacion`, null);
  }
}
