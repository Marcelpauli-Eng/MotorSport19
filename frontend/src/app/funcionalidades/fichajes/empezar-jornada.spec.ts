import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { FichajesService } from '../../nucleo/servicios/fichajes.service';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { EmpezarJornada, saludoDeLaHora } from './empezar-jornada';

describe('Puerta de fichar', () => {
  it('«Cerrar sesión» lleva a la entrada, no deja el panel sin menú y a cero debajo', () => {
    const router = { navigate: vi.fn().mockResolvedValue(true) };
    const sesion = { salir: vi.fn(), usuario: signal({ nombreCompleto: 'Javier Ortega' }) };
    TestBed.configureTestingModule({
      imports: [EmpezarJornada],
      providers: [
        provideZonelessChangeDetection(),
        { provide: Router, useValue: router },
        { provide: SesionService, useValue: sesion },
        { provide: FichajesService, useValue: {} },
      ],
    });
    const puerta = TestBed.createComponent(EmpezarJornada).componentInstance as never as { salir: () => void };

    puerta.salir();

    expect(sesion.salir).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/entrar']);
  });

  it('saluda según la hora', () => {
    expect(saludoDeLaHora(9)).toBe('Buenos días');
    expect(saludoDeLaHora(16)).toBe('Buenas tardes');
    expect(saludoDeLaHora(23)).toBe('Buenas noches');
  });
});
