import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Un permiso del catálogo, tal y como se pinta en la pantalla de roles. */
export interface PermisoCatalogo {
  clave: string;
  descripcion: string;
  /** Aclaración para los que el nombre no basta. Puede venir a null. */
  detalle: string | null;
}

/** Los permisos agrupados por área: Clientes, Motos, Órdenes… */
export interface GrupoPermisos {
  clave: string;
  titulo: string;
  permisos: PermisoCatalogo[];
}

export interface Rol {
  id: number;
  nombre: string;
  descripcion: string | null;
  /** Rol de serie: no se borra. */
  sistema: boolean;
  activo: boolean;
  /** El de administración no se toca: es el que reparte los permisos. */
  editable: boolean;
  borrable: boolean;
  /** Cuántos usuarios lo llevan. Con gente dentro no se cierra ni se borra. */
  usuarios: number;
  permisos: string[];
}

/**
 * Roles del taller.
 *
 * El catálogo de permisos lo sirve el backend y no se repite aquí a propósito:
 * si las dos listas discreparan, la pantalla ofrecería casillas que no protegen
 * nada o escondería permisos que sí se comprueban.
 */
@Injectable({ providedIn: 'root' })
export class RolesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/roles`;

  listar(soloActivos = false): Observable<Rol[]> {
    return this.http.get<Rol[]>(this.base, {
      params: new HttpParams().set('soloActivos', soloActivos),
    });
  }

  catalogo(): Observable<GrupoPermisos[]> {
    return this.http.get<GrupoPermisos[]>(`${this.base}/permisos`);
  }

  crear(datos: {
    nombre: string;
    descripcion?: string | null;
    permisos: string[];
  }): Observable<Rol> {
    return this.http.post<Rol>(this.base, datos);
  }

  actualizar(
    id: number,
    datos: { nombre: string; descripcion?: string | null; permisos: string[] },
  ): Observable<Rol> {
    return this.http.put<Rol>(`${this.base}/${id}`, datos);
  }

  /** Abre o cierra el rol para nuevas asignaciones. */
  cambiarEstado(id: number, activo: boolean): Observable<Rol> {
    return this.http.put<Rol>(`${this.base}/${id}/estado`, null, {
      params: new HttpParams().set('activo', activo),
    });
  }

  borrar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
