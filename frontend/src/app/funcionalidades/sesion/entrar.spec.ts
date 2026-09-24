import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { Entrar } from './entrar';

/**
 * La pantalla de entrada, cuando el destino no se abre.
 *
 * <p>La contraseña es correcta, la sesión queda abierta… y la pantalla se
 * quedaba con «Entrando…» para siempre, con el menú lateral ya montado detrás
 * mostrando el nombre del usuario. Desde fuera parece que el programa se cuelga
 * justo al entrar, que es de las peores cosas que puede hacer.
 *
 * <p>Esto no se puede reproducir a mano en el navegador —hay que provocar que
 * falle la carga de un trozo de la aplicación—, y por eso se comprueba aquí.
 */
describe('Pantalla de entrada', () => {
  let router: { navigateByUrl: ReturnType<typeof vi.fn>; url: string };
  let sesion: { entrar: ReturnType<typeof vi.fn> };

  /** Crea el componente y devuelve su instancia, con los accesos abiertos. */
  function pantalla(): Entrar & {
    username: (v?: string) => string;
    password: (v?: string) => string;
    enviando: () => boolean;
    error: () => string | null;
    entrar: () => void;
  } {
    const fixture = TestBed.createComponent(Entrar);
    // `protected` es solo de TypeScript: en tiempo de ejecución está ahí, y es
    // lo que permite comprobar el estado del botón sin pasar por el HTML.
    return fixture.componentInstance as never;
  }

  beforeEach(() => {
    router = { navigateByUrl: vi.fn(), url: '/entrar' };
    sesion = { entrar: vi.fn().mockReturnValue(of({ token: 'x' })) };

    TestBed.configureTestingModule({
      imports: [Entrar],
      providers: [
        provideZonelessChangeDetection(),
        { provide: Router, useValue: router },
        { provide: SesionService, useValue: sesion },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: new Map() } } },
      ],
    });
  });

  function rellenarYEntrar(componente: ReturnType<typeof pantalla>): void {
    componente.username.set('jortega');
    componente.password.set('tecnico1234');
    componente.entrar();
  }

  it('con todo bien, lleva al panel', async () => {
    router.navigateByUrl.mockResolvedValue(true);
    const c = pantalla();

    rellenarYEntrar(c);
    await Promise.resolve();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/panel');
    expect(c.error()).toBeNull();
  });

  it('si la pantalla no se puede cargar, avisa y desbloquea el botón', async () => {
    // Es lo que pasa cuando el navegador pide un fichero que el servidor ya ha
    // sustituido: `navigateByUrl` lanza, y antes nadie lo recogía.
    router.navigateByUrl.mockRejectedValue(new Error('ChunkLoadError'));
    const c = pantalla();

    rellenarYEntrar(c);
    await new Promise((r) => setTimeout(r, 0));

    expect(c.enviando()).toBe(false);
    expect(c.error()).toContain('Recargue');
  });

  it('si el destino no se puede activar, prueba con el panel', async () => {
    router.navigateByUrl.mockImplementation((destino: string) =>
      Promise.resolve(destino === '/panel'),
    );
    const c = pantalla();
    (c as never as { ruta: { snapshot: { queryParamMap: Map<string, string> } } }).ruta = {
      snapshot: { queryParamMap: new Map([['returnUrl', '/ajustes']]) },
    };

    rellenarYEntrar(c);
    await new Promise((r) => setTimeout(r, 0));

    expect(router.navigateByUrl).toHaveBeenCalledWith('/ajustes');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/panel');
    expect(c.error()).toBeNull();
  });

  it('si un guard desvia, no se queda bloqueado aunque diga que no llego', async () => {
    // El guard devuelve otra pantalla: la navegación contesta `false`, pero la
    // aplicación ya está dentro. Eso vale, y no debe verse como un fallo.
    router.navigateByUrl.mockImplementation(() => {
      router.url = '/panel';
      return Promise.resolve(false);
    });
    const c = pantalla();

    rellenarYEntrar(c);
    await new Promise((r) => setTimeout(r, 0));

    expect(c.error()).toBeNull();
  });

  it('con la contrasena mal, desbloquea el boton y enseña el motivo', async () => {
    sesion.entrar.mockReturnValue(
      throwError(() => ({ error: { mensaje: 'Usuario o contrasena incorrectos.' } })),
    );
    const c = pantalla();

    rellenarYEntrar(c);
    await Promise.resolve();

    expect(c.enviando()).toBe(false);
    expect(c.error()).toBe('Usuario o contrasena incorrectos.');
    expect(c.password()).toBe('');
  });

  it('con el servidor caído dice que no hay conexión, no que la contraseña esté mal', async () => {
    sesion.entrar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const c = pantalla();

    rellenarYEntrar(c);
    await Promise.resolve();

    expect(c.enviando()).toBe(false);
    expect(c.error()).toContain('servidor');
  });

  it('con nginx arriba pero la API caída (502) también lo dice', async () => {
    sesion.entrar.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 502, error: '<html>Bad Gateway</html>' })),
    );
    const c = pantalla();

    rellenarYEntrar(c);
    await Promise.resolve();

    expect(c.error()).toContain('no está disponible');
  });
});
