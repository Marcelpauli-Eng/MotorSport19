import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Dialogo } from './dialogo';
import { Icono } from './icono';

/** Una columna que entiende la importación de una pantalla. */
export interface CampoImportable {
  /** Nombre del campo en el alta de la API. */
  campo: string;
  /** Cómo se llama en pantalla. También vale como cabecera del fichero. */
  etiqueta: string;
  /** Otras cabeceras habituales para lo mismo: «población» para la ciudad. */
  alias?: string[];
  obligatorio?: boolean;
  /** Otra columna que la sustituye si falta: marca y modelo pueden venir juntos en la denominación. */
  oBien?: string;
  /** Números escritos a la española: «12.000 km», «12,50 €». */
  tipo?: 'entero' | 'decimal';
}

/** Lo que se ha sacado del fichero, listo para enviar. */
export interface Lectura {
  filas: Record<string, unknown>[];
  /** En qué fila del fichero estaba cada una, para decir cuál ha fallado. */
  numeros: number[];
  /** «POBLACION → Ciudad». */
  usadas: string[];
  ignoradas: string[];
  /** Columnas obligatorias que el fichero no trae. */
  faltan: string[];
}

interface Nota {
  fila: number;
  motivo: string;
}

interface Resultado {
  creadas: number;
  rechazadas: Nota[];
  /** Filas que han entrado dejando aparte algo que no valía: hay que revisar su ficha. */
  avisos: Nota[];
}

/** Filas por petición: cada tanda cabe holgada en el tiempo y el tamaño que deja pasar nginx. */
const POR_TANDA = 250;

/**
 * Botón «Importar» de un listado: da de alta en bloque desde un CSV o un JSON.
 *
 * <p>El fichero se lee aquí, en el navegador, y se reconocen sus columnas por
 * el nombre. Al servidor le llegan las filas con los campos del alta normal y
 * cada una pasa por sus mismas reglas, así que lo que no entraría a mano
 * tampoco entra por aquí: vuelve con su motivo y el número de fila.
 */
@Component({
  selector: 'app-importar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialogo, Icono],
  template: `
    <button type="button" class="boton" (click)="abierto.set(true)">
      <app-icono nombre="subir" [tamano]="16" />
      Importar
    </button>

    @if (abierto()) {
      <app-dialogo
        class="dialogo--ancho"
        [titulo]="titulo()"
        subtitulo="Desde un fichero CSV o JSON"
        (cerrar)="cerrar()"
      >
        @if (resultado(); as r) {
          <div class="apilado">
            <div class="aviso aviso--exito">
              {{ r.creadas }} {{ r.creadas === 1 ? 'fila dada de alta' : 'filas dadas de alta' }}.
            </div>
            @if (r.corte) {
              <div class="aviso aviso--error">{{ r.corte }}</div>
            }
            @if (r.avisos.length) {
              <div class="aviso aviso--ambar">
                {{ r.avisos.length }} {{ r.avisos.length === 1 ? 'ha entrado' : 'han entrado' }} con algo
                que revisar en su ficha:
              </div>
              <ul class="pequeno" style="margin: 0; padding-left: 1.1rem">
                @for (motivo of r.avisos; track $index) {
                  <li>{{ motivo }}</li>
                }
              </ul>
            }
            @if (r.rechazadas.length) {
              <div class="aviso aviso--error">
                {{ r.rechazadas.length }} no se han importado. Corrija el fichero y vuelva a
                importarlo: lo que ya entró no se duplica.
              </div>
              <ul class="pequeno" style="margin: 0; padding-left: 1.1rem">
                @for (motivo of r.rechazadas; track $index) {
                  <li>{{ motivo }}</li>
                }
              </ul>
            }
          </div>
        } @else {
          <div class="apilado">
            <p class="pequeno" style="margin: 0">
              En un CSV, la primera fila lleva los nombres de las columnas; en un JSON, cada
              registro los lleva como claves. Se reconocen:
              @for (c of campos(); track c.campo) {
                <strong>{{ c.etiqueta }}{{ c.obligatorio ? '*' : '' }}</strong>{{ $last ? '.' : ', ' }}
              }
              Las marcadas con * son obligatorias; el resto de columnas se ignora.
            </p>
            @if (ayuda()) {
              <p class="pequeno silenciado" style="margin: 0">{{ ayuda() }}</p>
            }
            <div class="campo">
              <label for="fichero-importacion">Fichero</label>
              <input
                id="fichero-importacion"
                type="file"
                accept=".csv,.json,.txt"
                [disabled]="enviadas() !== null"
                (change)="leer($event)"
              />
            </div>
            @if (error()) {
              <p class="dialogo__error" style="margin: 0">{{ error() }}</p>
            }
            @if (lectura(); as l) {
              <div class="aviso" [class.aviso--info]="!l.faltan.length" [class.aviso--error]="l.faltan.length">
                <div>
                  <strong>{{ l.filas.length }} {{ l.filas.length === 1 ? 'fila' : 'filas' }} con datos.</strong>
                  @if (l.faltan.length) {
                    Falta la columna obligatoria: {{ l.faltan.join(', ') }}.
                  }
                  <br />Se usan: {{ l.usadas.join(' · ') || 'ninguna' }}.
                  @if (l.ignoradas.length) {
                    <br />Se ignoran: {{ l.ignoradas.join(', ') }}.
                  }
                </div>
              </div>
            }
          </div>
        }

        <ng-container pie>
          @if (resultado()) {
            <button type="button" class="boton boton--principal" (click)="cerrar()">Cerrar</button>
          } @else {
            <button type="button" class="boton" [disabled]="enviadas() !== null" (click)="cerrar()">
              Cancelar
            </button>
            <button
              type="button"
              class="boton boton--principal"
              [disabled]="!listo()"
              (click)="importar()"
            >
              @if (enviadas() !== null) {
                Importando… {{ enviadas() }} de {{ lectura()?.filas?.length }}
              } @else {
                Importar {{ lectura()?.filas?.length || '' }}
              }
            </button>
          }
        </ng-container>
      </app-dialogo>
    }
  `,
})
export class Importar {
  private readonly http = inject(HttpClient);

  /** Colección de la API donde se da de alta: «clientes», «motos», «piezas». */
  readonly ruta = input.required<string>();
  readonly titulo = input.required<string>();
  /** En el informe, cada fila se identifica por el primero que traiga. */
  readonly campos = input.required<CampoImportable[]>();
  /** Lo que haya que saber de alguna columna. */
  readonly ayuda = input('');
  readonly importado = output<void>();

  protected readonly abierto = signal(false);
  protected readonly lectura = signal<Lectura | null>(null);
  protected readonly error = signal('');
  /** Cuántas van enviadas; nulo cuando no se está enviando. */
  protected readonly enviadas = signal<number | null>(null);
  protected readonly resultado = signal<{
    creadas: number;
    rechazadas: string[];
    avisos: string[];
    corte: string;
  } | null>(null);
  protected readonly listo = computed(() => {
    const l = this.lectura();
    return !!l?.filas.length && !l.faltan.length && this.enviadas() === null;
  });

  protected cerrar(): void {
    // A medio enviar no se cierra: el informe de lo que ha entrado se perdería.
    if (this.enviadas() !== null) return;
    this.abierto.set(false);
    this.lectura.set(null);
    this.error.set('');
    this.resultado.set(null);
  }

  protected async leer(evento: Event): Promise<void> {
    const fichero = (evento.target as HTMLInputElement).files?.[0];
    this.lectura.set(null);
    this.error.set('');
    if (!fichero) return;

    if (/\.xlsx?$/i.test(fichero.name)) {
      this.error.set('Es un fichero de Excel. Ábralo y guárdelo como «CSV UTF-8» (Archivo → Guardar como).');
      return;
    }
    try {
      this.lectura.set(leerFichero(await textoDe(fichero), this.campos()));
    } catch (e) {
      this.error.set(`No se ha podido leer el fichero: ${(e as Error).message}`);
    }
  }

  protected async importar(): Promise<void> {
    const lectura = this.lectura();
    if (!lectura) return;
    const url = `${environment.urlApi}/${this.ruta()}/importacion`;
    let creadas = 0;
    const rechazadas: string[] = [];
    const avisos: string[] = [];
    let corte = '';

    for (let desde = 0; desde < lectura.filas.length; desde += POR_TANDA) {
      this.enviadas.set(desde);
      // El servidor cuenta las filas dentro de la tanda; el informe, en el fichero.
      const describir = ({ fila, motivo }: Nota) => {
        const i = desde + fila - 1;
        const quien = this.campos()
          .map((c) => lectura.filas[i][c.campo])
          .find((valor) => valor != null);
        return `Fila ${lectura.numeros[i]}${quien ? ` (${quien})` : ''}: ${motivo}`;
      };
      try {
        const tanda = lectura.filas.slice(desde, desde + POR_TANDA);
        const r = await firstValueFrom(this.http.post<Resultado>(url, tanda));
        creadas += r.creadas;
        rechazadas.push(...r.rechazadas.map(describir));
        avisos.push(...r.avisos.map(describir));
      } catch {
        // Por qué ha fallado ya lo cuenta el aviso del interceptor de errores.
        corte = `Se ha cortado en la fila ${lectura.numeros[desde]}: ni esa ni las siguientes se han enviado.`;
        break;
      }
    }

    this.enviadas.set(null);
    this.resultado.set({ creadas, rechazadas, avisos, corte });
    if (creadas) this.importado.emit();
  }
}

/**
 * Excel en Windows guarda los CSV en Windows-1252 si no se le pide UTF-8. Sin
 * esto, las eñes y los acentos de los nombres llegarían rotos a la ficha.
 */
async function textoDe(fichero: File): Promise<string> {
  const bytes = await fichero.arrayBuffer();
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    return new TextDecoder('windows-1252').decode(bytes);
  }
}

/** Reconoce el formato, empareja las columnas con los campos y limpia los valores. */
export function leerFichero(texto: string, campos: CampoImportable[]): Lectura {
  let cabeceras: string[];
  let registros: unknown[][];
  let primera: number;

  if (/^\s*[[{]/.test(texto)) {
    const dato: unknown = JSON.parse(texto);
    const lista = Array.isArray(dato) ? dato : Object.values(dato ?? {}).find(Array.isArray);
    if (!lista) throw new Error('el JSON tiene que ser una lista de registros: [ { … }, { … } ].');
    const objetos = lista.map((o) => (o && typeof o === 'object' ? o : {}) as Record<string, unknown>);
    cabeceras = [...new Set(objetos.flatMap(Object.keys))];
    registros = objetos.map((o) => cabeceras.map((c) => o[c]));
    primera = 1;
  } else {
    [cabeceras = [], ...registros] = leerCsv(texto);
    primera = 2; // la 1 es la de los nombres de columna
  }

  const porNombre = new Map<string, CampoImportable>();
  for (const c of campos) {
    for (const nombre of [c.campo, c.etiqueta, ...(c.alias ?? [])]) porNombre.set(clave(nombre), c);
  }
  // Si dos columnas dicen lo mismo («Teléfono» y «Móvil»), vale la primera.
  const tomados = new Set<string>();
  const columnas = cabeceras.map((cabecera) => {
    const campo = porNombre.get(clave(cabecera));
    if (!campo || tomados.has(campo.campo)) return undefined;
    tomados.add(campo.campo);
    return campo;
  });

  const filas: Record<string, unknown>[] = [];
  const numeros: number[] = [];
  registros.forEach((registro, i) => {
    const fila: Record<string, unknown> = {};
    columnas.forEach((campo, j) => {
      const valor = campo && limpiar(registro[j], campo.tipo);
      if (campo && valor !== undefined) fila[campo.campo] = valor;
    });
    // Las filas vacías que deja una hoja de cálculo al final no son errores.
    if (Object.keys(fila).length) {
      filas.push(fila);
      numeros.push(primera + i);
    }
  });

  return {
    filas,
    numeros,
    usadas: columnas.flatMap((campo, j) => (campo ? [`${cabeceras[j]} → ${campo.etiqueta}`] : [])),
    ignoradas: cabeceras.filter((c, j) => !columnas[j] && c.trim()),
    faltan: campos
      .filter((c) => c.obligatorio && !tomados.has(c.campo) && !(c.oBien && tomados.has(c.oBien)))
      .map((c) => c.etiqueta),
  };
}

/**
 * Un CSV tal y como lo guarda una hoja de cálculo: separado por «;» (Excel en
 * español), «,» o tabuladores, y con comillas que pueden llevar dentro el
 * separador, otras comillas dobladas o saltos de línea.
 */
export function leerCsv(texto: string): string[][] {
  const cabecera = texto.split(/\r?\n/, 1)[0];
  const separador = [';', ',', '\t'].reduce((a, b) =>
    cabecera.split(b).length > cabecera.split(a).length ? b : a,
  );
  const filas: string[][] = [];
  let fila: string[] = [];
  let celda = '';
  let entreComillas = false;

  for (let i = 0; i < texto.length; i++) {
    const c = texto[i];
    if (entreComillas) {
      if (c !== '"') celda += c;
      else if (texto[i + 1] === '"') celda += texto[++i];
      else entreComillas = false;
    } else if (c === '"') {
      entreComillas = true;
    } else if (c === separador) {
      fila.push(celda);
      celda = '';
    } else if (c === '\n' || c === '\r') {
      if (c === '\r' && texto[i + 1] === '\n') i++;
      filas.push([...fila, celda]);
      fila = [];
      celda = '';
    } else {
      celda += c;
    }
  }
  if (celda || fila.length) filas.push([...fila, celda]);
  return filas;
}

/** «Código postal», «CODIGO_POSTAL» y «codigoPostal» son la misma columna. */
function clave(nombre: string): string {
  return String(nombre)
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');
}

/**
 * Deja el valor como lo espera la API. Lo que no se sabe arreglar se manda tal
 * cual: el servidor lo rechaza diciendo qué columna y qué valor.
 */
// ponytail: «1.234» sin coma se lee como 1,234; si llegan tarifas con miles
// sin decimales, decidirlo por columna.
function limpiar(valor: unknown, tipo?: CampoImportable['tipo']): unknown {
  if (typeof valor !== 'string') return valor ?? undefined; // del JSON: números y booleanos
  const texto = valor.trim();
  if (!texto) return undefined;

  if (tipo === 'entero') {
    const n = texto.replace(/[.\s]/g, '').replace(/[a-z]+$/i, ''); // «12.000 km» → 12000
    return /^-?\d+$/.test(n) ? n : texto;
  }
  if (tipo === 'decimal') {
    const n = texto.replace(/[\s€]/g, '');
    // El separador decimal es el último que aparece: «1.234,56» y «1,234.56».
    return n.lastIndexOf(',') > n.lastIndexOf('.') ? n.replace(/\./g, '').replace(',', '.') : n.replace(/,/g, '');
  }
  return texto;
}
