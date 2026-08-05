import { Injectable, computed, signal } from '@angular/core';

export type Rol = 'ADMIN' | 'MOSTRADOR' | 'TECNICO';

export interface UsuarioSesion {
  id: number;
  username: string;
  nombreCompleto: string;
  rol: Rol;
}

const CLAVE_TOKEN = 'motorsport19.token';

/**
 * Estado de la sesión del usuario.
 *
 * La autenticación real llega en la fase 5. De momento este servicio expone la
 * misma interfaz que tendrá entonces, para que las pantallas se escriban una
 * sola vez: cuando exista el login, solo cambia de dónde sale el usuario.
 *
 * Mientras tanto devuelve un usuario de trabajo con rol ADMIN, de forma que la
 * interfaz se pueda usar entera. Es deliberado y provisional: la API tampoco
 * exige credenciales todavía.
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly _token = signal<string | null>(localStorage.getItem(CLAVE_TOKEN));

  private readonly _usuario = signal<UsuarioSesion | null>({
    id: 1,
    username: 'admin',
    nombreCompleto: 'Dirección del taller',
    rol: 'ADMIN',
  });

  readonly token = this._token.asReadonly();
  readonly usuario = this._usuario.asReadonly();
  readonly autenticado = computed(() => this._usuario() !== null);
  readonly rol = computed(() => this._usuario()?.rol ?? null);

  /** Identificador que se envía a la API para firmar las operaciones. */
  readonly usuarioId = computed(() => this._usuario()?.id ?? null);

  puede(...roles: Rol[]): boolean {
    const rol = this.rol();
    return rol !== null && roles.includes(rol);
  }

  iniciarSesion(token: string, usuario: UsuarioSesion): void {
    localStorage.setItem(CLAVE_TOKEN, token);
    this._token.set(token);
    this._usuario.set(usuario);
  }

  cerrarSesion(): void {
    localStorage.removeItem(CLAVE_TOKEN);
    this._token.set(null);
    this._usuario.set(null);
  }
}
