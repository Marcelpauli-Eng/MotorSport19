import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Signal, WritableSignal, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CampoImportable, Importar, Lectura, leerCsv, leerFichero } from './importar';

const CAMPOS: CampoImportable[] = [
  { campo: 'nombre', etiqueta: 'Nombre', obligatorio: true },
  { campo: 'ciudad', etiqueta: 'Ciudad', alias: ['poblacion'] },
  { campo: 'codigoPostal', etiqueta: 'Código postal' },
  { campo: 'kmActual', etiqueta: 'Kilómetros', alias: ['km'], tipo: 'entero' },
  { campo: 'precioVenta', etiqueta: 'Venta', tipo: 'decimal' },
];

/**
 * Lo que llega a la importación son ficheros de verdad: CSV guardados desde un
 * Excel en español y exportaciones de otros programas. Si aquí se lee mal una
 * columna, al servidor le llega un dato que nadie ha escrito.
 */
describe('Leer un fichero para importar', () => {
  it('lee el CSV de un Excel en español: punto y coma, comillas y saltos de línea dentro', () => {
    expect(leerCsv('a;b\r\n"x;y";"dijo ""hola"""\r\n"dos\nlíneas";z\r\n')).toEqual([
      ['a', 'b'],
      ['x;y', 'dijo "hola"'],
      ['dos\nlíneas', 'z'],
    ]);
    expect(leerCsv('a,b\n1,2')).toEqual([
      ['a', 'b'],
      ['1', '2'],
    ]);
  });

  it('reconoce las columnas por su nombre, escrito como sea, y dice cuáles ignora', () => {
    const lectura = leerFichero(
      'NOMBRE;POBLACION;CODIGO_POSTAL;KM;Venta;DADO_BAJA\n' +
        'Rocío;Móstoles;28931;12.000 km;1.234,50 €;NO\n' +
        ';;;;;\n' +
        'Paula;;;abc;12.5;\n',
      CAMPOS,
    );

    expect(lectura.filas).toEqual([
      { nombre: 'Rocío', ciudad: 'Móstoles', codigoPostal: '28931', kmActual: '12000', precioVenta: '1234.50' },
      // Lo que no se sabe arreglar va tal cual: el servidor dice qué columna está mal.
      { nombre: 'Paula', kmActual: 'abc', precioVenta: '12.5' },
    ]);
    // La fila vacía que deja Excel no se envía, pero no descuadra la numeración.
    expect(lectura.numeros).toEqual([2, 4]);
    expect(lectura.usadas).toContain('POBLACION → Ciudad');
    expect(lectura.ignoradas).toEqual(['DADO_BAJA']);
    expect(lectura.faltan).toEqual([]);
  });

  it('avisa de la columna obligatoria que falta antes de enviar nada', () => {
    expect(leerFichero('Ciudad\nMadrid', CAMPOS).faltan).toEqual(['Nombre']);
  });

  it('lee un JSON, suelto o dentro de un objeto, sin tocar los números', () => {
    const lista = [
      { nombre: 'Rocío', km: 1500 },
      { nombre: 'Paula', ciudad: null },
    ];
    const esperado = [{ nombre: 'Rocío', kmActual: 1500 }, { nombre: 'Paula' }];

    expect(leerFichero(JSON.stringify(lista), CAMPOS).filas).toEqual(esperado);
    expect(leerFichero(JSON.stringify({ clientes: lista }), CAMPOS).filas).toEqual(esperado);
    expect(leerFichero(JSON.stringify(lista), CAMPOS).numeros).toEqual([1, 2]);
    expect(() => leerFichero('{"total": 3}', CAMPOS)).toThrow(/lista de registros/);
  });
});

/**
 * Un fichero grande va en tandas, y el informe tiene que señalar la fila del
 * fichero, no la posición dentro de la tanda: si no, quien lo corrige busca
 * el error en otra línea.
 */
describe('Importar en tandas', () => {
  const campos: CampoImportable[] = [{ campo: 'nombre', etiqueta: 'Nombre', obligatorio: true }];
  let red: HttpTestingController;
  let dialogo: {
    lectura: WritableSignal<Lectura | null>;
    resultado: Signal<{ creadas: number; rechazadas: string[]; avisos: string[]; corte: string } | null>;
    importar(): Promise<void>;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Importar],
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(Importar);
    fixture.componentRef.setInput('ruta', 'clientes');
    fixture.componentRef.setInput('titulo', 'Importar clientes');
    fixture.componentRef.setInput('campos', campos);
    dialogo = fixture.componentInstance as never;
    red = TestBed.inject(HttpTestingController);

    // 300 clientes con una línea en blanco después del primero.
    const nombres = Array.from({ length: 300 }, (_, i) => `Cliente ${i + 1}`);
    dialogo.lectura.set(leerFichero(`Nombre\n${nombres[0]}\n\n${nombres.slice(1).join('\n')}`, campos));
  });

  const siguienteTanda = async () => {
    await new Promise((listo) => setTimeout(listo));
    return red.expectOne('/api/clientes/importacion');
  };

  it('manda de 250 en 250 y señala la fila del fichero que no entró', async () => {
    const hecho = dialogo.importar();

    const primera = await siguienteTanda();
    expect(primera.request.body.length).toBe(250);
    primera.flush({ creadas: 250, rechazadas: [], avisos: [{ fila: 1, motivo: 'Email apartado' }] });

    const segunda = await siguienteTanda();
    expect(segunda.request.body.length).toBe(50);
    segunda.flush({ creadas: 49, rechazadas: [{ fila: 3, motivo: 'Ya existe' }], avisos: [] });
    await hecho;

    // La 3.ª de la segunda tanda es «Cliente 253», que está en la línea 255.
    expect(dialogo.resultado()).toEqual({
      creadas: 299,
      rechazadas: ['Fila 255 (Cliente 253): Ya existe'],
      avisos: ['Fila 2 (Cliente 1): Email apartado'],
      corte: '',
    });
  });

  it('si una tanda no llega, dice desde qué fila no se ha enviado nada', async () => {
    const hecho = dialogo.importar();
    (await siguienteTanda()).flush({ creadas: 250, rechazadas: [], avisos: [] });
    (await siguienteTanda()).flush('Bad Gateway', { status: 502, statusText: 'Bad Gateway' });
    await hecho;

    expect(dialogo.resultado()?.creadas).toBe(250);
    expect(dialogo.resultado()?.corte).toContain('fila 253');
  });
});
