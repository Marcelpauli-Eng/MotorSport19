import {
  HttpClient,
  HttpContext,
  HttpErrorResponse,
  HttpInterceptorFn,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { NotificacionesService } from '../servicios/notificaciones.service';
import { SesionService } from '../servicios/sesion.service';
import { PENDIENTE, REINTENTO } from '../servicios/sin-conexion.service';
import { errorInterceptor, mensajeDe } from './error.interceptor';

describe('Interceptor de errores', () => {
  let avisos: { error: ReturnType<typeof vi.fn> };

  function montar(...detras: HttpInterceptorFn[]): { http: HttpClient; red: HttpTestingController } {
    avisos = { error: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(withInterceptors([errorInterceptor, ...detras])),
        provideHttpClientTesting(),
        { provide: NotificacionesService, useValue: avisos },
        { provide: SesionService, useValue: { autenticado: () => true, salir: vi.fn() } },
        { provide: Router, useValue: { url: '/panel', navigate: vi.fn() } },
      ],
    });
    return { http: TestBed.inject(HttpClient), red: TestBed.inject(HttpTestingController) };
  }

  it('un PDF que falla dice el motivo del servidor, no uno genérico', async () => {
    const { http, red } = montar();
    const peticion = new Promise((terminado) =>
      http.get('/api/motos/26/historial/pdf', { responseType: 'blob' }).subscribe({ error: terminado }),
    );

    red.expectOne('/api/motos/26/historial/pdf').flush(
      new Blob([JSON.stringify({ mensaje: 'Faltan los datos del taller: rellénelos en Ajustes.' })], {
        type: 'application/json',
      }),
      { status: 404, statusText: 'Not Found' },
    );
    await peticion;

    expect(avisos.error).toHaveBeenCalledWith(
      'Faltan los datos del taller: rellénelos en Ajustes.',
      expect.anything(),
    );
  });

  it('un cambio que ha quedado pendiente no dice «NO se ha guardado»', () => {
    // Lo que hace el interceptor sin conexión al encolar.
    const { http } = montar(() =>
      throwError(() => new HttpErrorResponse({ status: 0, error: PENDIENTE })),
    );

    http.put('/api/ordenes/9/diagnostico', {}).subscribe({ error: () => {} });

    expect(avisos.error).not.toHaveBeenCalled();
  });

  it('un reenvío de la cola que sigue sin red no llena la esquina de errores', () => {
    const { http, red } = montar();

    http
      .put('/api/ordenes/9/diagnostico', {}, { context: new HttpContext().set(REINTENTO, true) })
      .subscribe({ error: () => {} });
    red.expectOne('/api/ordenes/9/diagnostico').error(new ProgressEvent('error'), { status: 0 });

    expect(avisos.error).not.toHaveBeenCalled();
  });

  it('un reenvío que el servidor rechaza sí se avisa: es la única forma de saber por qué', () => {
    const { http, red } = montar();

    http
      .post('/api/ordenes/9/lineas/piezas', {}, { context: new HttpContext().set(REINTENTO, true) })
      .subscribe({ error: () => {} });
    red
      .expectOne('/api/ordenes/9/lineas/piezas')
      .flush({ mensaje: 'La pieza está de baja.' }, { status: 409, statusText: 'Conflict' });

    expect(avisos.error).toHaveBeenCalledWith('La pieza está de baja.', expect.any(Array));
  });

  it('distingue red caída, API caída tras nginx y fallo del servidor', () => {
    expect(mensajeDe(new HttpErrorResponse({ status: 0 }))).toContain('contactar con el servidor');
    expect(mensajeDe(new HttpErrorResponse({ status: 502 }))).toContain('no está disponible');
    expect(mensajeDe(new HttpErrorResponse({ status: 500 }))).toContain('ha fallado');
  });
});
