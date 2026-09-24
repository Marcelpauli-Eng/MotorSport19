import { oUltimo } from './formulario-pieza';

/** La regla del alta anterior: lo gris solo se usa si nadie escribe encima. */
describe('oUltimo', () => {
  it('se queda con lo escrito', () => {
    expect(oUltimo('Motor', 'Frenos')).toBe('Motor');
  });

  it('hereda lo anterior cuando el campo se deja en blanco', () => {
    expect(oUltimo('   ', 'Frenos')).toBe('Frenos');
  });

  it('sin nada anterior, un campo vacío se guarda vacío', () => {
    expect(oUltimo('', undefined)).toBeNull();
  });
});
