import { HttpClient, HttpRequest } from '@angular/common/http';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { NotificacionesService } from './notificaciones.service';
import { SesionService } from './sesion.service';
import { SinConexionService, esAlta, noLlegoAlServidor } from './sin-conexion.service';
import { TiempoRealService } from './tiempo-real.service';

describe('esAlta', () => {
  it('crear algo nuevo necesita conexión', () => {
    expect(esAlta('POST', '/api/ordenes')).toBe(true);
    expect(esAlta('POST', '/api/clientes')).toBe(true);
    expect(esAlta('POST', '/api/citas/7/entrada')).toBe(true);
  });

  it('cambiar lo que ya existe se puede guardar para después', () => {
    expect(esAlta('PUT', '/api/clientes/3')).toBe(false);
    expect(esAlta('POST', '/api/ordenes/12/preparacion')).toBe(false);
    expect(esAlta('POST', '/api/inventario/piezas/4/entradas')).toBe(false);
    expect(esAlta('GET', '/api/ordenes')).toBe(false);
  });
});

describe('noLlegoAlServidor', () => {
  it('cuenta la red caída y nginx sin API, pero no un fallo de la API', () => {
    expect([0, 502, 503].every(noLlegoAlServidor)).toBe(true);
    expect([400, 409, 500, 504].some(noLlegoAlServidor)).toBe(false);
  });
});

describe('Cola de cambios sin conexión', () => {
  let servicio: SinConexionService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: HttpClient, useValue: {} },
        { provide: SesionService, useValue: { autenticado: signal(true), usuario: signal({ id: 1 }) } },
        // Caído: así no intenta sincronizar nada al arrancar.
        { provide: TiempoRealService, useValue: { caido: signal(true), cambios: new Subject() } },
        { provide: NotificacionesService, useValue: { info: vi.fn(), exito: vi.fn() } },
      ],
    });
    servicio = TestBed.inject(SinConexionService);
  });

  function peticion(id: string, cuerpo: unknown): HttpRequest<unknown> {
    return new HttpRequest('POST', '/api/ordenes/9/lineas/mano-de-obra', cuerpo).clone({
      setHeaders: { 'X-Id-Peticion': id },
    });
  }

  it('pulsar dos veces lo mismo sin red lo apunta una sola vez', () => {
    servicio.encolar(peticion('a', { descripcion: 'Montaje', horas: 1 }));
    servicio.encolar(peticion('b', { descripcion: 'Montaje', horas: 1 }));

    expect(servicio.pendientes()).toHaveLength(1);
  });

  it('dos cambios distintos se apuntan los dos, y sobreviven a una recarga', () => {
    servicio.encolar(peticion('a', { descripcion: 'Montaje', horas: 1 }));
    servicio.encolar(peticion('b', { descripcion: 'Purga', horas: 0.5 }));

    expect(servicio.pendientes()).toHaveLength(2);
    expect(JSON.parse(localStorage.getItem('motorsport19.pendientes')!)).toHaveLength(2);
  });
});
