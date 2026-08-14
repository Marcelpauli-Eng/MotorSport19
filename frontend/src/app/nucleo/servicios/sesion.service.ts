import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Los tres roles de siempre.
 *
 * Ya no son los únicos: el administrador compone los que quiera. Se conserva el
 * tipo porque muchas pantallas todavía preguntan por él a través de `puede()`,
 * que ahora traduce cada uno al permiso que de verdad lo definía.
 */
export type Rol = 'ADMIN' | 'MOSTRADOR' | 'TECNICO';

/** Un permiso del catálogo. La lista buena vive en el enum del backend. */
export type Permiso = string;

export interface UsuarioSesion {
  id: number;
  username: string;
  nombreCompleto: string;
  rolId: number;
  /** Nombre del rol, solo para enseñarlo. Para decidir se usan los permisos. */
  rol: string;
  permisos: Permiso[];
}

/** Lo que devuelve POST /auth/login. */
export interface RespuestaLogin {
  token: string;
  tipo: string;
  duracionSegundos: number;
  usuario: UsuarioSesion;
}

const CLAVE_TOKEN = 'motorsport19.token';
const CLAVE_USUARIO = 'motorsport19.usuario';

/**
 * Estado de la sesión del usuario.
 *
 * El token y el usuario se guardan en `localStorage` para que al recargar la
 * página no haya que volver a entrar. Es una decisión consciente: el sitio de
 * trabajo es el mostrador del taller y ese PC no lo tocan los clientes. Si algún
 * día se usara desde un equipo compartido habría que pasar a `sessionStorage`,
 * que se vacía al cerrar la pestaña.
 *
 * Quien manda de verdad es el backend. Lo que se guarda aquí es lo que el
 * servidor ya ha validado, y el rol solo sirve para no enseñar botones que van a
 * responder 403. Nunca es una autorización: esa vive en la API.
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.urlApi}/auth`;

  private readonly _token = signal<string | null>(localStorage.getItem(CLAVE_TOKEN));
  private readonly _usuario = signal<UsuarioSesion | null>(usuarioGuardado());

  readonly token = this._token.asReadonly();
  readonly usuario = this._usuario.asReadonly();
  readonly autenticado = computed(() => this._token() !== null && this._usuario() !== null);
  readonly rol = computed(() => this._usuario()?.rol ?? null);
  readonly permisos = computed(() => this._usuario()?.permisos ?? []);

  /**
   * ¿Tiene concedido este permiso?
   *
   * Es la pregunta buena desde que los roles los compone el administrador. Sirve
   * para no enseñar botones que la API va a rechazar; la autorización de verdad
   * sigue estando en el backend, que comprueba el mismo permiso en cada ruta.
   */
  tienePermiso(...permisos: Permiso[]): boolean {
    const concedidos = this.permisos();
    return permisos.some((p) => concedidos.includes(p));
  }

  /**
   * ¿Encaja el usuario en alguno de los tres perfiles de siempre?
   *
   * Se conserva porque muchas pantallas todavía preguntan así, pero ya no
   * compara nombres de rol —que ahora los pone el taller y pueden ser
   * cualquiera— sino el permiso que definía a cada perfil:
   *
   * - ADMIN, quien reparte permisos.
   * - MOSTRADOR, quien ve el dinero.
   * - TECNICO, quien solo ve las órdenes que tiene asignadas.
   *
   * Las pantallas nuevas deberían usar `tienePermiso()` directamente, que dice
   * lo que de verdad se está comprobando.
   */
  puede(...roles: Rol[]): boolean {
    return roles.some((rol) => {
      switch (rol) {
        case 'ADMIN':
          return this.tienePermiso('ROLES_GESTIONAR');
        case 'MOSTRADOR':
          return this.tienePermiso('IMPORTES_VER');
        case 'TECNICO':
          return this.autenticado() && !this.tienePermiso('ORDENES_VER_TODAS');
        default:
          return false;
      }
    });
  }

  entrar(username: string, password: string): Observable<RespuestaLogin> {
    return this.http
      .post<RespuestaLogin>(`${this.base}/login`, { username, password })
      .pipe(tap((respuesta) => this.guardar(respuesta.token, respuesta.usuario)));
  }

  /**
   * Confirma contra el servidor que el token guardado sigue valiendo.
   *
   * Se llama al arrancar. Sin esto, un token caducado dejaría la interfaz
   * montada y el usuario solo se enteraría al pulsar algo y ver un error.
   */
  revalidar(): Observable<UsuarioSesion> {
    return this.http
      .get<UsuarioSesion>(`${this.base}/yo`)
      .pipe(tap((usuario) => this.guardar(this._token()!, usuario)));
  }

  cambiarPassword(passwordActual: string, passwordNueva: string): Observable<void> {
    return this.http.post<void>(`${this.base}/password`, { passwordActual, passwordNueva });
  }

  salir(): void {
    localStorage.removeItem(CLAVE_TOKEN);
    localStorage.removeItem(CLAVE_USUARIO);
    this._token.set(null);
    this._usuario.set(null);
  }

  private guardar(token: string, usuario: UsuarioSesion): void {
    localStorage.setItem(CLAVE_TOKEN, token);
    localStorage.setItem(CLAVE_USUARIO, JSON.stringify(usuario));
    this._token.set(token);
    this._usuario.set(usuario);
  }
}

/** Recupera el usuario guardado, tolerando que el dato esté corrupto. */
function usuarioGuardado(): UsuarioSesion | null {
  const crudo = localStorage.getItem(CLAVE_USUARIO);
  if (!crudo) {
    return null;
  }
  try {
    return JSON.parse(crudo) as UsuarioSesion;
  } catch {
    localStorage.removeItem(CLAVE_USUARIO);
    return null;
  }
}
