import { numeroTecleado } from './inventario';

describe('Cantidades tecleadas en el almacén', () => {
  it('entiende la coma decimal, que es como se escribe aquí', () => {
    expect(numeroTecleado('1,5')).toBe(1.5);
    expect(numeroTecleado(' -2,25 ')).toBe(-2.25);
    expect(numeroTecleado('3')).toBe(3);
  });

  it('lo que no es un número no pasa por cero ni por válido', () => {
    expect(numeroTecleado('abc')).toBeNaN();
    expect(numeroTecleado('')).toBeNaN();
  });
});
