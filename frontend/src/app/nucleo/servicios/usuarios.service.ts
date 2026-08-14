import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Tecnico, Usuario } from '../modelos/configuracion';
import { Rol } from './sesion.service';

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/usuarios`;

  /** Listado completo. Reservado a dirección. */
  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.base);
  }

  /** Solo los técnicos activos: es lo que mostrador necesita para repartir el trabajo. */
  tecnicos(): Observable<Tecnico[]> {
    return this.http.get<Tecnico[]>(`${this.base}/tecnicos`);
  }

  crear(datos: {
    username: string;
    password: string;
    nombreCompleto: string;
    email?: string | null;
    telefono?: string | null;
    rolId: number;
  }): Observable<Usuario> {
    return this.http.post<Usuario>(this.base, datos);
  }

  actualizar(
    id: number,
    datos: { nombreCompleto: string; email?: string | null; telefono?: string | null; rolId: number },
  ): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.base}/${id}`, datos);
  }

  /** Restablecimiento por dirección, sin conocer la contraseña anterior. */
  restablecerPassword(id: number, password: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${id}/password`, { password });
  }

  darDeBaja(id: number): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.base}/${id}/baja`, null);
  }

  reactivar(id: number): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.base}/${id}/reactivacion`, null);
  }
}
