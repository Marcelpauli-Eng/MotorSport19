import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { throwError } from 'rxjs';
import { SesionService } from './sesion.service';
import { TiempoRealService } from './tiempo-real.service';

describe('Tiempo real con la sesión caducada', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('un 401 cierra la sesión en vez de pintar «sin conexión» y reintentar sin fin', async () => {
    vi.useFakeTimers();
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 401 }));
    vi.stubGlobal('fetch', fetch);

    const token = signal<string | null>('token-caducado');
    // Lo que hace el interceptor con el 401 de /auth/yo: salir.
    const revalidar = vi.fn(() => {
      token.set(null);
      return throwError(() => new Error('401'));
    });
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: SesionService, useValue: { token, revalidar } },
      ],
    });

    const tiempoReal = TestBed.inject(TiempoRealService);
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(0);

    expect(revalidar).toHaveBeenCalledOnce();
    expect(tiempoReal.caido()).toBe(false);

    TestBed.tick();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(fetch).toHaveBeenCalledOnce();
  });
});
