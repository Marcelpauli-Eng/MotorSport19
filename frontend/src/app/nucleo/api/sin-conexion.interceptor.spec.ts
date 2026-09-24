import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { PENDIENTE, SinConexionService } from '../servicios/sin-conexion.service';
import { TiempoRealService } from '../servicios/tiempo-real.service';
import { sinConexionInterceptor } from './sin-conexion.interceptor';

/**
 * Qué pasa con cada petición cuando el servidor no contesta.
 *
 * <p>Los dos fallos que se protegen aquí se vieron en el navegador: con la API
 * caída detrás de nginx la lista salía vacía aunque hubiera copia guardada, y un
 * cambio hecho sin red devolvía su propio cuerpo como si fuera la orden y la
 * pantalla se quedaba en blanco.
 */
describe('Interceptor sin conexión', () => {
  let http: HttpClient;
  let red: HttpTestingController;
  let cola: {
    encolar: ReturnType<typeof vi.fn>;
    sePuedeGuardar: ReturnType<typeof vi.fn>;
    claveDe: ReturnType<typeof vi.fn>;
    guardarRespuesta: ReturnType<typeof vi.fn>;
    leerRespuesta: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    cola = {
      encolar: vi.fn(),
      sePuedeGuardar: vi.fn().mockReturnValue(true),
      claveDe: vi.fn().mockReturnValue('1|/api/clientes'),
      guardarRespuesta: vi.fn(),
      leerRespuesta: vi.fn().mockResolvedValue([{ id: 7, nombre: 'Rocío' }]),
    };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(withInterceptors([sinConexionInterceptor])),
        provideHttpClientTesting(),
        { provide: SinConexionService, useValue: cola },
        { provide: TiempoRealService, useValue: { idPestana: 'pestana-1' } },
      ],
    });
    http = TestBed.inject(HttpClient);
    red = TestBed.inject(HttpTestingController);
  });

  it('con la API caída detrás de nginx (502) enseña lo último que se recibió', async () => {
    const respuesta = firstValueFrom(http.get('/api/clientes'));
    red.expectOne('/api/clientes').flush('Bad Gateway', { status: 502, statusText: 'Bad Gateway' });

    expect(await respuesta).toEqual([{ id: 7, nombre: 'Rocío' }]);
  });

  it('un fallo de verdad del servidor (500) no se tapa con datos viejos', async () => {
    const respuesta = firstValueFrom(http.get('/api/clientes'));
    red.expectOne('/api/clientes').flush({ mensaje: 'Error' }, { status: 500, statusText: 'Error' });

    await expect(respuesta).rejects.toMatchObject({ status: 500 });
    expect(cola.leerRespuesta).not.toHaveBeenCalled();
  });

  it('un cambio sin red se encola y termina como pendiente, sin inventar la respuesta', async () => {
    const respuesta = firstValueFrom(http.put('/api/ordenes/9/diagnostico', { diagnostico: 'Bobina' }));
    const peticion = red.expectOne('/api/ordenes/9/diagnostico');
    expect(peticion.request.headers.has('X-Id-Peticion')).toBe(true);
    peticion.error(new ProgressEvent('error'), { status: 0 });

    await expect(respuesta).rejects.toMatchObject({ error: PENDIENTE });
    expect(cola.encolar).toHaveBeenCalledTimes(1);
  });

  it('con nginx respondiendo 503 también se encola', async () => {
    const respuesta = firstValueFrom(http.post('/api/ordenes/9/lista', null));
    red.expectOne('/api/ordenes/9/lista').flush('', { status: 503, statusText: 'Unavailable' });

    await expect(respuesta).rejects.toMatchObject({ error: PENDIENTE });
    expect(cola.encolar).toHaveBeenCalledTimes(1);
  });

  it('un 504 no se encola: la API pudo llegar a aplicarlo', async () => {
    const respuesta = firstValueFrom(http.post('/api/ordenes/9/entrega', null));
    red.expectOne('/api/ordenes/9/entrega').flush('', { status: 504, statusText: 'Timeout' });

    await expect(respuesta).rejects.toMatchObject({ status: 504 });
    expect(cola.encolar).not.toHaveBeenCalled();
  });

  it('las lecturas no llevan identificador de petición; las escrituras sí', () => {
    http.get('/api/clientes').subscribe();
    http.put('/api/clientes/3/contacto', { nombre: 'Ana' }).subscribe();

    expect(red.expectOne('/api/clientes').request.headers.has('X-Id-Peticion')).toBe(false);
    expect(red.expectOne('/api/clientes/3/contacto').request.headers.has('X-Id-Peticion')).toBe(true);
  });
});
