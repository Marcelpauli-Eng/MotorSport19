import { LineaFactura, LineaRectificativa } from '../../nucleo/modelos/facturacion';
import { Pieza } from '../../nucleo/modelos/taller';
import { datosDePieza, FilaCorreccion, lineasDeLaCorreccion, totalDe } from './detalle-factura';

const horas: LineaRectificativa = {
  tipo: 'MANO_DE_OBRA',
  descripcion: 'Cambio de aceite',
  piezaSku: null,
  cantidad: 1.5,
  precioUnitario: 45,
  descuentoPct: 0,
  tipoIva: 'GENERAL',
  porcentajeIva: 21,
};

const fila = (cambio: Partial<LineaRectificativa> = {}, quitada = false): FilaCorreccion => ({
  original: horas,
  linea: { ...horas, ...cambio },
  quitada,
});

describe('Corregir conceptos de una factura', () => {
  it('lo que no se toca no sale en la rectificativa', () => {
    expect(lineasDeLaCorreccion([fila()])).toEqual([]);
  });

  it('un concepto cambiado sale en negativo como estaba y en positivo como debe quedar', () => {
    expect(lineasDeLaCorreccion([fila({ cantidad: 1 })])).toEqual([
      { ...horas, cantidad: -1.5 },
      { ...horas, cantidad: 1 },
    ]);
  });

  it('quitar un concepto lo resta entero; uno nuevo se suma tal cual', () => {
    const nuevo = {
      ...horas,
      descripcion: 'Filtro',
      tipo: 'PIEZA',
      cantidad: 1,
      precioUnitario: 9.5,
    };
    expect(
      lineasDeLaCorreccion([fila({}, true), { original: null, linea: nuevo, quitada: false }]),
    ).toEqual([{ ...horas, cantidad: -1.5 }, nuevo]);
  });

  it('un concepto nuevo que se quita no deja nada', () => {
    expect(lineasDeLaCorreccion([{ original: null, linea: horas, quitada: true }])).toEqual([]);
  });

  it('redondea como el servidor: la mitad hacia fuera del cero, también en negativo', () => {
    // 1,5 x 45 = 67,50 + 21 % (14,175 -> 14,18) = 81,68
    expect(totalDe(horas)).toBe(81.68);
    expect(totalDe({ ...horas, cantidad: -1.5 })).toBe(-81.68);
    // 0,3 x 10,05 = 3,015 -> 3,02, que en coma flotante es 3,0149999…
    expect(totalDe({ ...horas, cantidad: 0.3, precioUnitario: 10.05, porcentajeIva: 0 })).toBe(
      3.02,
    );
  });
});

describe('Elegir del almacén la pieza que no se cobró', () => {
  const linea = (tipoIva: string, porcentajeIva: number) =>
    ({ tipoIva, porcentajeIva }) as LineaFactura;
  const pieza = {
    sku: 'PAS-FRE-DEL',
    descripcion: 'Pastillas delanteras',
    precioVenta: 39.9,
    tipoIva: 'REDUCIDO',
  } as Pieza;

  it('copia descripción, referencia y precio de venta, con el IVA de la pieza si la factura lo lleva', () => {
    expect(datosDePieza(pieza, [linea('GENERAL', 21), linea('REDUCIDO', 10)])).toEqual({
      descripcion: 'Pastillas delanteras',
      piezaSku: 'PAS-FRE-DEL',
      precioUnitario: 39.9,
      tipoIva: 'REDUCIDO',
      porcentajeIva: 10,
    });
  });

  it('si la factura no lleva ese IVA, usa el de la factura', () => {
    expect(datosDePieza(pieza, [linea('EXENTO', 0)])).toMatchObject({
      tipoIva: 'EXENTO',
      porcentajeIva: 0,
    });
  });
});
