import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CitasService } from '../../nucleo/servicios/citas.service';
import { MotosService } from '../../nucleo/servicios/motos.service';
import { NotificacionesService } from '../../nucleo/servicios/notificaciones.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { DetalleCita } from './detalle-cita';

describe('Ficha de cita: «No se presentó»', () => {
  let citas: { marcarNoPresentado: ReturnType<typeof vi.fn> };
  let ficha: { noSePresento: () => void };

  beforeEach(() => {
    citas = { marcarNoPresentado: vi.fn().mockReturnValue(of({})) };
    TestBed.configureTestingModule({
      imports: [DetalleCita],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: CitasService, useValue: citas },
        { provide: MotosService, useValue: {} },
        { provide: NotificacionesService, useValue: { exito: vi.fn(), error: vi.fn() } },
        { provide: SesionService, useValue: { tienePermiso: () => true } },
      ],
    });
    const fixture = TestBed.createComponent(DetalleCita);
    fixture.componentRef.setInput('cita', {
      id: 3,
      estadosPosibles: ['NO_PRESENTADO'],
      fechaHora: '2026-09-15T09:00:00Z',
      motoSinRegistrar: false,
    });
    ficha = fixture.componentInstance as never;
  });

  afterEach(() => vi.restoreAllMocks());

  it('«Cancelar» en el diálogo no cierra la cita', () => {
    vi.spyOn(window, 'prompt').mockReturnValue(null);

    ficha.noSePresento();

    expect(citas.marcarNoPresentado).not.toHaveBeenCalled();
  });

  it('aceptar sin escribir nada sí la marca, sin motivo', () => {
    vi.spyOn(window, 'prompt').mockReturnValue('   ');

    ficha.noSePresento();

    expect(citas.marcarNoPresentado).toHaveBeenCalledWith(3, undefined);
  });
});
