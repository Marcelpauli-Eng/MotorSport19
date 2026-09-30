import { describe, expect, it } from 'vitest';
import { enlaceWhatsapp } from './enviar-presupuesto';

describe('enlaceWhatsapp', () => {
  it('pone el prefijo de España a un móvil de nueve cifras sin prefijo', () => {
    expect(enlaceWhatsapp('670 378 197')).toBe('https://wa.me/34670378197');
  });

  it('respeta el prefijo que ya trae (clientes de Francia)', () => {
    expect(enlaceWhatsapp('+33 6 12 34 56 78')).toBe('https://wa.me/33612345678');
  });

  it('lleva el mensaje codificado', () => {
    expect(enlaceWhatsapp('600111222', 'Total: 1.450,00 €\nGracias')).toBe(
      'https://wa.me/34600111222?text=Total%3A%201.450%2C00%20%E2%82%AC%0AGracias',
    );
  });
});
