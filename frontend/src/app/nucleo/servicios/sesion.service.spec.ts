import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SesionService, UsuarioSesion } from './sesion.service';

/**
 * La comprobación periódica de la sesión (cada 20 s) contra quien sale o entra
 * mientras está en vuelo.
 */
describe('Sesión: revalidar', () => {
  const usuario = (id: number): UsuarioSesion => ({
    id,
    username: `u${id}`,
    nombreCompleto: `Usuario ${id}`,
    rolId: 1,
    rol: 'Taller',
    permisos: ['ORDENES_VER'],
  });

  let sesion: SesionService;
  let red: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('motorsport19.token', 'token-A');
    localStorage.setItem('motorsport19.usuario', JSON.stringify(usuario(1)));
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    sesion = TestBed.inject(SesionService);
    red = TestBed.inject(HttpTestingController);
    TestBed.inject(HttpClient);
  });

  it('si se sale mientras está en vuelo, no deja una sesión fantasma guardada', () => {
    sesion.revalidar().subscribe();
    sesion.salir();
    red.expectOne('/api/auth/yo').flush(usuario(1));

    // Antes quedaba el texto «null» como token: al recargar parecía haber
    // sesión, fallaba todo con 401 y salía «Su sesión ha caducado».
    expect(localStorage.getItem('motorsport19.token')).toBeNull();
    expect(sesion.autenticado()).toBe(false);
  });

  it('con la misma sesión, actualiza los permisos que diga el servidor', () => {
    sesion.revalidar().subscribe();
    red.expectOne('/api/auth/yo').flush({ ...usuario(1), permisos: ['ORDENES_VER', 'CLIENTES_VER'] });

    expect(sesion.tienePermiso('CLIENTES_VER')).toBe(true);
  });
});
